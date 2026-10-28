package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRecoverer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.Job;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.CoroutinesUtils;
import org.springframework.core.KotlinDetector;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Calls the {@link ReactiveRecover} method that fits a
 * {@link ReactiveRetryable} method and its error.
 *
 * @author Mahdi Bohloul
 * @since 3.0.0
 */
final class RecoverMethodRecoverer implements ReactiveRecoverer {

	private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt",
			RecoverMethodRecoverer.class.getClassLoader());

	private final List<Candidate> candidates;

	private final boolean suspending;

	private final Map<Class<?>, Optional<Candidate>> cache = new ConcurrentReferenceHashMap<>();

	private RecoverMethodRecoverer(List<Candidate> candidates, boolean suspending) {
		this.candidates = candidates;
		this.suspending = suspending;
	}

	/**
	 * Finds the recover methods for a retryable method.
	 *
	 * @param targetClass
	 *            the class of the target object
	 * @param method
	 *            the retryable method
	 * @param recoverName
	 *            the name of the recover method, or an empty string to match by
	 *            signature
	 * @return the recoverer, or {@code null} if no recover method fits
	 * @throws IllegalStateException
	 *             if {@code recoverName} names a method that does not exist or does
	 *             not fit
	 */
	static @Nullable RecoverMethodRecoverer forMethod(Class<?> targetClass, Method method, String recoverName) {
		boolean suspending = isSuspending(method);
		// The interceptor retries only these methods, so only they can recover.
		if (!suspending && method.getReturnType() != Mono.class && method.getReturnType() != Flux.class)
			return null;
		boolean byName = StringUtils.hasText(recoverName);
		List<Candidate> candidates = new ArrayList<>();
		List<String> misfits = new ArrayList<>();
		for (Method recoverMethod : recoverMethods(targetClass)) {
			if (byName && !recoverMethod.getName().equals(recoverName))
				continue;
			String misfit = misfit(targetClass, method, recoverMethod);
			if (misfit == null)
				candidates.add(new Candidate(recoverMethod, errorType(method, recoverMethod)));
			else
				misfits.add(recoverMethod + ": " + misfit);
		}
		if (byName && candidates.isEmpty()) {
			if (misfits.isEmpty())
				throw new IllegalStateException("@ReactiveRetryable(recover = \"" + recoverName + "\") on " + method
						+ " names no @ReactiveRecover method in " + targetClass.getName());
			throw new IllegalStateException("The @ReactiveRecover method \"" + recoverName + "\" does not fit the "
					+ "@ReactiveRetryable method " + method + ". " + String.join("; ", misfits));
		}
		return candidates.isEmpty() ? null : new RecoverMethodRecoverer(List.copyOf(candidates), suspending);
	}

	@Override
	public @Nullable Publisher<?> recover(MethodInvocation invocation, Throwable error) throws Throwable {
		Candidate candidate = this.cache.computeIfAbsent(error.getClass(), this::closest).orElse(null);
		if (candidate == null)
			return null;
		Object target = invocation.getThis();
		Assert.state(target != null, "No target object to call the @ReactiveRecover method on");
		Object[] args = arguments(candidate, invocation.getArguments(), error);
		Method recoverMethod = candidate.method();
		ReflectionUtils.makeAccessible(recoverMethod);
		Publisher<?> recovered;
		if (this.suspending) {
			recovered = KotlinDelegate.invokeSuspendingFunction(recoverMethod, target, args);
		} else {
			try {
				recovered = (Publisher<?>) recoverMethod.invoke(target, args);
			} catch (InvocationTargetException ex) {
				throw ex.getTargetException();
			}
		}
		if (recovered == null)
			throw new IllegalStateException("The @ReactiveRecover method " + recoverMethod + " returned null");
		return recovered;
	}

	/**
	 * Chooses the candidate whose error type is the closest supertype of the error.
	 * A candidate without an error parameter is the farthest.
	 */
	private Optional<Candidate> closest(Class<?> errorClass) {
		Candidate closest = null;
		int closestDistance = Integer.MAX_VALUE;
		for (Candidate candidate : this.candidates) {
			int distance = candidate.distance(errorClass);
			if (distance >= 0 && (closest == null || distance < closestDistance)) {
				closest = candidate;
				closestDistance = distance;
			}
		}
		return Optional.ofNullable(closest);
	}

	private static Object[] arguments(Candidate candidate, Object[] args, Throwable error) {
		if (candidate.errorType() == null)
			return args;
		Object[] withError = new Object[args.length + 1];
		withError[0] = error;
		System.arraycopy(args, 0, withError, 1, args.length);
		return withError;
	}

	private static List<Method> recoverMethods(Class<?> targetClass) {
		List<Method> methods = new ArrayList<>();
		// Visits the class before its superclasses, so an override comes first.
		ReflectionUtils.doWithMethods(targetClass, method -> {
			if (AnnotatedElementUtils.findMergedAnnotation(method, ReactiveRecover.class) != null
					&& methods.stream().noneMatch(found -> found.getName().equals(method.getName())
							&& Arrays.equals(found.getParameterTypes(), method.getParameterTypes())))
				methods.add(method);
		}, method -> !method.isBridge() && !method.isSynthetic());
		return methods;
	}

	/**
	 * Tells why the recover method does not fit the retryable method.
	 *
	 * @return the reason, or {@code null} if the recover method fits
	 */
	private static @Nullable String misfit(Class<?> targetClass, Method method, Method recoverMethod) {
		boolean suspending = isSuspending(method);
		if (suspending != isSuspending(recoverMethod))
			return suspending ? "it must be a suspend function" : "it must not be a suspend function";
		List<Class<?>> parameterTypes = valueParameterTypes(method);
		List<Class<?>> recoverParameterTypes = valueParameterTypes(recoverMethod);
		int offset = recoverParameterTypes.size() - parameterTypes.size();
		if ((offset != 0 && offset != 1)
				|| (offset == 1 && !Throwable.class.isAssignableFrom(recoverParameterTypes.get(0)))
				|| !recoverParameterTypes.subList(offset, recoverParameterTypes.size()).equals(parameterTypes))
			return "its parameters must be an optional Throwable followed by " + parameterTypes;
		ResolvableType returnType = ResolvableType.forMethodReturnType(method, targetClass);
		ResolvableType recoverReturnType = ResolvableType.forMethodReturnType(recoverMethod, targetClass);
		if (!returnType.isAssignableFrom(recoverReturnType))
			return "its return type " + recoverReturnType + " is not compatible with " + returnType;
		return null;
	}

	private static @Nullable Class<?> errorType(Method method, Method recoverMethod) {
		int parameterCount = valueParameterTypes(method).size();
		List<Class<?>> recoverParameterTypes = valueParameterTypes(recoverMethod);
		return recoverParameterTypes.size() > parameterCount ? recoverParameterTypes.get(0) : null;
	}

	/**
	 * The parameter types without the {@code Continuation} of a suspend function.
	 */
	private static List<Class<?>> valueParameterTypes(Method method) {
		List<Class<?>> types = Arrays.asList(method.getParameterTypes());
		return isSuspending(method) ? types.subList(0, types.size() - 1) : types;
	}

	private static boolean isSuspending(Method method) {
		return COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method);
	}

	private record Candidate(Method method, @Nullable Class<?> errorType) {

		/**
		 * The number of superclass steps from the error class to the error type, or -1
		 * if this candidate does not accept the error.
		 */
		int distance(Class<?> errorClass) {
			if (this.errorType == null)
				return Integer.MAX_VALUE;
			if (!this.errorType.isAssignableFrom(errorClass))
				return -1;
			int distance = 0;
			for (Class<?> type = errorClass; type != this.errorType; type = type.getSuperclass())
				distance++;
			return distance;
		}
	}

	/**
	 * Inner class to avoid a hard dependency on Kotlin at runtime.
	 */
	private static final class KotlinDelegate {

		/**
		 * Calls a suspend function with the coroutine context of the caller, as
		 * {@code AopUtils.invokeJoinpointUsingReflection} does.
		 */
		static Publisher<?> invokeSuspendingFunction(Method method, Object target, Object[] args) {
			Continuation<?> continuation = (Continuation<?>) args[args.length - 1];
			Assert.state(continuation != null, "No Continuation available");
			CoroutineContext context = continuation.getContext().minusKey(Job.Key);
			return CoroutinesUtils.invokeSuspendingFunction(context, method, target, args);
		}
	}
}
