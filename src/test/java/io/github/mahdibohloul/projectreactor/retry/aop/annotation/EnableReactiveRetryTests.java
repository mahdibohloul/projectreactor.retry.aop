package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mahdibohloul.projectreactor.retry.aop.ApplicationTests;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.test.StepVerifier;

public class EnableReactiveRetryTests {
    @Test
    public void successfulRetry() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        StepVerifier.create(service.service()).verifyComplete();
        assertThat(service.getCount()).isEqualTo(3);
        context.close();
    }

    @Test
    public void exhaustedRetry() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        StepVerifier.create(service.exhaustedRetry()).expectError(RuntimeException.class).verify();
        assertThat(service.getCount()).isEqualTo(2);
        context.close();
    }

    @Test
    public void withoutRetryApplication() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.WithoutRetryConfiguration.class);
        ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
        assertThat(AopUtils.isAopProxy(service)).isFalse();
        StepVerifier.create(service.service()).expectError(RuntimeException.class).verify();
        assertThat(service.getCount()).isEqualTo(1);
        context.close();
    }

    @Test
    public void proxyTargetClass() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestProxyConfiguration.class);
        ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
        assertThat(AopUtils.isCglibProxy(service)).isTrue();
        context.close();
    }

    @Test
    public void marker() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
        assertThat(AopUtils.isCglibProxy(service)).isTrue();
        assertThat(service instanceof io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryable)
                .isTrue();
        context.close();
    }

    @Test
    public void excludedService() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.ExcludesService service = context.getBean(ApplicationTests.ExcludesService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        StepVerifier.create(service.service()).expectError(IllegalStateException.class).verify();
        assertThat(service.getCount()).isEqualTo(1);
        context.close();
    }

    @Test
    public void type() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.RetryableService service = context.getBean(ApplicationTests.RetryableService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        StepVerifier.create(service.service()).verifyComplete();
        assertThat(service.getCount()).isEqualTo(3);
        context.close();
    }

    @Test
    public void customInterceptor() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ApplicationTests.TestConfiguration.class);
        ApplicationTests.CustomInterceptorService service = context
                .getBean(ApplicationTests.CustomInterceptorService.class);
        assertThat(AopUtils.isAopProxy(service)).isTrue();
        StepVerifier.create(service.service()).expectError(RuntimeException.class).verify();
        assertThat(service.getCount()).isEqualTo(6);
        context.close();
    }
}
