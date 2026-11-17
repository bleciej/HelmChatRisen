---
name: Jetpack Navigation 3
description: State-driven navigation with Jetpack Navigation 3 (androidx.navigation3) in Compose - key-based back stacks, rememberNavBackStack + NavDisplay, adaptive layouts, deep links, returning results.
---

# Jetpack Navigation 3 (androidx.navigation3)

## Documentation

- Overview: https://developer.android.com/guide/navigation/navigation-3
- Releases: https://developer.android.com/jetpack/androidx/releases/navigation3
- Recipes: https://github.com/android/nav3-recipes

## Principles

1. Model each destination as a unique key object and provide a function that
   resolves keys to content ("Resolve keys to content").
2. Keep the back stack as app state (e.g. `rememberNavBackStack`) hoisted so it
   survives configuration changes and process death.
3. Render with `NavDisplay` - it observes the back stack and updates the UI.
4. Use scene strategies (`NavDisplay`) for adaptive layouts: list-detail,
   two-pane, dialogs, bottom sheets.
5. Deep links and returning results between destinations are supported - consult
   the docs before implementing them manually.

## Workflow

1. Check the versions in `gradle/libs.versions.toml` (`navigation3-runtime`,
   `navigation3-ui`) before adding or upgrading the dependency.
2. Fetch the relevant documentation page before using a Navigation 3 API for the
   first time; the library is evolving, so prefer the current docs over memory.
3. Keep navigation changes state-driven: navigate by adding/removing keys, not
   by imperative "start destination" calls.
