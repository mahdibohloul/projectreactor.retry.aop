package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;

/**
 * Annotation to enable retry functionality for reactive methods or classes.
 * This annotation works in conjunction with reactive programming to provide
 * declarative retry capabilities in a reactive context.
 * <p>
 * This annotation allows configuration of retry handling via attributes such as
 * - The types of exceptions to include or exclude for retry. - Maximum number
 * of retry attempts. - Exponential backoff strategy with customizable delay and
 * factors. - Custom retry interceptor bean name for more advanced use cases.
 * <p>
 * It can be applied at the method or class level to specify retry behavior
 * based on reactive streams. It is commonly used in scenarios where transient
 * failures or network-related issues occur, and retrying operations is
 * necessary.
 * <p>
 * The retry logic managed by this annotation is designed to handle reactive
 * streams in a non-blocking and efficient manner.
 * <p>
 * Attributes: - `include`: Specify the exception types to include for retry. By
 * default, all exceptions will be retried if no exclusions are specified. -
 * `exclude`: Specify the exception types to exclude from retry. If left empty,
 * no exceptions are excluded. - `maxAttempts`: Define the maximum number of
 * retry attempts allowed. - `exponentialBackoff`: Enable or disable exponential
 * backoff for retry operations. - `shouldCheckMaxInRow`: Determine whether to
 * check and enforce a maximum number of retries in succession. -
 * `backOffFixDelay`: Configure a fixed delay for exponential backoff retries. -
 * `backOffMaxDelay`: Specify the maximum delay for exponential backoff. -
 * `backOffMinDelay`: Specify the minimum delay for exponential backoff. -
 * `backOffFactor`: Define the factor for calculating delays in exponential
 * backoff. - `interceptor`: Specify the name of a retry interceptor bean for
 * custom retry logic.
 * <p>
 * This annotation is part of a broader reactive retry mechanism and is
 * typically enabled in combination with {@link EnableReactiveRetry}. It allows
 * fine-grained control and customization over retry behavior in a reactive
 * programming model.
 *
 * @author mahdibohloul
 * @since 1.0.0
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
