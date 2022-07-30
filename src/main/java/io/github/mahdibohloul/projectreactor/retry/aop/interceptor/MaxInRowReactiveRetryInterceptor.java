package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetrySpec;

public class MaxInRowReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected MaxInRowReactiveRetryInterceptor(RetrySpec retryPolicy) {
        super(retryPolicy);
    }
}
