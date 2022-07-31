# ProjectReactor Retry AOP [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) [![Java CI with Gradle](https://github.com/mahdibohloul/projectreactor.retry.aop/actions/workflows/gradle.yml/badge.svg?branch=master)](https://github.com/mahdibohloul/projectreactor.retry.aop/actions/workflows/gradle.yml)

This project uses [projectreactor](https://projectreactor.io/) retry mechanism
and [spring AOP](https://docs.spring.io/spring-framework/docs/2.5.x/reference/aop.html) to retry failed reactive
operations.

## Quick Start

This section provides a quick introduction to getting started with ProjectReactor Retry AOP.

### Add Dependencies

You can add the
following [maven dependencies](https://search.maven.org/artifact/io.github.mahdibohloul/projectreactor-retry-aop/1.0.0/pom)
to your project:

```xml

<dependency>
    <groupId>io.github.mahdibohloul</groupId>
    <artifactId>projectreactor-retry-aop</artifactId>
    <version>1.0.0</version>
    <type>pom</type>
</dependency>
```

```groovy
implementation 'io.github.mahdibohloul:projectreactor-retry-aop:1.0.0'
```

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

## API

ProjectReactor Retry AOP uses project reactor's retry mechanism in underlying reactive operations.
You can see more details about the retry mechanism in
the [projectreactor retry documentation](https://projectreactor.io/docs/core/release/api/reactor/util/retry/Retry.html).

## Contributing

ProjectReactor Retry AOP is released under the non-restrictive MIT license.
If you have any questions or comments, please open an issue or open a pull request.

***If you can improve this project, do not hesitate to contribute with me. I'm waiting for your merge requests with open
arms.***
