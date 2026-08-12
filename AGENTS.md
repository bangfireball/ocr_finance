# Repository Guidelines

## Project Structure & Module Organization

This is a single-module Android application built with Kotlin and Jetpack Compose. Application code lives in `app/src/main/java/com/example/ocr_finace/`; `MainActivity.kt` is the current UI entry point, while reusable colors, typography, and themes belong under `ui/theme/`. Android resources are in `app/src/main/res/`, and manifest declarations are in `app/src/main/AndroidManifest.xml`.

Keep local JVM tests in `app/src/test/` and device or emulator tests in `app/src/androidTest/`. Centralize dependency versions and aliases in `gradle/libs.versions.toml`; module-specific Android configuration belongs in `app/build.gradle.kts`.

## Build, Test, and Development Commands

Run commands from the repository root with the checked-in Gradle wrapper:

- `./gradlew assembleDebug` builds a debug APK.
- `./gradlew installDebug` installs the debug build on a connected device or emulator.
- `./gradlew testDebugUnitTest` runs local JVM tests.
- `./gradlew connectedDebugAndroidTest` runs instrumentation and Compose UI tests on a device.
- `./gradlew lintDebug` performs Android static analysis.
- `./gradlew clean` removes generated build output when troubleshooting stale artifacts.

Use Android Studio's Compose Preview for quick UI iteration; do not commit generated `build/` directories or local IDE configuration.

## Coding Style & Naming Conventions

Follow standard Kotlin formatting with four-space indentation and trailing commas in multiline argument lists. Use `PascalCase` for classes and `@Composable` functions, `camelCase` for functions and properties, and `snake_case` for resource names. Keep packages lowercase under `com.example.ocr_finace`. Prefer small, stateless composables, hoist mutable state, and place user-facing text in `res/values/strings.xml` rather than hard-coding it.

## Testing Guidelines

JUnit 4 backs local tests; AndroidX JUnit, Espresso, and Compose UI testing support instrumentation tests. Name test classes after the subject (`ReceiptParserTest`) and test methods after behavior (`parsesTotalWithTax`). Add JVM tests for pure logic and device tests for Android APIs, navigation, and UI semantics. Run both test suites and lint before opening a pull request.

## Commit & Pull Request Guidelines

Git history is not included in this checkout, so use concise, imperative commit subjects such as `Add receipt capture screen`. Keep commits focused. Pull requests should explain the change and verification performed, link relevant issues, and include screenshots or recordings for visible UI changes. Call out manifest, permission, SDK, or dependency changes explicitly.
