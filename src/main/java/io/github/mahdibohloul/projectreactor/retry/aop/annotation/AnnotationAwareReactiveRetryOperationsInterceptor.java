package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryInterceptorBuilder;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.IntroductionInterceptor;
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
	public Object invoke(MethodInvocation invocation) throws Throwable {
		MethodInterceptor delegate = getDelegate(invocation.getThis(), invocation.getMethod());
		if (delegate != null)
			return delegate.invoke(invocation);
		return invocation.proceed();
	}

	private MethodInterceptor getDelegate(Object target, Method method) {
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
					interceptor = this.beanFactory.getBean(reactiveRetryable.interceptor(), MethodInterceptor.class);
				} else if (reactiveRetryable.exponentialBackoff()) {
					interceptor = getBackOffInterceptor(target, method, reactiveRetryable);
				} else if (reactiveRetryable.shouldCheckMaxInRow()) {
					interceptor = getMaxInRowInterceptor(target, method, reactiveRetryable);
				} else if (reactiveRetryable.backOffFixDelay() > 0) {
					interceptor = getFixedDelayInterceptor(target, method, reactiveRetryable);
				} else {
					interceptor = getMaxAttemptsInterceptor(target, method, reactiveRetryable);
				}
			}
			cachedMethods.putIfAbsent(method, interceptor);
			delegate = cachedMethods.get(method);
		}
		this.delegates.putIfAbsent(target, cachedMethods);
		return delegate == NULL_INTERCEPTOR ? null : delegate;
	}

	private MethodInterceptor getMaxAttemptsInterceptor(Object target, Method method,
			ReactiveRetryable reactiveRetryable) {
		return ReactiveRetryInterceptorBuilder.maxAttempts().setMaxAttempts(reactiveRetryable.maxAttempts())
				.setInclude(reactiveRetryable.include()).setExclude(reactiveRetryable.exclude()).build();
	}

	private MethodInterceptor getFixedDelayInterceptor(Object target, Method method,
			ReactiveRetryable reactiveRetryable) {
		return ReactiveRetryInterceptorBuilder.fixedDelay().setFixedDelay(reactiveRetryable.backOffFixDelay())
				.setMaxAttempts(reactiveRetryable.maxAttempts()).setInclude(reactiveRetryable.include())
				.setExclude(reactiveRetryable.exclude()).build();
	}

	private MethodInterceptor getMaxInRowInterceptor(Object target, Method method,
			ReactiveRetryable reactiveRetryable) {
		return ReactiveRetryInterceptorBuilder.maxInRow().setMaxAttempts(reactiveRetryable.maxAttempts())
				.setInclude(reactiveRetryable.include()).setExclude(reactiveRetryable.exclude()).build();
	}

	private MethodInterceptor getBackOffInterceptor(Object target, Method method, ReactiveRetryable reactiveRetryable) {
		return ReactiveRetryInterceptorBuilder.backOff().setBackOffFactor(reactiveRetryable.backOffFactor())
				.setMaxDelay(reactiveRetryable.backOffMaxDelay()).setMinDelay(reactiveRetryable.backOffMinDelay())
				.setExclude(reactiveRetryable.exclude()).setInclude(reactiveRetryable.include())
				.setMaxAttempts(reactiveRetryable.maxAttempts()).build();
	}

	private <A extends Annotation> A findAnnotationOnTarget(Object target, Method method, Class<A> annotation) {
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

	private <A extends Annotation> A classLevelAnnotation(Method method, Class<A> annotation) {
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
