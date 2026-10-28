package io.github.mahdibohloul.projectreactor.retry.aop.annotation;

import io.github.mahdibohloul.projectreactor.retry.aop.ApplicationTests;
import io.github.mahdibohloul.projectreactor.retry.aop.interceptor.ReactiveRetryInterceptorBuilder;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for {@link ReactiveRecover} methods.
 */
class ReactiveRecoverTests {

	private AnnotationConfigApplicationContext context;

	@AfterEach
	void close() {
		if (this.context != null)
			this.context.close();
	}

	private <T> T bean(Class<?> configuration, Class<T> type) {
		this.context = new AnnotationConfigApplicationContext(configuration);
		return this.context.getBean(type);
	}

	@Test
	void recoverByName() {
		ByNameService service = bean(Config.class, ByNameService.class);
		// The name wins over the closer match of "closer".
		StepVerifier.create(service.load("42")).expectNext("fallback:42:load 42").verifyComplete();
		Assertions.assertEquals(3, service.calls());
	}

	@Test
	void recoverBySignature() {
		SignatureService service = bean(Config.class, SignatureService.class);
		StepVerifier.create(service.load("state", 7)).expectNext("state:state:7:state error").verifyComplete();
		Assertions.assertEquals(3, service.calls());
	}

	@Test
	void closestMatchIsChosen() {
		SignatureService service = bean(Config.class, SignatureService.class);
		// ChildIllegalStateException -> IllegalStateException is closer than
		// RuntimeException.
		StepVerifier.create(service.load("child", 1)).expectNext("state:child:1:child error").verifyComplete();
		// Again, to use the cached choice.
		StepVerifier.create(service.load("child", 2)).expectNext("state:child:2:child error").verifyComplete();
		StepVerifier.create(service.load("arg", 3)).expectNext("runtime:arg:3:arg error").verifyComplete();
	}

	@Test
	void recoverWithoutThrowableParameter() {
		SignatureService service = bean(Config.class, SignatureService.class);
		// Only the method without a Throwable parameter accepts an IOException.
		StepVerifier.create(service.load("io", 4)).expectNext("any:io:4").verifyComplete();
		Assertions.assertEquals(3, service.calls());
	}

	@Test
	void argumentsArePassed() {
		SignatureService service = bean(Config.class, SignatureService.class);
		StepVerifier.create(service.load("state", 9)).expectNext("state:state:9:state error").verifyComplete();
		Assertions.assertEquals(List.of("state/9"), service.recovered());
	}

	@Test
	void excludedErrorIsRecoveredWithoutRetry() {
		MiscService service = bean(Config.class, MiscService.class);
		StepVerifier.create(service.excluded("x")).expectNext("excluded:x:not retried").verifyComplete();
		Assertions.assertEquals(1, service.calls());
	}

	@Test
	void noMatchingRecoverMethodGivesTheLastError() {
		MiscService service = bean(Config.class, MiscService.class);
		StepVerifier.create(service.noMatch(5)).expectErrorMatches(error -> error instanceof IllegalStateException
				&& !Exceptions.isRetryExhausted(error) && "no match 3".equals(error.getMessage())).verify();
		Assertions.assertEquals(3, service.calls());
		Assertions.assertEquals(0, service.recoverCalls());
	}

	@Test
	void errorOfTheRecoverMethodGoesToTheCaller() {
		MiscService service = bean(Config.class, MiscService.class);
		StepVerifier.create(service.recoverThrows(1L)).expectErrorMatches(
				error -> error instanceof IllegalArgumentException && "recover failed".equals(error.getMessage()))
				.verify();
		Assertions.assertEquals(2, service.calls());
		Assertions.assertEquals(1, service.recoverCalls());
	}

	@Test
	void errorSignalledByTheRecoverPublisherGoesToTheCaller() {
		MiscService service = bean(Config.class, MiscService.class);
		StepVerifier.create(service.recoverSignalsError(1.0))
				.expectErrorMatches(
						error -> error instanceof IOException && "recover signalled".equals(error.getMessage()))
				.verify();
		Assertions.assertEquals(2, service.calls());
	}

