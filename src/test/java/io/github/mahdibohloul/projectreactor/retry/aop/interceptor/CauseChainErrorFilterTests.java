package io.github.mahdibohloul.projectreactor.retry.aop.interceptor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Edge cases for matching include and exclude against the cause chain.
 */
@SuppressWarnings("unchecked")
class CauseChainErrorFilterTests {

	private static Class<? extends Throwable>[] types(Class<?>... types) {
		return (Class<? extends Throwable>[]) types;
	}

	private static ReactiveRetryInterceptorBuilder<?> builder(Class<?>[] include, Class<?>[] exclude) {
		return ReactiveRetryInterceptorBuilder.maxAttempts().setInclude(types(include)).setExclude(types(exclude));
	}

	/** A cause chain that is 3 levels deep. */
	private static Throwable deep(Throwable root) {
		return new RuntimeException("top", new IllegalStateException("middle",
				new UncheckedIOException("low", root instanceof IOException io ? io : new IOException("io", root))));
	}

	@Test
	void includeMatchesOnlyADeepCause() {
		Throwable error = new RuntimeException("1",
				new IllegalStateException("2", new IllegalArgumentException("3", new IOException("4"))));
		Assertions.assertTrue(builder(new Class[]{IOException.class}, new Class[]{}).errorFilter(error));
		Assertions.assertFalse(builder(new Class[]{ArithmeticException.class}, new Class[]{}).errorFilter(error));
	}

	@Test
	void deepExcludedCauseWinsOverIncludedTopError() {
		Throwable error = new IllegalStateException("1",
				new RuntimeException("2", new RuntimeException("3", new IllegalArgumentException("4"))));
		Assertions.assertFalse(
				builder(new Class[]{IllegalStateException.class}, new Class[]{IllegalArgumentException.class})
						.errorFilter(error));
	}

	@Test
	void emptyIncludeWithExcludedCause() {
		Throwable excluded = new RuntimeException("top", new IllegalArgumentException("cause"));
		Throwable other = new RuntimeException("top", new IllegalStateException("cause"));
		ReactiveRetryInterceptorBuilder<?> builder = builder(new Class[]{},
				new Class[]{IllegalArgumentException.class});
		Assertions.assertFalse(builder.errorFilter(excluded));
		Assertions.assertTrue(builder.errorFilter(other));
		Assertions.assertTrue(builder.errorFilter(new RuntimeException("no cause")));
	}

	@Test
	void subclassOfIncludedTypeAsCause() {
		Throwable error = new RuntimeException("top", new FileNotFoundException("missing"));
		Assertions.assertTrue(builder(new Class[]{IOException.class}, new Class[]{}).errorFilter(error));
		// The reverse is not a match: the cause is a superclass of the included type.
		Throwable superCause = new RuntimeException("top", new IOException("io"));
		Assertions
				.assertFalse(builder(new Class[]{FileNotFoundException.class}, new Class[]{}).errorFilter(superCause));
	}

