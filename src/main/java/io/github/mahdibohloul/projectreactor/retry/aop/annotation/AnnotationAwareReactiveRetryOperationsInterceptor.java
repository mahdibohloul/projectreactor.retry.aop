package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRecoverer;
import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryInterceptorBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.IntroductionInterceptor;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.StringUtils;

/**
 * Enable reactive retryable aop capability. It is invoking and delegates to an
 * appropriate {@link MethodInterceptor} based on the {@link ReactiveRetryable}
 * annotation.
 */
public class AnnotationAwareReactiveRetryOperationsInterceptor implements IntroductionInterceptor, BeanFactoryAware {

	private static final MethodInterceptor NULL_INTERCEPTOR = methodInvocation -> {
		throw new UnsupportedOperationException(
				"No reactive retry advice available for method " + methodInvocation.getMethod());
	};

	private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

	private final ConcurrentReferenceHashMap<Object, ConcurrentMap<Method, MethodInterceptor>> delegates = new ConcurrentReferenceHashMap<>();

	private BeanFactory beanFactory;

	/**
	 * This method is invoked by the Spring container to create a new instance of
	 * the interceptor.
	 *
	 * @param invocation
	 *            the method invocation joinpoint
	 */
	@Override
	public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
		MethodInterceptor delegate = getDelegate(invocation.getThis(), invocation.getMethod());
		if (delegate != null)
			return delegate.invoke(invocation);
		return invocation.proceed();
	}

	private @Nullable MethodInterceptor getDelegate(@Nullable Object target, Method method) {
		ConcurrentMap<Method, MethodInterceptor> cachedMethods = this.delegates.get(target);
		if (cachedMethods == null)
			cachedMethods = new ConcurrentHashMap<>();
		MethodInterceptor delegate = cachedMethods.get(method);
		if (delegate == null) {
			MethodInterceptor interceptor = NULL_INTERCEPTOR;
			ReactiveRetryable reactiveRetryable = AnnotatedElementUtils.findMergedAnnotation(method,
					ReactiveRetryable.class);
			if (reactiveRetryable == null)
				reactiveRetryable = classLevelAnnotation(method, ReactiveRetryable.class);
			if (reactiveRetryable == null)
				reactiveRetryable = findAnnotationOnTarget(target, method, ReactiveRetryable.class);
			if (reactiveRetryable != null) {
				if (StringUtils.hasText(reactiveRetryable.interceptor())) {
					if (StringUtils.hasText(reactiveRetryable.recover()))
						throw new IllegalStateException("@ReactiveRetryable on " + method
								+ " sets both interceptor = \"" + reactiveRetryable.interceptor()
								+ "\" and recover = \"" + reactiveRetryable.recover()
								+ "\". The interceptor attribute is mutually exclusive"
								+ " with the other attributes.");
					interceptor = this.beanFactory.getBean(reactiveRetryable.interceptor(), MethodInterceptor.class);
				} else {
					ReactiveRecoverer recoverer = getRecoverer(target, method, reactiveRetryable);
					if (reactiveRetryable.exponentialBackoff()) {
						interceptor = getBackOffInterceptor(reactiveRetryable, recoverer);
					} else if (reactiveRetryable.shouldCheckMaxInRow()) {
						interceptor = getMaxInRowInterceptor(reactiveRetryable, recoverer);
					} else if (reactiveRetryable.backOffFixDelay() > 0) {
						interceptor = getFixedDelayInterceptor(reactiveRetryable, recoverer);
					} else {
						interceptor = getMaxAttemptsInterceptor(reactiveRetryable, recoverer);
					}
				}
			}
			cachedMethods.putIfAbsent(method, interceptor);
			delegate = cachedMethods.get(method);
		}
		this.delegates.putIfAbsent(target, cachedMethods);
		return delegate == NULL_INTERCEPTOR ? null : delegate;
	}

	private @Nullable ReactiveRecoverer getRecoverer(@Nullable Object target, Method method,
			ReactiveRetryable reactiveRetryable) {
		if (target == null)
			return null;
		Class<?> targetClass = AopProxyUtils.ultimateTargetClass(target);
		return RecoverMethodRecoverer.forMethod(targetClass, AopUtils.getMostSpecificMethod(method, targetClass),
				reactiveRetryable.recover());
	}

	private MethodInterceptor getMaxAttemptsInterceptor(ReactiveRetryable reactiveRetryable,
			@Nullable ReactiveRecoverer recoverer) {
		return ReactiveRetryInterceptorBuilder.maxAttempts().setMaxAttempts(reactiveRetryable.maxAttempts())
				.setInclude(reactiveRetryable.include()).setExclude(reactiveRetryable.exclude()).setRecoverer(recoverer)
				.build();
	}

	private MethodInterceptor getFixedDelayInterceptor(ReactiveRetryable reactiveRetryable,
			@Nullable ReactiveRecoverer recoverer) {
		return ReactiveRetryInterceptorBuilder.fixedDelay().setFixedDelay(reactiveRetryable.backOffFixDelay())
				.setMaxAttempts(reactiveRetryable.maxAttempts()).setInclude(reactiveRetryable.include())
				.setExclude(reactiveRetryable.exclude()).setRecoverer(recoverer).build();
	}

	private MethodInterceptor getMaxInRowInterceptor(ReactiveRetryable reactiveRetryable,
			@Nullable ReactiveRecoverer recoverer) {
		return ReactiveRetryInterceptorBuilder.maxInRow().setMaxAttempts(reactiveRetryable.maxAttempts())
				.setInclude(reactiveRetryable.include()).setExclude(reactiveRetryable.exclude()).setRecoverer(recoverer)
				.build();
	}

	private MethodInterceptor getBackOffInterceptor(ReactiveRetryable reactiveRetryable,
			@Nullable ReactiveRecoverer recoverer) {
		return ReactiveRetryInterceptorBuilder.backOff().setBackOffFactor(reactiveRetryable.backOffFactor())
				.setMaxDelay(reactiveRetryable.backOffMaxDelay()).setMinDelay(reactiveRetryable.backOffMinDelay())
				.setShouldCheckMaxInRow(reactiveRetryable.shouldCheckMaxInRow()).setExclude(reactiveRetryable.exclude())
				.setInclude(reactiveRetryable.include()).setMaxAttempts(reactiveRetryable.maxAttempts())
				.setRecoverer(recoverer).build();
	}

	private <A extends Annotation> @Nullable A findAnnotationOnTarget(@Nullable Object target, Method method,
			Class<A> annotation) {
		try {
			Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());
			A ann = AnnotatedElementUtils.findMergedAnnotation(targetMethod, annotation);
			if (ann == null)
				ann = classLevelAnnotation(targetMethod, annotation);
			return ann;
		} catch (Exception e) {
			return null;
		}
	}

	private <A extends Annotation> @Nullable A classLevelAnnotation(Method method, Class<A> annotation) {
		A ann = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), annotation);
		if (ann != null && AnnotatedElementUtils.findMergedAnnotation(method, ReactiveRecover.class) != null)
			ann = null;
		return ann;
	}

	@Override
	public boolean implementsInterface(Class<?> intf) {
		return io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryable.class
				.isAssignableFrom(intf);
	}

	/**
	 * Set the bean factory that this object runs in.
	 *
	 * @param beanFactory
	 *            owning BeanFactory (never {@code null}). The bean can immediately
	 *            call methods on the factory.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
		this.evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
	}
}
