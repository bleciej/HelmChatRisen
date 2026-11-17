---
name: Kotlin Android Quality
description: Kotlin and Jetpack Compose code standards - StateFlow and state hoisting, lifecycle and background service handling, Javadoc/KDoc documentation format, SonarQube clean code rules, and test requirements.
---

# Kotlin & Android code quality standards

## Language and state

- Kotlin with Jetpack Compose Material 3 (and Material Adaptive for adaptive layouts).
- Use `StateFlow` and Compose state hoisting; avoid blocking the main thread.
- Handle Android lifecycle, configuration changes, permissions, and
  background/foreground service constraints correctly.

## Documentation (Javadoc/KDoc)

Every class and function you add or change must carry a `/** ... */` block
comment:

- The description is followed by the standard Javadoc block tags.
- `@param` for each parameter (in declaration order), `@return` for any function
  that returns a value, `@throws` for documented exceptions.
- Wrapped tag text is indented by two spaces.
- Inline KDoc references (`[Symbol]`) must be resolvable.
- After documenting, re-run the IDE analysis so no malformed-documentation or
  unresolved-reference warning remains.

## SonarQube

- The IDE has the SonarQube plugin with predefined rules installed. Write clean,
  maintainable code that satisfies SonarQube inspections: no code smells, bugs,
  vulnerabilities or security hotspots; keep complexity low; avoid duplication;
  follow naming and size conventions.
- Run SonarQube analysis/inspections before reporting work as complete and fix
  any reported issues.

## Tests

- Test stack: JUnit, kotlinx-coroutines-test, Compose UI tests.
- Add or update tests when behavior changes.