	@Test
	void selfReferentialCauseTerminates() {
		Throwable self = new RuntimeException("self") {
			@Override
			public synchronized Throwable getCause() {
				return this;
			}
		};
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			Assertions.assertFalse(builder(new Class[]{IOException.class}, new Class[]{}).errorFilter(self));
			Assertions.assertTrue(builder(new Class[]{}, new Class[]{IOException.class}).errorFilter(self));
			Assertions.assertTrue(builder(new Class[]{RuntimeException.class}, new Class[]{}).errorFilter(self));
		});
	}

	@Test
	void loopingCauseChainWithMatchInsideTheLoopTerminates() {
		RuntimeException first = new RuntimeException("first");
		IllegalStateException second = new IllegalStateException("second", first);
		first.initCause(second);
		Throwable top = new RuntimeException("top", first);
		Assertions.assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			Assertions.assertTrue(builder(new Class[]{IllegalStateException.class}, new Class[]{}).errorFilter(top));
			Assertions.assertFalse(builder(new Class[]{IOException.class}, new Class[]{}).errorFilter(top));
			Assertions.assertFalse(
					builder(new Class[]{IllegalStateException.class}, new Class[]{IllegalStateException.class})
							.errorFilter(top));
		});
	}

	@Test
	void propagatedCheckedException() {
		RuntimeException wrapped = Exceptions.propagate(new IOException("io"));
		Assertions.assertNotSame(IOException.class, wrapped.getClass());
		Assertions.assertTrue(builder(new Class[]{IOException.class}, new Class[]{}).errorFilter(wrapped));
		Assertions.assertFalse(builder(new Class[]{}, new Class[]{IOException.class}).errorFilter(wrapped));
	}

	@Test
	void nonDefaultBuildersApplyTheCauseFilter() {
		Supplier<Throwable> included = () -> deep(new IOException("io"));
		Supplier<Throwable> excluded = () -> new IllegalStateException("top", new IllegalArgumentException("cause"));
		Class<? extends Throwable>[] include = types(IOException.class, IllegalStateException.class);
		Class<? extends Throwable>[] exclude = types(IllegalArgumentException.class);

		MethodInterceptor[] interceptors = {
				ReactiveRetryInterceptorBuilder.backOff().setMinDelay(1).setMaxDelay(2).setInclude(include)
						.setExclude(exclude).setMaxAttempts(2).build(),
				ReactiveRetryInterceptorBuilder.backOff().setMinDelay(1).setMaxDelay(2).setShouldCheckMaxInRow(true)
						.setInclude(include).setExclude(exclude).setMaxAttempts(2).build(),
				ReactiveRetryInterceptorBuilder.maxInRow().setInclude(include).setExclude(exclude).setMaxAttempts(2)
						.build(),
				((ReactiveRetryInterceptorBuilder.FixedDelayRetryInterceptorBuilder) ReactiveRetryInterceptorBuilder
						.fixedDelay().setInclude(include).setExclude(exclude).setMaxAttempts(2)).setFixedDelay(1)
						.build()};
		for (MethodInterceptor interceptor : interceptors) {
			Failing includedTarget = proxy(new Failing(included), interceptor);
			StepVerifier.create(includedTarget.call()).expectError(RuntimeException.class)
					.verify(Duration.ofSeconds(5));
			Assertions.assertEquals(3, includedTarget.calls().get(), interceptor.getClass().getSimpleName());

			Failing excludedTarget = proxy(new Failing(excluded), interceptor);
			StepVerifier.create(excludedTarget.call()).expectError(IllegalStateException.class)
					.verify(Duration.ofSeconds(5));
			Assertions.assertEquals(1, excludedTarget.calls().get(), interceptor.getClass().getSimpleName());
		}
	}

	@Test
	void exhaustedRetryOfAPropagatedCheckedExceptionSignalsTheCheckedException() {
		Failing target = proxy(new Failing(() -> Exceptions.propagate(new IOException("io"))),
				builder(new Class[]{IOException.class}, new Class[]{}).setMaxAttempts(1).build());
		// Reactor unwraps its own ReactiveException when the retry companion errors.
		StepVerifier.create(target.call()).expectError(IOException.class).verify();
		Assertions.assertEquals(2, target.calls().get());
	}

	private static Failing proxy(Failing target, MethodInterceptor interceptor) {
		ProxyFactory factory = new ProxyFactory(target);
		factory.setProxyTargetClass(true);
		factory.addAdvice(interceptor);
		return (Failing) factory.getProxy();
	}

	public static class Failing {
		private final AtomicInteger calls = new AtomicInteger();
		private final Supplier<Throwable> error;

		public Failing(Supplier<Throwable> error) {
			this.error = error;
		}

		public Mono<String> call() {
			return Mono.defer(() -> {
				this.calls.incrementAndGet();
				return Mono.error(this.error.get());
			});
		}

		public AtomicInteger calls() {
			return this.calls;
		}
	}
}
