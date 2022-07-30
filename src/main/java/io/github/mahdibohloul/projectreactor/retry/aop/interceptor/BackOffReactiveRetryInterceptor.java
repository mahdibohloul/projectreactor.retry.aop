package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetryBackoffSpec;

/**
 * FixedDelayReactiveRetryInterceptor is a reactive retry interceptor that retries
 * the invocation with a backoff exponential policy.
 *
 * @author Mahdi Bohloul
 */
public class BackOffReactiveRetryInterceptor extends ReactiveRetryInterceptor {

    public BackOffReactiveRetryInterceptor(RetryBackoffSpec retryBackoffSpec) {
        super(retryBackoffSpec);
    }
}
