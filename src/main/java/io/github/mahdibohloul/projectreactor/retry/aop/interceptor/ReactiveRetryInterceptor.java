package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Abstract class for reactive retry interceptor.
 *
 * @author Mahdi Bohloul
 */
public abstract class ReactiveRetryInterceptor implements MethodInterceptor {

    private final Retry retryPolicy;

    protected ReactiveRetryInterceptor(Retry retryPolicy) {
        this.retryPolicy = retryPolicy;
    }

    /**
     * Retry the given invocation using the configured retry policy.
     * This function uses project reactor's retry mechanism to provide retry support.
     *
     * @param invocation the method invocation joinpoint
     * @return the result of the invocation
     * @throws Throwable if the invocation fails
     */
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
