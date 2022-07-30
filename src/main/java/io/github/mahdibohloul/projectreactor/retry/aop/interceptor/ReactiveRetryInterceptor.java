package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

public abstract class ReactiveRetryInterceptor implements MethodInterceptor {

    private final Retry retryPolicy;

    protected ReactiveRetryInterceptor(Retry retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        if (!isPublisher(invocation.getMethod().getReturnType()))
            return invocation.proceed();
        if (isMono(invocation.getMethod().getReturnType()))
            return Mono.defer(() -> {
                try {
                    return (Mono) invocation.proceed();
                } catch (Throwable t) {
                    return Mono.error(t);
                }
            }).retryWhen(retryPolicy);
        if (isFlux(invocation.getMethod().getReturnType()))
            return Flux.defer(() -> {
                try {
                    return (Flux) invocation.proceed();
                } catch (Throwable t) {
                    return Flux.error(t);
                }
            }).retryWhen(this.retryPolicy);
        return invocation.proceed();
    }

    private boolean isFlux(Class<?> returnType) {
        return returnType.equals(Flux.class);
    }

    private boolean isMono(Class<?> returnType) {
        return returnType.equals(Mono.class);
    }

    private boolean isPublisher(Class<?> returnType) {
        return Publisher.class.isAssignableFrom(returnType);
    }
}
