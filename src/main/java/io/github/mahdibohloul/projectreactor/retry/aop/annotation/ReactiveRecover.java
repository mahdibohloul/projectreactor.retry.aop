package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

/**
 * Marks a method that recovers when the retries of a {@link ReactiveRetryable}
 * method fail.
 *
 * <p>
 * The library calls the recover method in these conditions:
 * <ul>
 * <li>The last retry fails.</li>
 * <li>The error is not retried, because {@link ReactiveRetryable#include()} or
 * {@link ReactiveRetryable#exclude()} stops it.</li>
 * </ul>
 * The caller then gets the result of the recover method, not the error. The
 * recover method gets the last error, not Reactor's
 * {@code RetryExhaustedException}.
 *
 * <p>
 * The recover method must be in the same bean as the retryable method. The
 * library finds it in one of these ways:
 * <ul>
 * <li>If {@link ReactiveRetryable#recover()} is set, the library uses the
 * recover method with that name.</li>
 * <li>If it is empty, the library uses the recover methods of the bean's class
 * and its superclasses that have a fitting signature.</li>
 * </ul>
 *
 * <p>
 * Signature rules:
 * <ul>
 * <li>The first parameter is optional. If you add it, it must be a
 * {@link Throwable} type. The library calls the method only if the error is an
 * instance of this type.</li>
 * <li>The other parameters must have the same types as the parameters of the
 * retryable method. They get the same argument values.</li>
 * <li>A method that returns {@code Mono<T>} recovers a method that returns
 * {@code Mono<T>}. A method that returns {@code Flux<T>} recovers a method that
 * returns {@code Flux<T>}.</li>
 * <li>A Kotlin {@code suspend} function recovers a {@code suspend} function.
 * The return types must be compatible. The recover function gets the coroutine
 * context of the caller.</li>
 * </ul>
 *
 * <p>
 * If more than one method fits, the library uses the method whose
 * {@link Throwable} parameter is the closest supertype of the error. A method
 * without a {@link Throwable} parameter is the last choice. If no method fits
 * the error, the caller gets the error.
 *
 * <p>
 * If the recover method fails, the caller gets its error. The library calls the
 * recover method on the target object, not through the proxy, so other advice
 * does not apply to it.
 *
 * <p>
 * A method with this annotation is not retried by a type-level
 * {@link ReactiveRetryable} declaration.
 *
 * <p>
 * Recovery does not apply to a custom interceptor bean that
 * {@link ReactiveRetryable#interceptor()} names.
 *
 * @author mahdibohloul
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import(ReactiveRetryConfiguration.class)
@Documented
public @interface ReactiveRecover {
}
