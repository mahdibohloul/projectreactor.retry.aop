package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import io.github.mahdibohloul.projectreactor.retry.aop.ApplicationTests;
import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryable;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.Exceptions;
import reactor.test.StepVerifier;

class EnableReactiveRetryTests {
	@Test
	void successfulRetry() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).verifyComplete();
		Assertions.assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	void exhaustedRetry() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.exhaustedRetry()).expectErrorMatches(error -> error instanceof RuntimeException
				&& !Exceptions.isRetryExhausted(error) && Objects.equals(error.getMessage(), "error")).verify();
		Assertions.assertEquals(2, service.getCount());
		context.close();
	}

	@Test
	void backOffComposesWithMaxInRow() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.TransientErrorsBackOffService service = context
				.getBean(ApplicationTests.TransientErrorsBackOffService.class);
		// Three failures on a budget of two, survived because each is preceded by an
		// emission that resets the in-a-row counter.
		StepVerifier.create(service.emitThenFail()).expectNext("value", "value", "value", "value").verifyComplete();
		Assertions.assertEquals(4, service.getSubscriptions());
		context.close();
	}

	@Test
	void backOffWithMaxInRowStillExhaustsWithoutEmissions() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.TransientErrorsBackOffService service = context
				.getBean(ApplicationTests.TransientErrorsBackOffService.class);
		// Nothing ever resets the counter, so the budget is spent over the lifetime of
		// the subscription: the initial attempt plus two retries.
		StepVerifier.create(service.failWithoutEmitting()).expectError(RuntimeException.class).verify();
		Assertions.assertEquals(3, service.getSubscriptions());
		context.close();
	}

	@Test
	void withoutRetryApplication() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.WithoutRetryConfiguration.class);
		ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
		Assertions.assertFalse(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).expectError(RuntimeException.class).verify();
		Assertions.assertEquals(1, service.getCount());
		context.close();
	}

	@Test
	void proxyTargetClass() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestProxyConfiguration.class);
		ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
		Assertions.assertTrue(AopUtils.isCglibProxy(service));
		context.close();
	}

	@Test
	void marker() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.Service service = context.getBean(ApplicationTests.Service.class);
		Assertions.assertTrue(AopUtils.isCglibProxy(service));
		Assertions.assertInstanceOf(ReactiveRetryable.class, service);
		context.close();
	}

	@Test
	void excludedService() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.ExcludesService service = context.getBean(ApplicationTests.ExcludesService.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).expectError(IllegalStateException.class).verify();
		Assertions.assertEquals(1, service.getCount());
		context.close();
	}

	@Test
	void inheritedExcludeService() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.InheritedExcludesService service = context
				.getBean(ApplicationTests.InheritedExcludesService.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).expectError(ApplicationTests.ChildIllegalStateException.class).verify();
		Assertions.assertEquals(1, service.getCount());
		context.close();
	}

	@Test
	void inheritedExcludeBackOffService() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.InheritedExcludesBackOffService service = context
				.getBean(ApplicationTests.InheritedExcludesBackOffService.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).expectError(ApplicationTests.ChildIllegalStateException.class).verify();
		Assertions.assertEquals(1, service.getCount());
		context.close();
	}

	@Test
	void type() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.RetryableService service = context.getBean(ApplicationTests.RetryableService.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).verifyComplete();
		Assertions.assertEquals(3, service.getCount());
		context.close();
	}

	@Test
	void customInterceptor() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
				ApplicationTests.TestConfiguration.class);
		ApplicationTests.CustomInterceptorService service = context
				.getBean(ApplicationTests.CustomInterceptorService.class);
		Assertions.assertTrue(AopUtils.isAopProxy(service));
		StepVerifier.create(service.service()).expectError(RuntimeException.class).verify();
		Assertions.assertEquals(6, service.getCount());
		context.close();
	}
}
