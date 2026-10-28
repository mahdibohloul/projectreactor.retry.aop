package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

/**
 * Gives a replacement publisher when the retries of a method fail.
 *
 * <p>
 * A {@link ReactiveRetryInterceptor} calls the recoverer after the last retry
 * fails, and also when it does not retry the error. The recoverer gets the
 * error that the interceptor signals: the last error, not Reactor's
 * {@code RetryExhaustedException}.
 *
 * @author Mahdi Bohloul
 * @since 3.0.0
 * @see ReactiveRetryInterceptorBuilder#setRecoverer(ReactiveRecoverer)
 */
@FunctionalInterface
public interface ReactiveRecoverer {

	/**
	 * Recovers from the error of the given invocation.
	 *
	 * @param invocation
	 *            the original method invocation
	 * @param error
	 *            the error after the retries
	 * @return the publisher that replaces the failed one, or {@code null} if this
	 *         recoverer cannot recover from the error. Then the caller gets the
	 *         error.
	 * @throws Throwable
	 *             if the recovery fails. The caller gets this error.
	 */
	@Nullable
	Publisher<?> recover(MethodInvocation invocation, Throwable error) throws Throwable;
}
