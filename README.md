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

| This library                | Spring Framework 7                           | Notes                                                                                                   |
|-----------------------------|----------------------------------------------|---------------------------------------------------------------------------------------------------------|
| `@EnableReactiveRetry`      | `@EnableResilientMethods`                    | Spring also enables `@ConcurrencyLimit`.                                                                |
| `proxyTargetClass`          | `proxyTargetClass`                           | Same meaning.                                                                                           |
| `order`                     | `order`                                      | The Spring default is `Ordered.LOWEST_PRECEDENCE - 1`.                                                  |
| `include`                   | `includes` (or `value`)                      | Both examine the nested causes.                                                                         |
| `exclude`                   | `excludes`                                   | Both examine the nested causes.                                                                         |
| `maxAttempts`               | `maxRetries`                                 | Both count the retries after the first call. The default is 3 for both.                                 |
| (no delay)                  | `delay`                                      | The Spring default is 1000 ms. Set `delay = 0` for no delay.                                            |
| `backOffFixDelay`           | `delay`                                      | The Spring default `multiplier` is 1.0, which gives a fixed delay.                                      |
| `exponentialBackoff = true` | `delay = 100, multiplier = 2.0, jitter = 50` | These values are the defaults of this library for exponential backoff.                                  |
| `backOffMinDelay`           | `delay`                                      | The first delay.                                                                                        |
| `backOffMaxDelay`           | `maxDelay`                                   |                                                                                                         |
| `backOffFactor`             | `jitter`                                     | This library uses a factor (0 to 1). Spring uses a duration. Set `jitter` to `backOffFactor` × `delay`. |
| `shouldCheckMaxInRow`       | No equivalent                                |                                                                                                         |
| `interceptor`               | No equivalent                                | Spring has `predicate` (a `MethodRetryPredicate`) for a custom retry decision.                          |
| `@ReactiveRecover`          | No equivalent                                | Put `@Retryable` on the methods, not on the class.                                                      |
| No equivalent               | `timeout`                                    | A time limit for all the attempts together.                                                             |
| No equivalent               | `*String` attributes                         | Values from placeholders, for example `delayString = "${retry.delay}"`.                                 |

## Contributing

ProjectReactor Retry AOP is released under the non-restrictive MIT license.
If you have any questions or comments, please open an issue or open a pull request.

***If you can improve this project, do not hesitate to contribute with me. I'm waiting for your merge requests with open
arms.***

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for the full, versioned history of changes.
