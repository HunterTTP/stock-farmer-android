# Stock Farmer (Android)

An Android remake of the Stock Farmer iOS farming game, with identical gameplay, mechanics and graphics. Kotlin, Jetpack Compose and libGDX. See [AGENTS.md](AGENTS.md) for the workflow and [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for setup.

## Layout

- `core`: pure Kotlin rules, catalog and persistence, tested on the JVM.
- `app`: the Android app.
- `docs`: development workflow and the gameplay parity checklist.

## Quick start

```powershell
./gradlew :core:test
./gradlew :app:assembleDebug
```
