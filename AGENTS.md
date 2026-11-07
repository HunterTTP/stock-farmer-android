# Working in this repo

Shared instructions for Claude, Codex and other coding agents. Start with `docs/HANDOFF.md`: it holds the current state and the remaining steps. Follow the user's current request when it overrides a default below.

## What this is

An Android remake of the iOS game at `../stock-farmer-ios` (ported from iOS commit `d01e7bb`; [docs/SYNC.md](docs/SYNC.md) records how far it has caught up since). Gameplay, mechanics, balance, visuals and sound match it; the iOS Swift code and its tests are the spec for what the player sees and experiences. Check whether iOS already has a feature before building it, and port it rather than inventing it. Android never invents gameplay.

How Android does it underneath is its own. Rendering, threading, caching, persistence, audio, input and lifecycle may differ from iOS wherever Android's performance and hardware call for it, as long as the player-visible result is the same. Build those parts the modern Android way: current Jetpack and Kotlin practice (coroutines and Flow, Compose state hoisting, lifecycle-aware components, WorkManager, Material guidance for system bars and edge-to-edge), measured performance work, and no iOS idioms carried over for their own sake. Android saves are separate from iOS saves and need no compatibility.

## Stack

- `core`: pure Kotlin/JVM, no Android imports. Catalog, rules, persistence, sound and drag policies. `GameEngine` owns a mutable `GameState`; preview an action by running it on `engine.copy()`.
- `app`: single-activity Android app. Jetpack Compose for menus and HUD, libGDX for the farm, MediaPlayer for music, SoundPool for effects.
- Gradle Kotlin DSL with a version catalog (`gradle/libs.versions.toml`). Build JDK 25. minSdk 26, targetSdk 36.

## Workflow

1. Read `docs/DEVELOPMENT.md` and the feature in `docs/FEATURES.md`, then the iOS source and tests. Code style and number rules are in `docs/CONVENTIONS.md`.
2. Work in vertical slices: a feature's rules, tests and UI land together.
3. Rules go test-first in `core`. Write tests that mirror the iOS tests' behavior.
4. Run `./gradlew spotlessApply` to format, then `./gradlew spotlessCheck :core:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`. Resolve failures before committing. Never claim an unrun check passed.
5. Every change ships with its tests. All tests must pass and the app must build before a commit; never commit a red build.
6. Commit in meaningful pieces with clear messages, each ending with the `Port:` trailers (see Syncing with the iOS repo below), then `git push` to `main` as each piece of work completes. Never commit signing material or secrets.
7. Installing the debug build on a connected phone or tablet is allowed (`adb devices`, then `./gradlew installDebug` with `ANDROID_SERIAL`; skip a device that is not connected). Never test on a device or act on its screen unless the user asks in that request: no launching the app, taps or other input, screenshots, screen recording, instrumented tests or logcat. This is a hard rule for every agent.
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

## Syncing with the iOS repo

Gameplay changes flow both ways between this repo and `../stock-farmer-ios`. [docs/SYNC.md](docs/SYNC.md) holds the watermark, the how-to and the table for commits made before these rules. This section is the same in both repos' `AGENTS.md`, apart from the repo names; change both together.

- **Trailers.** Every commit message ends, just before `Co-Authored-By`, with:

  ```
  Port: rules | ui | assets | none
  Port-Size: S | M | L
  Port-Note: <one line: what to port, which tests to port first>
  ```

  Leave out `Port-Size` when `Port: none`; `Port-Note` is optional. `none` covers platform-only work. iOS: xtool, Xcode Cloud, versioning and release scripts, SpriteKit/iOS performance tuning, old-save migrations (Android skips those on purpose). Android: Gradle, libGDX, Play Store, ADB/device setup, Android performance work. Both: documentation-only commits.
- **Ported work.** A commit that ports a change from the other repo adds one `Ported-From: ios@<sha>` or `Ported-From: android@<sha>` line per source commit and uses `Port: none`, so the change never bounces back. Once ported work is merged, move the watermark in `docs/SYNC.md` forward to the newest source commit that is now fully ported.
- **Finding work.** `python scripts/port-backlog.py --from ../stock-farmer-ios` lists the other repo's commits since the watermark that are not `Port: none` and not yet credited by a `Ported-From` here, with size, suggested agent and note; untagged commits come from the legacy tables or show as `unclassified`. `scripts/port-backlog.py` is identical in both repos: change both copies together and run `python scripts/port-backlog.py --self-test`.
- **Porting contract.** Port a rule change by porting the matching Core tests first; the port is done when the Kotlin tests pass. If both repos changed the same thing differently (the tests disagree), stop and ask the owner rather than overwrite either side.

Port size picks the agent. This table is the one place porting model choices live:

| Size | Typical change | Agent |
| --- | --- | --- |
| S | Text, one number, a one-line UI fix | Sonnet, low effort |
| M | One feature on one screen, a balance pass with tests | Sonnet high or Opus medium |
| L | New system, new persisted state, cross-cutting rework | Opus high, porting the Core tests first |

Codex hand-offs use `codex exec -m gpt-6.1-sol` for implementation; `gpt-6-astra` is for planning only.

## Releases

Pushes to `main` do not build or ship anything. Any tag pushed to GitHub makes `.github/workflows/release.yml` build the app and upload it to the Google Play internal testers (see `docs/RELEASE.md`). Release tags are named `YYYY.M.D-ga-N`: the date without leading zeros and a counter for the day, for example `2026.11.6-ga-1`.

- Only tag when the owner explicitly asks to ship or release to testers; never create, push, move or delete any other tag, since every pushed tag ships. When asked, tag exactly as `docs/RELEASE.md` describes, from a clean `main` that has been pushed.

## Models and tools

Agents run on the Claude Max plan only. Opus 5.5 at high reasoning is for planning and very complex tasks; Sonnet 5.5 at medium does the rest. Porting work from iOS picks its agent from the size table under Syncing with the iOS repo. Start a fresh session per slice and keep context small.

Run each slice in its own git worktree and branch. Slice agents in worktrees must not push; the main session pushes after review. Review and merge each branch only after `spotlessCheck`, `:core:test`, `:app:testDebugUnitTest`, `:app:assembleDebug` and `:app:lintDebug` pass.
