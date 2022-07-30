package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import reactor.util.retry.RetryBackoffSpec;

public class BackOffReactiveRetryInterceptor extends ReactiveRetryInterceptor {

    public BackOffReactiveRetryInterceptor(RetryBackoffSpec retryBackoffSpec) {
        super(retryBackoffSpec);
    }
}