	@Test
	void aMethodWithBothAnnotationsDoesNotRecoverItself() {
		SelfService service = bean(Config.class, SelfService.class);
		// The first call and two retries. No fourth call as its own recover method.
		StepVerifier.create(service.both('x'))
				.expectErrorMatches(
						error -> error instanceof IllegalStateException && "both call 3".equals(error.getMessage()))
				.verify();
		Assertions.assertEquals(3, service.calls());
	}

	@Test
	void aMethodWithBothAnnotationsRecoversAnotherMethodWithoutRetry() {
		SelfService service = bean(Config.class, SelfService.class);
		StepVerifier.create(service.other('y'))
				.expectErrorMatches(
						error -> error instanceof IllegalStateException && "both call 1".equals(error.getMessage()))
				.verify();
		Assertions.assertEquals(3, service.otherCalls());
		Assertions.assertEquals(1, service.calls());
	}

	@Test
	void recoverNamingTheMethodItselfFails() {
		SelfService service = bean(Config.class, SelfService.class);
		IllegalStateException error = Assertions.assertThrows(IllegalStateException.class, service::selfNamed);
		Assertions.assertTrue(error.getMessage().contains("cannot recover itself"), error.getMessage());
	}

	@Test
	void fluxRecovery() {
		MiscService service = bean(Config.class, MiscService.class);
		StepVerifier.create(service.flux("a", "b")).expectNext("a", "a", "a", "recovered:a:b:flux error")
				.verifyComplete();
		Assertions.assertEquals(3, service.calls());
	}

	@Test
	void recoverMethodOfAClassLevelRetryableIsNotRetried() {
		ClassLevelService service = bean(Config.class, ClassLevelService.class);
		StepVerifier.create(service.load()).expectNext("class level recovered").verifyComplete();
		Assertions.assertEquals(3, service.calls());
		Assertions.assertEquals(1, service.recoverCalls());

		// Called through the proxy, the recover method itself is not retried.
		service.failRecover();
		StepVerifier.create(service.fallback(new IllegalStateException("direct")))
				.expectError(IllegalArgumentException.class).verify();
		Assertions.assertEquals(2, service.recoverCalls());
	}

	@Test
	void recoverNamingAMissingMethodFails() {
		InvalidService service = bean(Config.class, InvalidService.class);
		IllegalStateException error = Assertions.assertThrows(IllegalStateException.class, service::missing);
		Assertions.assertTrue(error.getMessage().contains("doesNotExist"), error.getMessage());
		Assertions.assertTrue(error.getMessage().contains("missing()"), error.getMessage());
		Assertions.assertEquals(0, service.calls());
	}

	@Test
	void recoverNamingAMethodThatDoesNotFitFails() {
		InvalidService service = bean(Config.class, InvalidService.class);
		IllegalStateException error = Assertions.assertThrows(IllegalStateException.class, service::wrongReturnType);
		Assertions.assertTrue(error.getMessage().contains("wrongReturnType()"), error.getMessage());
		Assertions.assertTrue(error.getMessage().contains("integerFallback"), error.getMessage());
	}

	@Test
	void recoverTogetherWithInterceptorFails() {
		InvalidService service = bean(Config.class, InvalidService.class);
		IllegalStateException error = Assertions.assertThrows(IllegalStateException.class, service::withInterceptor);
		Assertions.assertTrue(error.getMessage().contains("interceptor"), error.getMessage());
		Assertions.assertTrue(error.getMessage().contains("withInterceptor()"), error.getMessage());
	}

	@Test
	void jdkInterfaceProxy() {
		Greeter greeter = bean(JdkProxyConfig.class, Greeter.class);
		Assertions.assertTrue(AopUtils.isJdkDynamicProxy(greeter));
		StepVerifier.create(greeter.greet("Ada")).expectNext("hello Ada from fallback").verifyComplete();
		StepVerifier.create(greeter.greetByName("Ada")).expectNext("hi Ada by name").verifyComplete();
	}

	@Configuration
	@EnableReactiveRetry(proxyTargetClass = true)
	static class Config {
		@Bean
		ByNameService byNameService() {
			return new ByNameService();
		}

		@Bean
		SignatureService signatureService() {
			return new SignatureService();
		}

		@Bean
		MiscService miscService() {
			return new MiscService();
		}

