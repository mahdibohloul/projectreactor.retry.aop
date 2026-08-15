# Contributing to ProjectReactor Retry AOP

Thanks for your interest in improving this project. Contributions of all kinds are welcome:
bug reports, feature requests, documentation, and code.

## Getting Started

1. Fork the repository and clone your fork.
2. Make sure you have **JDK 21** installed. The Gradle toolchain targets Java 21.
3. Build and run the tests:
   ```bash
   ./gradlew clean check
   ```

## Development Workflow

1. Create a topic branch from `master` (or the relevant `*.x` maintenance branch).
2. Make your change, and add or update tests that cover it.
3. Format the code and verify the style before you push:
   ```bash
   ./gradlew spotlessApply spotlessCheck
   ```
4. Run the full check to confirm everything passes:
   ```bash
   ./gradlew clean check
   ```

## Commit Messages

This project follows the [Conventional Commits](https://www.conventionalcommits.org/) style,
for example:

```
feat(retry): add support for custom backoff strategies
fix(interceptor): preserve co-located annotations during retry
chore(build): bump reactor-core to 3.7.7
```

Keep each commit focused on a single logical change.

## Pull Requests

- Open the pull request against `master` unless the change targets a maintenance branch.
- Describe the problem and the solution, and link any related issues.
- Make sure CI (build, tests, and style check) is green.
- Update `CHANGELOG.md` under the `[Unreleased]` section when your change is user-facing.

## Reporting Bugs and Requesting Features

Open an [issue](https://github.com/mahdibohloul/projectreactor.retry.aop/issues) and include:

- The library version, Java version, and Spring / Reactor versions you use.
- A minimal reproduction, the expected behavior, and the actual behavior.

## License

By contributing, you agree that your contributions are licensed under the
[MIT License](LICENSE.md).
