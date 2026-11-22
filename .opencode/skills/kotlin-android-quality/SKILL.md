---
name: Kotlin Android Quality
description: Kotlin and Jetpack Compose code standards - StateFlow and state hoisting, lifecycle and background service handling, Javadoc/KDoc documentation format, SonarQube clean code rules, local Android Lint and detekt static analysis, and test requirements.
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

## Local static analysis (Android Lint / detekt)

- Static analysis also runs fully locally through Gradle: no server and no
  SonarQube connection are required. Use the built-in terminal tool (`bash` /
  `studio_run_shell_command`) from the project root after making changes.
- **Android Lint** ships with the Android Gradle Plugin and is always available;
  it is the preferred Android-aware checker (Compose, resources, manifest,
  API-level and correctness checks). Run `./gradlew lintDebug` for the debug
  variant (fast, part of this project's verification) or `./gradlew lint` for all
  variants. Read the generated report in `app/build/reports/lint-results-debug.txt`
  / `lint-results-debug.html` and fix every error or warning you introduced.
- **detekt** is Kotlin-specific and must be configured first (add the
  `io.gitlab.arturbosch.detekt` Gradle plugin and declare its version/coordinates
  in `gradle/libs.versions.toml`; it is not currently set up in this project).
  Only run `./gradlew detekt` when the plugin is actually present — otherwise
  either add it for Kotlin-only smells or rely on Android Lint. Never claim
  detekt passed without having run it.
- These local checks complement SonarQube: they catch Kotlin/Java code smells,
  bugs and risky constructs before work is reported as complete. Always list the
  commands you ran and their results in the verification summary.

## Tests

- Test stack: JUnit, kotlinx-coroutines-test, Compose UI tests.
- Add or update tests when behavior changes.