		@Bean
		ClassLevelService classLevelService() {
			return new ClassLevelService();
		}

		@Bean
		InvalidService invalidService() {
			return new InvalidService();
		}

		@Bean
		SelfService selfService() {
			return new SelfService();
		}

		@Bean
		MethodInterceptor customInterceptor() {
			return ReactiveRetryInterceptorBuilder.maxAttempts().setMaxAttempts(1).build();
		}
	}

	@Configuration
	@EnableReactiveRetry(proxyTargetClass = false)
	static class JdkProxyConfig {
		@Bean
		Greeter greeter() {
			return new GreeterImpl();
		}
	}

	static class ByNameService {
		final AtomicInteger calls = new AtomicInteger();

		public int calls() {
			return this.calls.get();
		}

		@ReactiveRetryable(maxAttempts = 2, recover = "fallback")
		public Mono<String> load(String id) {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalStateException("load " + id));
		}

		@ReactiveRecover
		public Mono<String> fallback(RuntimeException error, String id) {
			return Mono.just("fallback:" + id + ":" + error.getMessage());
		}

		@ReactiveRecover
		public Mono<String> closer(IllegalStateException error, String id) {
			return Mono.just("closer:" + id);
		}
	}

	static class SignatureService {
		final AtomicInteger calls = new AtomicInteger();
		final List<String> recovered = new CopyOnWriteArrayList<>();

		public int calls() {
			return this.calls.get();
		}

		public List<String> recovered() {
			return this.recovered;
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> load(String kind, int number) {
			this.calls.incrementAndGet();
			return switch (kind) {
				case "child" -> Mono.error(new ApplicationTests.ChildIllegalStateException("child error"));
				case "state" -> Mono.error(new IllegalStateException("state error"));
				case "arg" -> Mono.error(new IllegalArgumentException("arg error"));
				default -> Mono.error(new IOException("io error"));
			};
		}

		@ReactiveRecover
		public Mono<String> recoverRuntime(RuntimeException error, String kind, int number) {
			return Mono.just("runtime:" + kind + ":" + number + ":" + error.getMessage());
		}

		@ReactiveRecover
		public Mono<String> recoverState(IllegalStateException error, String kind, int number) {
			this.recovered.add(kind + "/" + number);
			return Mono.just("state:" + kind + ":" + number + ":" + error.getMessage());
		}

		@ReactiveRecover
		public Mono<String> recoverAny(String kind, int number) {
			return Mono.just("any:" + kind + ":" + number);
		}

		// Does not fit: other parameter types.
		@ReactiveRecover
		public Mono<String> recoverOther(IllegalStateException error, String kind, long number) {
			return Mono.just("other");
		}
	}

	static class MiscService {
		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger recoverCalls = new AtomicInteger();

		public int calls() {
			return this.calls.get();
		}

		public int recoverCalls() {
			return this.recoverCalls.get();
		}

		@ReactiveRetryable(maxAttempts = 2, exclude = IllegalArgumentException.class)
		public Mono<String> excluded(String id) {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalArgumentException("not retried"));
		}

		@ReactiveRecover
		public Mono<String> recoverExcluded(IllegalArgumentException error, String id) {
			return Mono.just("excluded:" + id + ":" + error.getMessage());
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> noMatch(int id) {
			return Mono.error(new IllegalStateException("no match " + this.calls.incrementAndGet()));
		}

		// Does not accept the IllegalStateException.
		@ReactiveRecover
		public Mono<String> recoverNoMatch(IllegalArgumentException error, int id) {
			this.recoverCalls.incrementAndGet();
			return Mono.just("wrong");
		}

		// Does not fit: a Flux does not recover a Mono.
		@ReactiveRecover
		public Flux<String> recoverNoMatchWithFlux(int id) {
			this.recoverCalls.incrementAndGet();
			return Flux.just("wrong");
		}

		@ReactiveRetryable(maxAttempts = 1)
		public Mono<String> recoverThrows(long id) {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalStateException("retryable failed"));
		}

		@ReactiveRecover
		public Mono<String> throwingRecover(Exception error, long id) {
			this.recoverCalls.incrementAndGet();
			throw new IllegalArgumentException("recover failed");
		}

		@ReactiveRetryable(maxAttempts = 1)
		public Mono<String> recoverSignalsError(double id) {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalStateException("retryable failed"));
		}

		@ReactiveRecover
		public Mono<String> signallingRecover(double id) {
			return Mono.error(new IOException("recover signalled"));
		}

		@ReactiveRetryable(maxAttempts = 2)
		public Flux<String> flux(String first, String second) {
			this.calls.incrementAndGet();
			return Flux.concat(Flux.just(first), Flux.error(new IllegalStateException("flux error")));
		}

		@ReactiveRecover
		public Flux<String> recoverFlux(RuntimeException error, String first, String second) {
			return Flux.just("recovered:" + first + ":" + second + ":" + error.getMessage());
		}
	}

	@ReactiveRetryable(maxAttempts = 2)
	static class ClassLevelService {
		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger recoverCalls = new AtomicInteger();
		volatile boolean failRecover;

		public int calls() {
			return this.calls.get();
		}

		public int recoverCalls() {
			return this.recoverCalls.get();
		}

		public void failRecover() {
			this.failRecover = true;
		}

		public Mono<String> load() {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalStateException("class level"));
		}

		@ReactiveRecover
		public Mono<String> fallback(IllegalStateException error) {
			return Mono.defer(() -> {
				this.recoverCalls.incrementAndGet();
				if (this.failRecover)
					return Mono.error(new IllegalArgumentException("recover failed"));
				return Mono.just(error.getMessage() + " recovered");
			});
		}
	}

	static class SelfService {
		final AtomicInteger calls = new AtomicInteger();
		final AtomicInteger otherCalls = new AtomicInteger();

		public int calls() {
			return this.calls.get();
		}

		public int otherCalls() {
			return this.otherCalls.get();
		}

		@ReactiveRetryable(maxAttempts = 2)
		@ReactiveRecover
		public Mono<String> both(Character id) {
			return Mono.error(new IllegalStateException("both call " + this.calls.incrementAndGet()));
		}

		// "both" fits it by signature.
		@ReactiveRetryable(maxAttempts = 2)
		public Mono<String> other(Character id) {
			this.otherCalls.incrementAndGet();
			return Mono.error(new IllegalStateException("other"));
		}

		@ReactiveRetryable(recover = "selfNamed")
		@ReactiveRecover
		public Mono<String> selfNamed() {
			return Mono.just("never called");
		}
	}

	static class InvalidService {
		final AtomicInteger calls = new AtomicInteger();

		public int calls() {
			return this.calls.get();
		}

		@ReactiveRetryable(recover = "doesNotExist")
		public Mono<String> missing() {
			this.calls.incrementAndGet();
			return Mono.error(new IllegalStateException("missing"));
		}

		@ReactiveRetryable(recover = "integerFallback")
		public Mono<String> wrongReturnType() {
			return Mono.error(new IllegalStateException("wrong return type"));
		}

		@ReactiveRecover
		public Mono<Integer> integerFallback() {
			return Mono.just(1);
		}

		@ReactiveRetryable(interceptor = "customInterceptor", recover = "stringFallback")
		public Mono<String> withInterceptor() {
			return Mono.error(new IllegalStateException("with interceptor"));
		}

		@ReactiveRecover
		public Mono<String> stringFallback() {
			return Mono.just("fallback");
		}
	}

	interface Greeter {
		Mono<String> greet(String name);

		Mono<String> greetByName(String name);
	}

	static class GreeterImpl implements Greeter {
		@Override
		@ReactiveRetryable(maxAttempts = 1)
		public Mono<String> greet(String name) {
			return Mono.error(new IllegalStateException("greet"));
		}

		@Override
		@ReactiveRetryable(maxAttempts = 1, recover = "namedFallback")
		public Mono<String> greetByName(String name) {
			return Mono.error(new IllegalStateException("greet"));
		}

		// Not in the interface: the library calls it on the target object.
		@ReactiveRecover
		public Mono<String> fallback(RuntimeException error, String name) {
			return Mono.just("hello " + name + " from fallback");
		}

		@ReactiveRecover
		public Mono<String> namedFallback(String name) {
			return Mono.just("hi " + name + " by name");
		}
	}
}
