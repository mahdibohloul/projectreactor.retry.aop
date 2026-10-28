# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Recovery with `@ReactiveRecover`. When the retries of a `@ReactiveRetryable` method fail, or the error is not retried, the library calls a recover method of the same bean and returns its result to the caller. The new `recover` attribute of `@ReactiveRetryable` names the recover method. If `recover` is empty, the library finds the recover method by its signature. `suspend` recover functions get the coroutine context of the caller.
- `ReactiveRecoverer` and `ReactiveRetryInterceptorBuilder.setRecoverer`, to add recovery to an interceptor that you build.
- Kotlin `suspend` functions annotated with `@ReactiveRetryable` are now retried, including `suspend` functions that return a `Flow`. This needs `kotlinx-coroutines-reactor` and `kotlin-reflect` on the classpath.
- JSpecify null-safety annotations (`@NullMarked`) on the public API, so Kotlin sees non-null types.
- `kotlin-interop-tests`, a test-only project that uses the library from Kotlin 2.4 and records how Spring Framework 7's own `@Retryable` behaves on reactive methods.
- A comparison with Spring Framework 7's `@Retryable` in the README.
### Changed
- **Breaking:** the baseline is now Spring Framework 7.0 (Spring Boot 4) and Project Reactor 3.8. Use 2.0.0 with Spring Framework 6.2 (Spring Boot 3).
- **Breaking:** `include` and `exclude` now also match the nested causes of an error, not only the error itself. An error that Reactor wraps (for example with `Exceptions.propagate`) is now retried when its cause is included. An excluded cause prevents the retry, even if the error itself is included.
- `suspend` functions in a class with a class-level `@ReactiveRetryable` are now retried. Before, they were called one time only.
- Built with Gradle 9.8, Spotless 8.10, and the Maven Publish plugin 0.37.
### Removed
- The `repo.spring.io/release` plugin repository, which now needs authentication.

## [2.0.0]
### Added
- Community health files (CONTRIBUTING, CODE_OF_CONDUCT, SECURITY) and Dependabot configuration.
### Changed
- Migrated the build and toolchain to Java 21, targeting Spring Framework 6.2 and Project Reactor 3.7.
- Restructured the build as a proper `java-library`: dependencies split into `api`/`implementation`, versions managed through the Spring Boot BOM, and a completed Maven Central POM.
- Improved error handling and logging in the reactive retry interceptor.
### Fixed
- `exponentialBackoff` and `shouldCheckMaxInRow` now compose correctly.

## [2.0.0-RC2]
### Changed
- Migrated the build and toolchain to Java 21.
- Improved error handling and logging in the reactive retry interceptor.

## [2.0.0-RC1]
### Changed
- Started the 2.0.0 release line targeting Spring Framework 6.2 and Project Reactor 3.7.

## [1.4.0]
### Added
- Logging before and after a retry attempt. *The throwable message is available at `DEBUG` level.*
### Fixed
- Method join-point processing so that co-located annotations (for example `@Transactional`) are still applied during retries.

## [1.3.0]
### Fixed
- Exception filter for the retry backoff strategy.

## [1.2.1]
### Fixed
- Missing sources artifact in Maven publications.

## [1.2.0]
### Added
- `order` attribute on the `@EnableReactiveRetry` annotation to set interceptor ordering.

## [1.1.0]
### Changed
- Source compatibility lowered from Java 17 to Java 11.

## [1.0.0]
### Added
- Initial public release: declarative reactive retry via `@ReactiveRetryable` and `@EnableReactiveRetry`.

[Unreleased]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/v2.0.0...HEAD
[2.0.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/v2.0.0-RC2...v2.0.0
[2.0.0-RC2]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/v2.0.0-RC1...v2.0.0-RC2
[2.0.0-RC1]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.4.0...v2.0.0-RC1
[1.4.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.3.0...1.4.0
[1.3.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.2.1...1.3.0
[1.2.1]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.2.0...1.2.1
[1.2.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.1.0...1.2.0
[1.1.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/mahdibohloul/projectreactor.retry.aop/releases/tag/1.0.0
