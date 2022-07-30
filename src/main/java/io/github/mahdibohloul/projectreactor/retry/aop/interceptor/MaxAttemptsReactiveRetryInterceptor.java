package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetrySpec;

/**
 * MaxInRowReactiveRetryInterceptor is a reactive retry interceptor that retries
 * the invocation with a max possible number of retries.
 *
 * @author Mahdi Bohloul
 */
public class MaxAttemptsReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected MaxAttemptsReactiveRetryInterceptor(RetrySpec retryPolicy) {
        super(retryPolicy);
    }
}
