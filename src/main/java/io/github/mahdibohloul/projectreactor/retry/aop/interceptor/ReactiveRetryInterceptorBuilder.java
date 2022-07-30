package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import java.time.Duration;
import java.util.function.Predicate;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.util.Assert;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;
import reactor.util.retry.RetrySpec;

public abstract class ReactiveRetryInterceptorBuilder<T extends MethodInterceptor> {
    protected long maxAttempts;
    protected Class<? extends Throwable>[] retryOn = new Class[]{};
    protected Class<? extends Throwable>[] excludeFromRetryOn = new Class[]{};

    public ReactiveRetryInterceptorBuilder<T> setMaxAttempts(long maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than 0");
        }
        this.maxAttempts = maxAttempts;
        return this;
    }

    public ReactiveRetryInterceptorBuilder<T> setInclude(Class<? extends Throwable>[] retryOn) {
        Assert.notNull(retryOn, "retryOn cannot be null");
        this.retryOn = retryOn;
        return this;
    }

    public ReactiveRetryInterceptorBuilder<T> setExclude(Class<? extends Throwable>[] excludeFromRetryOn) {
        Assert.notNull(excludeFromRetryOn, "excludeFromRetryOn cannot be null");
        this.excludeFromRetryOn = excludeFromRetryOn;
        return this;
    }

    public abstract T build();

    public static BackOffRetryInterceptorBuilder backOff() {
        return new BackOffRetryInterceptorBuilder();
    }

    public static MaxInRowRetryInterceptorBuilder maxInRow() {
        return new MaxInRowRetryInterceptorBuilder();
    }

    public static FixedDelayRetryInterceptorBuilder fixedDelay() {
        return new FixedDelayRetryInterceptorBuilder();
    }

    public static MaxAttemptsRetryInterceptorBuilder maxAttempts() {
        return new MaxAttemptsRetryInterceptorBuilder();
    }

    protected Predicate<? super Throwable> errorFilter(Class<? extends Throwable>[] retryOn,
            Class<? extends Throwable>[] excludeFromRetryOn) {
        return throwable -> {
            for (Class<? extends Throwable> ex : this.excludeFromRetryOn) {
                if (ex.isAssignableFrom(throwable.getClass()))
                    return false;
            }
            if (this.retryOn.length == 0)
                return true;

            for (Class<? extends Throwable> ex : this.retryOn) {
                if (ex.isAssignableFrom(throwable.getClass()))
                    return true;
            }
            return false;
        };
    }

    public static class MaxAttemptsRetryInterceptorBuilder
            extends
                ReactiveRetryInterceptorBuilder<MaxAttemptsReactiveRetryInterceptor> {

        @Override
        public MaxAttemptsReactiveRetryInterceptor build() {
            RetrySpec retrySpec = Retry.max(this.maxAttempts)
                    .filter(errorFilter(this.retryOn, this.excludeFromRetryOn));
            return new MaxAttemptsReactiveRetryInterceptor(retrySpec);
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxAttemptsReactiveRetryInterceptor> setMaxAttempts(long maxAttempts) {
            super.setMaxAttempts(maxAttempts);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxAttemptsReactiveRetryInterceptor> setExclude(
                Class<? extends Throwable>[] excludeFromRetryOn) {
            super.setExclude(excludeFromRetryOn);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxAttemptsReactiveRetryInterceptor> setInclude(
                Class<? extends Throwable>[] retryOn) {
            super.setInclude(retryOn);
            return this;
        }
    }

    public static class FixedDelayRetryInterceptorBuilder
            extends
                ReactiveRetryInterceptorBuilder<FixedDelayReactiveRetryInterceptor> {

        private long fixedDelay;

        @Override
        public FixedDelayReactiveRetryInterceptor build() {
            RetryBackoffSpec retrySpec = Retry.fixedDelay(this.maxAttempts, Duration.ofMillis(this.fixedDelay))
                    .filter(errorFilter(this.retryOn, this.excludeFromRetryOn));
            return new FixedDelayReactiveRetryInterceptor(retrySpec);
        }

        @Override
        public ReactiveRetryInterceptorBuilder<FixedDelayReactiveRetryInterceptor> setMaxAttempts(long maxAttempts) {
            super.setMaxAttempts(maxAttempts);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<FixedDelayReactiveRetryInterceptor> setInclude(
                Class<? extends Throwable>[] retryOn) {
            super.setInclude(retryOn);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<FixedDelayReactiveRetryInterceptor> setExclude(
                Class<? extends Throwable>[] excludeFromRetryOn) {
            super.setExclude(excludeFromRetryOn);
            return this;
        }

        public ReactiveRetryInterceptorBuilder<FixedDelayReactiveRetryInterceptor> setFixedDelay(long fixedDelay) {
            this.fixedDelay = fixedDelay;
            return this;
        }
    }

    public static class MaxInRowRetryInterceptorBuilder
            extends
                ReactiveRetryInterceptorBuilder<MaxInRowReactiveRetryInterceptor> {
        @Override
        public MaxInRowReactiveRetryInterceptor build() {
            RetrySpec retrySpec = Retry.maxInARow(this.maxAttempts);
            retrySpec = retrySpec.filter(errorFilter(this.retryOn, this.excludeFromRetryOn));
            return new MaxInRowReactiveRetryInterceptor(retrySpec);
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxInRowReactiveRetryInterceptor> setMaxAttempts(long maxAttempts) {
            super.setMaxAttempts(maxAttempts);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxInRowReactiveRetryInterceptor> setExclude(
                Class<? extends Throwable>[] excludeFromRetryOn) {
            super.setExclude(excludeFromRetryOn);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<MaxInRowReactiveRetryInterceptor> setInclude(
                Class<? extends Throwable>[] retryOn) {
            super.setInclude(retryOn);
            return this;
        }
    }

    public static class BackOffRetryInterceptorBuilder
            extends
                ReactiveRetryInterceptorBuilder<BackOffReactiveRetryInterceptor> {
        private long minDelay = -1;
        private long maxDelay = -1;
        private double backOffFactor = -1.0;

        @Override
        public BackOffReactiveRetryInterceptor build() {
            RetryBackoffSpec retryBackoffSpec = Retry.backoff(this.maxAttempts, Duration.ofMillis(100));
            if (this.minDelay > 0)
                retryBackoffSpec = retryBackoffSpec.minBackoff(Duration.ofMillis(this.minDelay));
            if (this.maxDelay > 0)
                retryBackoffSpec = retryBackoffSpec.maxBackoff(Duration.ofMillis(this.maxDelay));
            if (this.backOffFactor > 0)
                retryBackoffSpec = retryBackoffSpec.jitter(this.backOffFactor);
            retryBackoffSpec.filter(errorFilter(this.retryOn, this.excludeFromRetryOn));

            return new BackOffReactiveRetryInterceptor(retryBackoffSpec);
        }

        public BackOffRetryInterceptorBuilder setMinDelay(long minDelay) {
            this.minDelay = minDelay;
            return this;
        }

        public BackOffRetryInterceptorBuilder setMaxDelay(long maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        public BackOffRetryInterceptorBuilder setBackOffFactor(double backOffFactor) {
            this.backOffFactor = backOffFactor;
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<BackOffReactiveRetryInterceptor> setInclude(
                Class<? extends Throwable>[] retryOn) {
            super.setInclude(retryOn);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<BackOffReactiveRetryInterceptor> setExclude(
                Class<? extends Throwable>[] excludeFromRetryOn) {
            super.setExclude(excludeFromRetryOn);
            return this;
        }

        @Override
        public ReactiveRetryInterceptorBuilder<BackOffReactiveRetryInterceptor> setMaxAttempts(long maxAttempts) {
            super.setMaxAttempts(maxAttempts);
            return this;
        }
    }
}
