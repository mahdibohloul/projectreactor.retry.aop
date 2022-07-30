package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.lang.annotation.*;
import org.springframework.context.annotation.Import;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Import(ReactiveRetryConfiguration.class)
@Documented
public @interface ReactiveRecover {
}
