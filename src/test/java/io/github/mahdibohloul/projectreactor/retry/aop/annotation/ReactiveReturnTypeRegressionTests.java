package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Pins down how return types other than a plain Mono or Flux are handled.
 */
class ReactiveReturnTypeRegressionTests {

	@Configuration
	@EnableReactiveRetry(proxyTargetClass = true)
	static class Config {
		@Bean
		ReturnTypes returnTypes() {
			return new ReturnTypes();
		}
	}

	static class ReturnTypes {
		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger subscriptions = new AtomicInteger();
		final Publisher<String> failingPublisher = Flux.defer(() -> {
			this.subscriptions.incrementAndGet();
			return Flux.error(new IllegalStateException("publisher"));
		});

		public int calls() {
			return this.calls.get();
		}

		public int subscriptions() {
			return this.subscriptions.get();
		}

		public Publisher<String> failingPublisher() {
			return this.failingPublisher;
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Publisher<String> publisher() {
			this.calls.incrementAndGet();
			return this.failingPublisher;
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> throwsBeforeReturning() {
			if (this.calls.incrementAndGet() < 3)
				throw new IllegalStateException("sync");
			return Mono.just("ok");
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Flux<String> fluxThrowsBeforeReturning() {
			if (this.calls.incrementAndGet() < 3)
				throw new IllegalStateException("sync");
			return Flux.just("a", "b");
		}

		@ReactiveRetryable(maxAttempts = 2)
		public String notReactive() {
			this.calls.incrementAndGet();
			throw new IllegalStateException("plain");
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> returnsNull() {
			this.calls.incrementAndGet();
			return null;
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> emptyMono() {
			this.calls.incrementAndGet();
			return Mono.empty();
		}
	}

	@Test
	void otherPublisherIsNotRetriedAndIsReturnedAsIs() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			Publisher<String> result = service.publisher();
			Assertions.assertSame(service.failingPublisher(), result);
			StepVerifier.create(result).expectError(IllegalStateException.class).verify();
			Assertions.assertEquals(1, service.calls());
			Assertions.assertEquals(1, service.subscriptions());
		}
	}

	@Test
	void monoMethodThrowingSynchronouslyIsInvokedAgain() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			Mono<String> result = service.throwsBeforeReturning();
			Assertions.assertEquals(0, service.calls(), "invocation is deferred until subscription");
			StepVerifier.create(result).expectNext("ok").verifyComplete();
			Assertions.assertEquals(3, service.calls());
		}
	}

	@Test
	void fluxMethodThrowingSynchronouslyIsInvokedAgain() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			StepVerifier.create(service.fluxThrowsBeforeReturning()).expectNext("a", "b").verifyComplete();
			Assertions.assertEquals(3, service.calls());
		}
	}

	@Test
	void nonReactiveMethodPassesThrough() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			Assertions.assertThrows(IllegalStateException.class, service::notReactive);
			Assertions.assertEquals(1, service.calls());
		}
	}

	@Test
	void monoMethodReturningNullIsRetriedAsAnError() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			StepVerifier.create(service.returnsNull()).expectError(NullPointerException.class).verify();
			Assertions.assertEquals(3, service.calls());
		}
	}

	@Test
	void emptyMonoIsNotRetried() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
			ReturnTypes service = context.getBean(ReturnTypes.class);
			StepVerifier.create(service.emptyMono()).verifyComplete();
			Assertions.assertEquals(1, service.calls());
		}
	}
}
