# ProjectReactor Retry AOP [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Java CI with Gradle](https://github.com/mahdibohloul/projectreactor.retry.aop/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/mahdibohloul/projectreactor.retry.aop/actions/workflows/gradle.yml)

This project uses [projectreactor](https://projectreactor.io/) retry mechanism
and [spring AOP](https://docs.spring.io/spring-framework/reference/core/aop.html) to retry failed reactive
operations.

> [!NOTE]
> Spring Framework 7 (Spring Boot 4) has a built-in `@Retryable` annotation that also retries `Mono` and `Flux`.
> For simple retries, it is sufficient. This library gives more control. See
> [Spring Framework 7 `@Retryable` or this library?](#spring-framework-7-retryable-or-this-library)

## Compatibility

| Version | Spring Framework | Spring Boot | Project Reactor | Java | Kotlin       |
|---------|------------------|-------------|-----------------|------|--------------|
| 3.0.x   | 7.0              | 4.x         | 3.8             | 21+  | 2.4 (tested) |
| 2.0.x   | 6.2              | 3.x         | 3.7             | 21+  | —            |

### Kotlin

The public API has [JSpecify](https://jspecify.dev/) null-safety annotations, so Kotlin sees non-null types
instead of platform types.

- Kotlin classes are `final` by default. Spring must make a subclass (proxy) of the bean to retry it.
  Use the [`kotlin-spring`](https://kotlinlang.org/docs/all-open-plugin.html#spring-support) compiler
  plugin, which opens `@Component`, `@Service`, and other Spring-annotated classes, or make the class `open`.
- `suspend` functions are retried (since 3.0). Each retry calls the function again. A `suspend`
  function that returns a `Flow` is also retried. You must have `kotlinx-coroutines-reactor` and
  `kotlin-reflect` on the classpath. Spring needs them to call a `suspend` function through a proxy.
- A retried `Mono` or `Flux` works with coroutines. Use `awaitSingle()` or `asFlow()` from
  `kotlinx-coroutines-reactor`.
- A function that is not `suspend` and returns a `Flow` is **not** retried. Return a `Flux`, or
  make the function `suspend`.

```kotlin
@Service
class GreetingService(private val client: GreetingClient) {
  @ReactiveRetryable(include = [IOException::class], maxAttempts = 2)
  suspend fun greet(name: String): String = client.fetchGreeting(name)
}
```

## Quick Start

This section provides a quick introduction to getting started with ProjectReactor Retry AOP.

### Add Dependencies

You can add the
following [maven dependency](https://central.sonatype.com/artifact/io.github.mahdibohloul/projectreactor-retry-aop)
to your project:

```xml

<dependency>
    <groupId>io.github.mahdibohloul</groupId>
    <artifactId>projectreactor-retry-aop</artifactId>
    <version>3.0.0</version>
</dependency>
```

```groovy
implementation 'io.github.mahdibohloul:projectreactor-retry-aop:3.0.0'
```

> **Requirements:** Java 21+ and Spring Framework 7.0 / Project Reactor 3.8 on the consumer's classpath.
> For Spring Framework 6.2 (Spring Boot 3), use version 2.0.0.

### Declarative Example

The following example shows how to use ProjectReactor Retry AOP in a declarative fashion.

```java

@Configuration
@EnableReactiveRetry
public class Application {

}

@Service
class Service {
    @ReactiveRetryable(include = IllegalStateException.class)
    public Mono<Void> service() {
        return Mono.defer(() -> {
            // do something
        });
    }
}
```

This example calls the `service` method and, if it fails with a `IllegalStateException`, it will retry the call.
There are various options in the `@ReactiveRetryable` annotation attributes for including and excluding exception types,
limiting the number of retries, and specifying a backoff strategy.

## Features

### Recovery

Use `@ReactiveRecover` to give a fallback result when the retries fail. The recover method is
another method of the same bean. The library calls it in these conditions:

- The last retry fails.
- The error is not retried, because `include` or `exclude` stops it.

The caller then gets the result of the recover method, not the error.

```java
@Service
class GreetingService {
    @ReactiveRetryable(include = IOException.class, maxAttempts = 2)
    public Mono<String> greet(String name) {
        return client.fetchGreeting(name);
    }

    @ReactiveRecover
    public Mono<String> greetFallback(IOException error, String name) {
        return Mono.just("Hello, " + name);
    }
}
```

#### How the library finds the recover method

- If you set `recover`, the library uses the `@ReactiveRecover` method with that name, for example
  `@ReactiveRetryable(recover = "greetFallback")`.
- If `recover` is empty, the library examines the `@ReactiveRecover` methods of the bean's class and
  its superclasses. It uses the methods that have a fitting signature.
- If more than one method fits, the library uses the method whose `Throwable` parameter is the
  closest supertype of the error. A method without a `Throwable` parameter is the last choice.

#### Signature rules

- The first parameter is optional. If you add it, it must be a `Throwable` type. The library calls
  the method only if the error is an instance of this type.
- The other parameters must have the same types as the parameters of the retryable method. They
  get the same argument values.
- A `Mono<T>` method recovers a `Mono<T>` method. A `Flux<T>` method recovers a `Flux<T>` method.
- A `suspend` function recovers a `suspend` function. The return types must be compatible.

#### Errors

- The recover method gets the last error, not Reactor's `RetryExhaustedException`.
- If no recover method fits the error, the caller gets the last error.
- If the recover method fails, the caller gets the error of the recover method.
- If `recover` names a method that does not exist or does not fit, the first call fails with an
  `IllegalStateException`.

#### Other rules

- The library calls the recover method on the target object, not through the proxy. Thus, other
  advice (for example `@Transactional`) does not apply to the recover method.
- A recover method in a class with a class-level `@ReactiveRetryable` is not retried.
- Recovery does not apply to a custom interceptor bean. If you set `interceptor` and `recover`
  together, the first call fails with an `IllegalStateException`.

In Kotlin, a `suspend` recover function gets the coroutine context of the caller:

```kotlin
@Service
class GreetingService(private val client: GreetingClient) {
    @ReactiveRetryable(include = [IOException::class], maxAttempts = 2)
    suspend fun greet(name: String): String = client.fetchGreeting(name)

    @ReactiveRecover
    suspend fun greetFallback(error: IOException, name: String): String = "Hello, $name"
}
```

### Custom Interceptor

By default, ProjectReactor Retry AOP uses the `ReactiveRetryInterceptor` to intercept failed reactive operations based
on the `@ReactiveRetryable` annotation attributes. You can use your own interceptor by implementing the
`MethodInterceptor` and pass the name of your interceptor bean to the `ReactiveRetryable` annotation.

**Version 1.2.0**: In this version you can specify the order of the interceptor bean in the `EnableReactiveRetry`
annotation.

## API

ProjectReactor Retry AOP uses project reactor's retry mechanism in underlying reactive operations.
You can see more details about the retry mechanism in
the [projectreactor retry documentation](https://projectreactor.io/docs/core/release/api/reactor/util/retry/Retry.html).

## Spring Framework 7 `@Retryable` or this library?

Spring Framework 7 has the
[
`@Retryable`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/resilience/annotation/Retryable.html)
annotation in `spring-context`, enabled with `@EnableResilientMethods`. It also retries methods that
return `Mono` or `Flux`. For simple retries, use it. You do not need an additional dependency.

Use this library if you need one of these features:

- **The method is called again on each retry.** The interceptors that run inside the retry
  interceptor also run again for each attempt. For example, to start a new transaction for each
  attempt, give `@EnableReactiveRetry(order = ...)` a higher precedence than `@Transactional`.
  An exception that the method throws before it returns the publisher is also retried. Spring calls the method one time
  only, and subscribes again
  to the publisher that the first call returned.
- **Consecutive failures** with `shouldCheckMaxInRow`. This is useful for long-lived streams.
- **A custom interceptor bean** for each method, with the `interceptor` attribute.
- **Recovery.** A `@ReactiveRecover` method gives a fallback result when the retries fail. Spring's
  `@Retryable` has no recovery.
- **Kotlin `suspend` functions.** Spring's `@Retryable` calls a `suspend` function one time only.

### Other differences

- **The error after the last retry.** This library signals the last error. Spring signals
  Reactor's `RetryExhaustedException`, with the last error as its cause.
- **The default delay.** This library does not wait between retries by default. Spring waits 1 second.
- **Other reactive types.** Spring uses the `ReactiveAdapterRegistry`, so it also retries RxJava
  types and Kotlin `Flow` return values. This library retries `Mono`, `Flux`, and `suspend` functions.
- **Events.** Spring publishes a `MethodRetryEvent` for each failure. This library writes log messages.

Both examine the nested causes of an error for `include` and `exclude` (since 3.0 for this library).

The tests in [`kotlin-interop-tests`](kotlin-interop-tests) show these differences.

### Attribute mapping

Use this table if you move from one annotation to the other.

| This library                  | Spring Framework 7                           | Notes                                                                                                                                                         |
|-------------------------------|----------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@EnableReactiveRetry`        | `@EnableResilientMethods`                    | Spring also enables `@ConcurrencyLimit`.                                                                                                                      |
| `proxyTargetClass`            | `proxyTargetClass`                           | Same meaning.                                                                                                                                                 |
| `order`                       | `order`                                      | The Spring default is `Ordered.LOWEST_PRECEDENCE - 1`.                                                                                                        |
| `include`                     | `includes` (or `value`)                      | Both examine the nested causes.                                                                                                                               |
| `exclude`                     | `excludes`                                   | Both examine the nested causes.                                                                                                                               |
| `maxAttempts`                 | `maxRetries`                                 | Both count the retries after the first call. The default is 3 for both.                                                                                       |
| (no delay)                    | `delay`                                      | The Spring default is 1000 ms. Set `delay = 0` for no delay.                                                                                                  |
| `backOffFixDelay`             | `delay`                                      | The Spring default `multiplier` is 1.0, which gives a fixed delay.                                                                                            |
| `exponentialBackoff = true`   | `delay = 100, multiplier = 2.0, jitter = 50` | These values are the defaults of this library for exponential backoff.                                                                                        |
| `backOffMinDelay`             | `delay`                                      | The first delay.                                                                                                                                              |
| `backOffMaxDelay`             | `maxDelay`                                   |                                                                                                                                                               |
| `backOffFactor`               | `jitter`                                     | This library uses a factor (0 to 1). Spring uses a duration. Set `jitter` to `backOffFactor` × `delay`.                                                       |
| `shouldCheckMaxInRow`         | No equivalent                                |                                                                                                                                                               |
| `interceptor`                 | No equivalent                                | Spring has `predicate` (a `MethodRetryPredicate`) for a custom retry decision.                                                                                |
| `@ReactiveRecover`, `recover` | No equivalent                                | Spring has no recovery. Use `onErrorResume` on the returned publisher. A `@ReactiveRecover` method is also not retried by a class-level `@ReactiveRetryable`. |
| No equivalent                 | `timeout`                                    | A time limit for all the attempts together.                                                                                                                   |
| No equivalent                 | `*String` attributes                         | Values from placeholders, for example `delayString = "${retry.delay}"`.                                                                                       |

## Contributing

ProjectReactor Retry AOP is released under the non-restrictive MIT license.
If you have any questions or comments, please open an issue or open a pull request.

***If you can improve this project, do not hesitate to contribute with me. I'm waiting for your merge requests with open
arms.***

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full, versioned history of changes.
