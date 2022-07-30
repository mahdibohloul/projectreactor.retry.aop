package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetrySpec;

public class MaxAttemptsReactiveRetryInterceptor extends ReactiveRetryInterceptor {
    protected MaxAttemptsReactiveRetryInterceptor(RetrySpec retryPolicy) {
        super(retryPolicy);
    }
}
