package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AliasFor;

/**
 * Annotation to enable reactive retry capabilities in a Spring application.
 * When applied to a configuration class, it provides configuration for handling
 * retry logic using the {@link ReactiveRetryable} annotation. It integrates
 * with Spring AOP and allows retry aspects to be applied to reactive methods in
 * the application.
 * <p>
 * Features enabled by this annotation include: - Support for annotating methods
 * or classes with {@link ReactiveRetryable}, which specifies retry behavior for
 * reactive methods. - Configuration of proxying strategies and aspect order for
 * retry logic.
 * <p>
 * This annotation imports {@link ReactiveRetryConfiguration} for setting up the
 * necessary infrastructure and components required for retry handling.
 * <p>
 * The annotation also builds on the functionality of Spring AOP by leveraging
 * {@link EnableAspectJAutoProxy}, allowing the use of proxies for retry logic.
 *
 * @author mahdibohloul
 * @since 1.0.0
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
