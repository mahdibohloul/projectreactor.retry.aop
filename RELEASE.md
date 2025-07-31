# Release Process

## Version Naming Convention

We follow [Semantic Versioning](https://semver.org/) with additional qualifiers for pre-releases:

- `MAJOR.MINOR.PATCH` (e.g., `1.4.0`) - Regular releases
- `MAJOR.MINOR.PATCH-M#` (e.g., `1.4.0-M1`) - Milestones
- `MAJOR.MINOR.PATCH-RC#` (e.g., `1.4.0-RC1`) - Release Candidates

## Branch Strategy

- `master` - Main development branch
- `#.x` - Maintenance branches for each major version (e.g., `1.x`, `2.x`)
- `release/v#.#.#` - Temporary release branches

## Release Types

### Milestone Release
```bash
# Create milestone branch
git checkout -b release/v1.4.0-M1

# Update version in build.gradle
# version = '1.4.0-M1'

# Commit and tag
git add build.gradle
git commit -m "chore: prepare milestone release v1.4.0-M1"
git tag -a v1.4.0-M1 -m "Release milestone 1 for version 1.4.0"
git push origin v1.4.0-M1
```

### Release Candidate
```bash
# Create RC branch
git checkout -b release/v1.4.0-RC1

# Update version in build.gradle
# version = '1.4.0-RC1'

# Commit and tag
git add build.gradle
git commit -m "chore: prepare release candidate v1.4.0-RC1"
git tag -a v1.4.0-RC1 -m "Release candidate 1 for version 1.4.0"
git push origin v1.4.0-RC1
```

### General Availability (GA) Release
```bash
# Create release branch
git checkout -b release/v1.4.0

# Update version in build.gradle
# version = '1.4.0'

# Commit and tag
git add build.gradle
git commit -m "chore: prepare release v1.4.0"
git tag -a v1.4.0 -m "Release version 1.4.0"
git push origin v1.4.0
```

## Release Checklist

1. **Pre-release**
   - [ ] Update dependencies to latest stable versions
   - [ ] Run full test suite
   - [ ] Check code style with `./gradlew spotlessCheck`
   - [ ] Update documentation
   - [ ] Update CHANGELOG.md

2. **Release**
   - [ ] Create release branch
   - [ ] Update version in build.gradle
   - [ ] Commit changes
   - [ ] Create tag
   - [ ] Push tag
   - [ ] Monitor GitHub Actions workflow
   - [ ] Verify Maven Central publication

3. **Post-release**
   - [ ] Update master to next development version
   - [ ] Announce release on GitHub
   - [ ] Update documentation site (if applicable)

## Maintenance

### Backporting Fixes
```bash
# Checkout maintenance branch
git checkout 1.x

# Cherry-pick fix from master
git cherry-pick <commit-hash>

# Create patch release
git tag -a v1.4.1 -m "Release version 1.4.1"
git push origin v1.4.1
```

## Troubleshooting

### Maven Central Publication
- Check Sonatype OSSRH dashboard for staging repositories
- Verify GPG signing in published artifacts
- Monitor GitHub Actions logs for errors

### Common Issues
1. Version mismatch between tag and build.gradle
2. GPG signing failures
3. Maven Central staging repository problems

## Support Policy

- Latest GA release: Full support
- Previous minor version: Security updates
- Older versions: No support
