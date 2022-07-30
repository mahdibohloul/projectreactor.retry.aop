package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetryBackoffSpec;

/**
 * FixedDelayReactiveRetryInterceptor is a reactive retry interceptor that retries the invocation with a fixed delay.
 *
 * @author Mahdi Bohloul
 */
public class FixedDelayReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected FixedDelayReactiveRetryInterceptor(RetryBackoffSpec retryPolicy) {
        super(retryPolicy);
    }
}
