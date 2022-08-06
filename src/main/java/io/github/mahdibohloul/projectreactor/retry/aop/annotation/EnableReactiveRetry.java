package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AliasFor;

/**
 * Enable reactive retryable aop capability. To be used on
 * {@link org.springframework.context.annotation.Configuration @Configuration}
 * classes to configure reactive retryable aop.
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
     * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed
     * to standard Java interface-based proxies. The default is {@code false}.
     *
     * @return whether to proxy or not to proxy the class
     */
    @AliasFor(annotation = EnableAspectJAutoProxy.class)
    boolean proxyTargetClass() default false;

    /**
     * Indicate the order of the {@link ReactiveRetryable} aspect.
     *
     * @return the order of the aspect
     * @since 1.2.0
     */
    int order() default Ordered.LOWEST_PRECEDENCE;
}
