# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
