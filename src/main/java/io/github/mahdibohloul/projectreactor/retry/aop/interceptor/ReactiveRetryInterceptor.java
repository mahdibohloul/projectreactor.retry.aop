package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Abstract class for reactive retry interceptor.
 *
 * @author Mahdi Bohloul
 */
public abstract class ReactiveRetryInterceptor implements MethodInterceptor {

	private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent("kotlinx.coroutines.reactor.MonoKt",
			ReactiveRetryInterceptor.class.getClassLoader());

	private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

	private final Retry retryPolicy;

	protected ReactiveRetryInterceptor(Retry retryPolicy) {
		this.retryPolicy = retryPolicy;
	}

	/**
	 * Retry the given invocation using the configured retry policy. This function
	 * uses the project reactor's retry mechanism to provide retry support.
	 *
	 * <p>
	 * Methods returning {@link Mono} or {@link Flux} are retried, and so are Kotlin
	 * {@code suspend} functions when {@code kotlinx-coroutines-reactor} is on the
	 * classpath: Spring AOP exposes their result to interceptors as a {@link Mono}
	 * (or a {@link Flux} for a {@code Flow} result), and awaits the retried
	 * publisher on the way out.
	 *
	 * @param invocation
	 *            the method invocation joinpoint
	 * @return the result of the invocation
	 * @throws Throwable
	 *             if the invocation fails
	 */
	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		Method method = invocation.getMethod();
		if (COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method))
			return returnsFlow(method) ? retryFlux(invocation) : retryMono(invocation);
		if (isMono(method.getReturnType()))
			return retryMono(invocation);
		if (isFlux(method.getReturnType()))
			return retryFlux(invocation);
		return invocation.proceed();
	}

	private Mono<?> retryMono(MethodInvocation invocation) {
		return Mono.defer(() -> {
			try {
				return Mono.from((Publisher<?>) ReactiveRetryUtil.invocableClone(invocation).proceed());
			} catch (Throwable t) {
				return Mono.error(t);
			}
		}).retryWhen(this.retryPolicy);
	}

	private Flux<?> retryFlux(MethodInvocation invocation) {
		return Flux.defer(() -> {
			try {
				return Flux.from((Publisher<?>) ReactiveRetryUtil.invocableClone(invocation).proceed());
			} catch (Throwable t) {
				return Flux.error(t);
			}
		}).retryWhen(this.retryPolicy);
	}

	private boolean returnsFlow(Method method) {
		return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName());
	}

	private boolean isFlux(Class<?> returnType) {
		return returnType.equals(Flux.class);
	}

	private boolean isMono(Class<?> returnType) {
		return returnType.equals(Mono.class);
	}
}
