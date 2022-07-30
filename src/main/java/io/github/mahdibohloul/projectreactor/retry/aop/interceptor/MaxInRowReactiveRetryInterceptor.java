package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetrySpec;

/**
 * MaxInRowReactiveRetryInterceptor is a reactive retry interceptor that retries the invocation in a row.
 *
 * @author Mahdi Bohloul
 */
public class MaxInRowReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected MaxInRowReactiveRetryInterceptor(RetrySpec retryPolicy) {
        super(retryPolicy);
    }
}
