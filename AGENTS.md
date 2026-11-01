# Working in this repo

Shared instructions for Claude, Codex and other coding agents. Follow the user's current request when it overrides a default below.

## What this is

An exact Android remake of the iOS game at `../stock-farmer-ios` (frozen reference commit `d01e7bb`). Gameplay, mechanics, balance and graphics match it. The iOS Swift code and its tests are the spec; the Android code never invents behavior. Android saves are separate from iOS saves and need no compatibility.

## Stack

- `core`: pure Kotlin/JVM, no Android imports. Catalog, rules, persistence, sound and drag policies. `GameEngine` is an immutable value; preview an action by running it on a copy.
- `app`: single-activity Android app. Jetpack Compose for menus and HUD, libGDX for the farm, Media3 for music.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Build JDK 25. minSdk 26, targetSdk 36.

## Workflow

1. Read `docs/DEVELOPMENT.md` and the relevant row of `docs/PARITY.md`, then the iOS source and tests for the feature.
2. Work in vertical slices: a feature's rules, tests and UI land together.
3. Rules go test-first in `core`. Port the iOS tests with the rules; match behavior, not file names.
4. Run `./gradlew :core:test`, then `./gradlew :app:assembleDebug` for app changes. Resolve failures before committing. Never claim an unrun check passed.
5. Commit in meaningful pieces with clear messages, and `git push` as each piece of work completes. Never commit signing material or secrets.
6. Keep `docs/PARITY.md` current.

## Code

- Clean Code, SOLID, DRY, KISS, YAGNI. Match the style of the file you are in.
- Names carry the meaning. Comment only to say why, briefly.
- Minimum change per request; no drive-by refactors. Delete replaced code and tests.
- Rules live in `core`, never in composables or the renderer. UI presents state and forwards intent.
- Numbers: Swift `Int` is 64-bit, so use `Long` where values can exceed 32 bits (prices reach 100 billion cents). Keep `Double` arithmetic and rounding identical to the Swift.
- Nested state must be immutable (no shared mutable collections) so copies are independent.
- Use fixed times and a seeded generator in tests. Never rely on wall-clock or system randomness in rules tests.
- Add a dependency only when the current slice needs it.

## Models and tools

Opus plans and decides architecture; Sonnet ports and builds. Codex is for independent read-only review of a finished slice and for overflow work. Start a fresh session per slice and keep context small.
