package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetryBackoffSpec;

public class FixedDelayReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected FixedDelayReactiveRetryInterceptor(RetryBackoffSpec retryPolicy) {
        super(retryPolicy);
    }
}
