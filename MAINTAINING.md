# Maintainer's Guide

This document is for project maintainers and details the processes and tools used for maintaining the project.

## Development Environment Setup

1. **Required Tools**
   - JDK 11
   - Gradle
   - Git
   - GPG for signing releases

2. **IDE Configuration**
   - Install Spotless plugin
   - Configure auto-formatting
   - Set up import optimization

## Code Review Guidelines

1. **Pull Request Review Checklist**
   - [ ] Code follows style guidelines
   - [ ] Tests are included and passing
   - [ ] Documentation is updated
   - [ ] Commit messages follow conventions
   - [ ] Changes are appropriate for target branch

2. **Merge Strategy**
   - Use squash and merge for feature branches
   - Use merge commits for release branches
   - Always preserve history in maintenance branches

## Version Management

1. **Branch Structure**
   - `master` - Next version development
   - `#.x` - Maintenance branches
   - `release/*` - Release preparation

2. **Version Numbering**
   - Major: Breaking changes
   - Minor: New features (backwards compatible)
   - Patch: Bug fixes
   - Pre-release: `-M#` or `-RC#`

## Release Process

See [RELEASE.md](RELEASE.md) for detailed release procedures.

## Security

1. **Vulnerability Handling**
   - Security issues get priority
   - Create private security advisory
   - Backport fixes to supported versions
   - Coordinate disclosure

2. **Key Management**
   - Rotate GPG keys annually
   - Store keys securely
   - Document key handover process

## Documentation

1. **Required Updates**
   - README.md
   - CHANGELOG.md
   - API documentation
   - Wiki (if applicable)

2. **Release Notes**
   - Feature descriptions
   - Breaking changes
   - Upgrade instructions
   - Contributors list

## Support

1. **Issue Triage**
   - Bug: Needs reproduction
   - Enhancement: Needs specification
   - Question: Needs clarification

2. **Response Times**
   - Security issues: 24 hours
   - Bugs: 48 hours
   - Features: 1 week
   - Questions: 1 week
