# Stock Farmer (Android)

A farming game with a live stock market. Kotlin, Jetpack Compose and libGDX. See [AGENTS.md](AGENTS.md) for the workflow and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for setup.

## Layout

- `core`: pure Kotlin rules, catalog and persistence, tested on the JVM.
- `app`: the Android app.
- `docs`: development setup, [conventions](docs/CONVENTIONS.md) and the [feature checklist](docs/FEATURES.md).

## Quick start

```powershell
./gradlew spotlessCheck :core:test
./gradlew :app:assembleDebug
```
