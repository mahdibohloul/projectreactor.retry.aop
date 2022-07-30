package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;

/**
 * Reactive retryable annotation. This annotation can be used on methods or
 * classes and will cause the calling method to be retried if it throws an
 * exception to the type specified in the retryOn array. Note that this
 * annotation only works on reactive methods.
 *
 * @author mahdibohloul
 * @version 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ReactiveRetryable {
    /**
     * The types of exceptions that should be retried. If this array is empty, all
     * exceptions are retried.
     *
     * @return The types of exceptions that should be retried.
     */
    Class<? extends Throwable>[] include() default {};

    /**
     * The types of exception that should be excluded from retry. If this array is
     * empty, no exceptions will be excluded.
     *
     * @return The types of exceptions that should be excluded from retry.
     */
    Class<? extends Throwable>[] exclude() default {};

    /**
     * The maximum number of attempts that should be made.
     *
     * @return The maximum number of attempts that should be made.
     */
    long maxAttempts() default 3;

    /**
     * Whether exponential backoff should be used.
     *
     * @return Whether exponential backoff should be used.
     */
    boolean exponentialBackoff() default false;

    /**
     * Whether the maximum number of attempts in a row should be checked.
     *
     * @return Whether the maximum number of attempts in a row should be checked.
     */
    boolean shouldCheckMaxInRow() default false;

    /**
     * The fixed delay that should be used for exponential backoff.
     *
     * @return The fixed delay that should be used for exponential backoff.
     */
    long backOffFixDelay() default -1;

    /**
     * The maximum delay that should be used for exponential backoff.
     *
     * @return The maximum delay that should be used for exponential backoff.
     */
    long backOffMaxDelay() default -1;

    /**
     * The minimum delay that should be used for exponential backoff.
     *
     * @return The minimum delay that should be used for exponential backoff.
     */
    long backOffMinDelay() default -1;

    /**
     * The factor that should be used for exponential backoff.
     *
     * @return The factor that should be used for exponential backoff.
     */
    double backOffFactor() default -1.0;

    /**
     * Retry interceptor bean name to be applied for retryable method. Is mutually
     * exclusive with other attributes.
     *
     * @return the retry interceptor bean name
     */
    String interceptor() default "";
}
