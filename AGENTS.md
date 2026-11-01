# Working in this repo

Shared instructions for Claude, Codex and other coding agents. Start with `docs/HANDOFF.md`: it holds the current state and the remaining steps. Follow the user's current request when it overrides a default below.

## What this is

An Android remake of the iOS game at `../stock-farmer-ios` (frozen reference commit `d01e7bb`). Gameplay, mechanics, balance, visuals and sound match it; the iOS Swift code and its tests are the spec for what the player sees and experiences. Check whether iOS already has a feature before building it, and port it rather than inventing it. Android never invents gameplay.

How Android does it underneath is its own. Rendering, threading, caching, persistence, audio, input and lifecycle may differ from iOS wherever Android's performance and hardware call for it, as long as the player-visible result is the same. Build those parts the modern Android way: current Jetpack and Kotlin practice (coroutines and Flow, Compose state hoisting, lifecycle-aware components, WorkManager, Material guidance for system bars and edge-to-edge), measured performance work, and no iOS idioms carried over for their own sake. Android saves are separate from iOS saves and need no compatibility.

## Stack

- `core`: pure Kotlin/JVM, no Android imports. Catalog, rules, persistence, sound and drag policies. `GameEngine` is an immutable value; preview an action by running it on a copy.
- `app`: single-activity Android app. Jetpack Compose for menus and HUD, libGDX for the farm, MediaPlayer for music, SoundPool for effects.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Build JDK 25. minSdk 26, targetSdk 36.

## Workflow

1. Read `docs/DEVELOPMENT.md` and the feature in `docs/FEATURES.md`, then the iOS source and tests. Code style and number rules are in `docs/CONVENTIONS.md`.
2. Work in vertical slices: a feature's rules, tests and UI land together.
3. Rules go test-first in `core`. Write tests that mirror the iOS tests' behavior.
4. Run `./gradlew spotlessApply` to format, then `./gradlew spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Resolve failures before committing. Never claim an unrun check passed.
5. Every change ships with its tests. All tests must pass and the app must build before a commit; never commit a red build.
6. Commit in meaningful pieces with clear messages, then `git push` to `main` as each piece of work completes. Never commit signing material or secrets.
7. Never test on a device (no adb, installs, logcat or running the app) unless the user asks in that request. This is a hard rule for every agent, and a standing instruction to deploy does not lift it. When asked to deploy, install on each connected phone and tablet (`adb devices`, then `./gradlew installDebug` with `ANDROID_SERIAL`) and skip a device that is not connected.
8. Keep `docs/FEATURES.md` current.

## Code

- Clean Code, SOLID, DRY, KISS, YAGNI. Match the style of the file you are in.
- Names carry the meaning. Comment only to say why, briefly.
- Minimum change per request; no drive-by refactors. Delete replaced code and tests.
- Rules live in `core`, never in composables or the renderer. UI presents state and forwards intent. Old-save migrations from the iOS app are not ported: a new app has no old saves.
- Numbers: Swift `Int` is 64-bit, so use `Long` where values can exceed 32 bits (prices reach 100 billion cents). Keep `Double` arithmetic and rounding in game rules identical to the Swift, so balance matches.
- Performance work (culling, caching, batching, background threads, texture atlases) is welcome in `app`, but it must not change what the player sees or the rules. Measure before and after, and cover the logic with tests.
- `GameState` is a mutable object owned by one `GameEngine`; `GameState.copy()` is a deep copy, so a preview runs on `engine.copy()` and never touches the real farm. Value types inside it (plots, animals, buildings) are immutable data classes. Maps that feed derived indexes (buildings, trees, decorations) change only through `GameState`'s put and remove methods.
- Use fixed times and a seeded generator in tests. Never rely on wall-clock or system randomness in rules tests.
- Add a dependency only when the current slice needs it.

## Models and tools

Agents run on the Claude Max plan only. Opus 5.5 at high reasoning is for planning and very complex tasks; Sonnet 5.5 at medium does the rest. Start a fresh session per slice and keep context small.

Run each slice in its own git worktree and branch. Slice agents in worktrees must not push; the main session pushes after review. Review and merge each branch only after `spotlessCheck`, `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:lintDebug` pass.
