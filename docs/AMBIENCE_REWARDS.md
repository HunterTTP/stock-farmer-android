# Reward flights, ambience, animal voices and readiness

The implementations are wired into MainActivity (RewardFlights host, ambience driver tick), Hud (reward targets, delayed money/XP), FarmGame (fireworks, butterfly net).
Core behavior is unchanged. The reference is iOS `d01e7bb`: `RewardFlights.swift`, `RewardFlightTracker.swift`,
`GameViewModel+AnimalVoices.swift`, `FeedbackPresenter.tendAmbience` and `GameViewModel.chimeWhenEverythingIsReady`.

## Reward overlay

In MainActivity's ready-farm branch, wrap the HUD and overlay with the shared scope, above the full-screen FarmHost:

```kotlin
FarmHost()
RewardFlightsHost(model.session) {
    Hud(model.session, sound, Modifier.fillMaxSize())
    RewardFlights(model.session, Modifier.fillMaxSize())
}
```

Imports come from `com.hunter.stockfarmer.ui.rewards`. Keep the existing save-error dialog after this scope.
The farm and overlay must share the same full-screen Compose root, matching FarmView's viewport. The overlay accounts
for its own root-relative origin and density. `game/FarmProjection.kt` reverses `FarmView.tileAt` using tile centers.

In Hud's `StatusBar`, attach `Modifier.rewardTarget(HUDTarget.Money)` to the money Text, and
`Modifier.rewardTarget(HUDTarget.Level)` to the level Text (or a containing level panel). In `ScreenButtons`, attach
`Modifier.rewardTarget(HUDTarget.Storage)` to the Storage TextButton. The modifier measures each destination and
pulses it on arrival. It needs to be inside RewardFlightsHost. No destination coordinates are hardcoded.

To also delay the HUD's numbers until labels land, retain raw `state.xp` as `xp: Int` in `HudState`, setting it in
`GameSession.hud()`. In the composable `StatusBar`, calculate the displayed values on each composition:

```kotlin
val flights = LocalRewardFlights.current
val money = flights?.displayedMoney(hud.money) ?: hud.money
val xp = flights?.displayedXP(hud.xp) ?: hud.xp
val level = LevelRules.levelForXP(xp)
val (earned, span) = LevelRules.progress(xp)
val progress = earned.toFloat() / span.coerceAtLeast(1)
```

Use `money.dollars`, `level` and `progress` in the existing Text/progress controls. Leave gameplay, menu locks and
level-up events using actual engine state. If a different farm is loaded into the same GameSession, call
`LocalRewardFlights.current?.clear()` from a composable-owned load effect, or recreate RewardFlightsHost with a
`key(farmID)` around it. Disposing the overlay clears pending flights automatically.

RewardFlights collects `session.events` itself. Do not forward batches a second time. It groups sold/stored rewards
by item and destination, counts tractor quantities only when their event has a farm tile, delays quest XP by 0.8 seconds,
and sends harvests through per-item tally rows. Arrivals extend the tally hold by 0.9 seconds; departures release held
money/XP and pulse the destination. Above 12 incoming flights, rewards add straight to the tally without losing counts.
With system animations disabled, moving labels are hidden while bookkeeping and the tally still work.

## Audio driver

In MainActivity's `listenForSound`, after `val session = model.session`, create one driver:

```kotlin
val ambience = session.ambienceDriver(sound) { message ->
    Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
}
```

Import `com.hunter.stockfarmer.audio.ambienceDriver` and `android.widget.Toast`. In the existing one-second music loop,
after `sound.updateMusic(season, minute, chimed)`, add:

```kotlin
if (model.persistenceState.isReady) ambience.tick(now)
```

Use the existing `now: Instant`. Continue the existing lifecycle observer on SoundCenter: `sound.isActive` gates the
snapshot before reading the farm clock, so paused/background ticks do no work. Call `ambience.clear()` after replacing
a loaded farm in the same driver. The driver creates no coroutine, timer, player or lifecycle observer of its own.
Keep the existing music/night chime wiring; no new-day game rules run in this driver.

The driver supplies Weather.eased(rain) and winter state to SoundCenter.updateRain/AmbientPlayer. Snow is silent.
Birds use the existing core RandomMomentTrigger once per 80 seconds, only below 0.05 darkness and rain. A wet/dark
window is consumed, so a missed bird is not replayed when the weather clears. There is no extra night loop in the iOS
reference: nighttime suppresses birds, while rain continues.

AnimalChatter schedules one voice per kind per 180-second window, with 15 seconds between kinds, sorted by cue name.
Only animals outside shelter, on screen and close up qualify; placements supply pan and distance. Grown roosters crow,
chicks cluck, pets and livestock use their existing cues. Playback goes through SoundCenter and its existing VoiceLimiter;
channel volumes, failed playback and suspend behavior remain centralized there.

ReadyChime tracks both the whole planted field and newly completed crop groups using existing core growth queries.
It plays allCropsReady and calls onReady with the iOS message. The first partial-field observation is silent; an already
fully grown whole field announces once. Further announcements require a readiness transition. RandomMomentTrigger
belongs to the bird schedule, not crop readiness.

## Verification

Passed on 2026-11-01: `spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest`.
Core: 1,019 tests; app: 214 tests, including 18 new tests in this slice. Zero failures or skipped tests.
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

The Windows sandbox required a writable temporary Gradle cache and ANDROID_USER_HOME, in-process Kotlin compilation
(`-Pkotlin.compiler.execution.strategy=in-process`), and a temporary Gradle init script. That script points debug
signing at a temporary copy of the existing development key, avoiding AGP's inaccessible default-keystore lock.
It sets `java.io.tmpdir` inside each module's build directory and
`junit.jupiter.tempdir.cleanup.mode.default=NEVER`: JUnit's cleanup `toRealPath` is denied by the sandbox even there.
All test assertions ran; temporary test directories remain for inspection. No signing material, environment workaround
or build configuration was added to the repository.

JVM tests cover tracker accounting, rapid-harvest counts, timing, reset, tractor/quest events, projection and flight
positions; bird/weather scheduling, animal selection/placement and shelter, rooster maturity, and readiness transitions.
No device or emulator is available. Visual layout, animation feel, accessibility behavior and audible playback remain
unverified. Runtime integration remains a follow-up in the four protected files.

## Files in this slice

Paths below are relative to `app/src/main/kotlin/com/hunter/stockfarmer` unless marked otherwise.

| Area | Files |
|---|---|
| Rewards | `ui/rewards/RewardFlight.kt`, `RewardFlightTracker.kt`, `RewardMotion.kt`, `RewardFlights.kt` |
| Projection | `game/FarmProjection.kt` |
| Audio | `audio/AmbienceDriver.kt`, `AnimalVoices.kt`, `ReadyChime.kt`, `SessionAmbience.kt`, `SoundCenter.kt` (active-state getter only) |
| JVM tests | `app/src/test/kotlin/com/hunter/stockfarmer/ui/rewards/RewardFlightTrackerTest.kt`, `RewardMotionTest.kt`; `app/src/test/kotlin/com/hunter/stockfarmer/audio/AmbienceDriverTest.kt`, `ReadyChimeTest.kt`, `SessionAmbienceTest.kt` |
| Documentation | `docs/AMBIENCE_REWARDS.md`, `docs/PARITY.md` |

No core files or dependencies changed. MainActivity, Hud, GameViewModel and GameSession remain untouched.
