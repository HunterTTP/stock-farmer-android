# Stock Farmer (Android)

A farming game with a live stock market. Kotlin, Jetpack Compose and libGDX. See [AGENTS.md](AGENTS.md) for the workflow and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for setup.

## Layout

- `core`: pure Kotlin rules, catalog and persistence, tested on the JVM.
- `app`: the Android app. Packages from the bottom up: `game` (session, touch routing, view state), `audio`, `haptics`, `market`, `notifications`, `settings` and `diagnostics` (none may import `ui` or `farm`; `PackageLayeringTest` checks), then `farm` (libGDX scene, input, camera, effects) and `ui` (Compose HUD, menus, screens, sheets, `ui/weather`), then `MainActivity` with `GameRoot`, `LaunchOverlay` and `GameFeedbackDriver`.
- `docs`: development setup, [conventions](docs/CONVENTIONS.md) and the [feature checklist](docs/FEATURES.md).

## Quick start

```powershell
./gradlew spotlessCheck :core:test
./gradlew :app:assembleDebug
```
