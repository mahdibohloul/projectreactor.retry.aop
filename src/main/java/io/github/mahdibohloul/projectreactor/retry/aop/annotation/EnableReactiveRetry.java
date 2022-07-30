package io.github.mahdibohloul.projectreactor.retry.aop.annotation;
;
import java.lang.annotation.*;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

/**
 * Enable reactive retryable aop capability. To be used on {@link
 * org.springframework.context.annotation.Configuration @Configuration} classes to configure
 * reactive retryable aop.
 *
 * @author mahdibohloul
 * @version 1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnableAspectJAutoProxy()
@Import(ReactiveRetryConfiguration.class)
@Documented
public @interface EnableReactiveRetry {
  /**
   * The order of the reactive retryable aop. The default value is {@link
   * Ordered#LOWEST_PRECEDENCE}.
   *
   * @return The order of the reactive retryable aop.
   */
  int order() default Ordered.LOWEST_PRECEDENCE;

  boolean proxyTargetClass() default false;
}
