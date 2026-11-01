# Remaining full screens

Reference: iOS `d01e7bb`, `HUD/PerformanceScreen.swift`, `ChangelogScreen.swift`, `PrivacyScreen.swift`,
`LessonGuide.swift` (tool-circle guidance), `LaunchView.swift`, and `Performance/PerformanceMonitor.swift`.
All new Kotlin code is under `app/src/main/kotlin/com/hunter/stockfarmer/ui/screens/`. Core is unchanged.
Save slots belong to the separate slice and are deliberately excluded; this slice does not define `SaveSlotsScreen`.

## Information screens

Present `ChangelogScreen(onClose)` and `PrivacyScreen(onClose)` from Settings/About in the same overlay used by the
existing screens. Both supply their own theme, title, scrolling, Close button, and Android back handling. Neither
needs a session or an audio owner. They read the existing `information/Changelog.md` and `information/Privacy.md`
assets on the IO dispatcher and show the iOS fallback text on failure. Privacy text can be selected.

Changelog opens only the latest day, filters individual lines with case-insensitive search, and uses seven recorded
days per page. Searching opens all matching days without replacing the remembered folds. Choose Days uses Android's
date-range calendar: Done with one endpoint selects that day; two endpoints include every day between them.
Show All Days clears the date filter. The calendar is bounded by the bundled changelog dates.

```kotlin
when (openScreen) {
    "changelog" -> ChangelogScreen(onClose = { openScreen = null })
    "privacy" -> PrivacyScreen(onClose = { openScreen = null })
}
```

## Performance

Retain **one** `PerformanceMonitor` with the farm owner across screen openings and configuration changes. This
object has no Android dependencies; access from UI and renderer threads is synchronized. Reports use existing
core `PerformanceFrames` and `PerformanceDurations`, keep bounded histograms and eight longest hitches, and
retain only the latest completed report until the next recording finishes or the owner is discarded.

Keep `PerformanceSampling(monitor, nodeCount = { ... })` beside the farm in the parent composition, outside the
conditional Performance screen. Supply the renderer's actual farm-object count through a thread-safe snapshot.
This helper samples once a second while visible or recording, reads Android PSS/thermal information off the UI
thread, and excludes non-resumed lifecycle time. Android 26–28 report thermal status as Unknown.

At the start of each actual farm update call `monitor.frame()`. Wrap measured work with
`monitor.measure(PerformanceWork.scene) { ... }`, and use the appropriate `PerformanceWork` for each subsystem.
For scopes that cannot use a lambda, pair `val start = monitor.begin()` with
`monitor.end(PerformanceWork.crops, start)` in `finally`. Measure elapsed work on its actual thread; do not wrap
coroutines that suspend. Containers include child measurements and must not be added to subsystem totals.

```kotlin
PerformanceSampling(monitor, nodeCount = { rendererNodeCount })
if (openScreen == "performance") {
    PerformanceScreen(
        monitor = monitor,
        onClose = { openScreen = null },
        recordingContext = {
            session.read { state ->
                "$deviceAndAppDescription\nStarting farm: slot ${activeSlot + 1}, " +
                    "${state.plots.size} plots, ${state.placedBuildings.size} buildings, " +
                    "${state.trees.size} trees; season ${state.season}; shadows $dynamicShadowsEnabled"
            }
        },
    )
}
```

`rendererNodeCount`, device/app metadata, active slot and graphics settings above come from their existing owners.
Record starts a fresh capture and invokes `onClose` to return to play. Stop stays on the screen and shows the report.
`onShare: ((String) -> Unit)?` can override sharing; by default it opens Android's text share chooser.
The screen marks itself visible on entry and invisible on disposal. Recording continues when it closes.
Without renderer instrumentation the screen correctly shows waiting/no-frame/no-system explanations.
Sampling and instrumentation are integration hooks, not changes to the existing farm/game loop in this slice.

For a non-Compose owner the equivalent is `monitor.setActive(...)` on foreground transitions and
`monitor.sample(PerformanceResources(memoryMB, nodeCount, thermal))` once per active second. Times supplied through
an injected test clock or `begin`/`end` are monotonic seconds; only the report's start date uses an `Instant`.

## Lessons

Build an immutable `LessonGuideState` inside `session.read { LessonGuideState.from(it) }` whenever gameplay state
refreshes. Draw `LessonGuide(state, modifier)` immediately above the existing tool circle. This is the iOS
guidance bubble, not a modal page. It disappears for the storage selling steps and after teaching finishes.

Use its fields to highlight the existing controls:

- `pulses`: apply `Modifier.lessonPulse(state.pulses && !isDragging)` to the tool circle.
- `toolTarget`: highlight the Plant or Harvest entry in the tool ring, represented by `PlayMode.plant` or `.harvest`.
- `needsScythe`: highlight the Harvest dropdown and its Scythe choice. The circle does not pulse in this case.
- `storageTarget`: highlight Storage in the tool ring.
- `isTeaching`: suppress unrelated unlock dots while teaching, matching iOS.

Apply `Modifier.lessonPulse(...)` to ring/dropdown targets too. It provides the reference's 1.6-second swell
and golden shadow; when Android animations are disabled it keeps a static glow without scaling. The layer stays
in place when the pulse stops so teaching changes do not cancel a tool drag. `reduceMotion` can also be passed
explicitly to this modifier and LaunchScreen; their defaults respect Android's animator setting.

`LessonGuideState` only projects the saved lesson progress, current mode and selected harvest tool; it never changes
game state. Continue using core's existing lesson rules for actual progress: planting/harvesting/Scythe selection
already learn their steps. The game's periodic owner must call `engine.learnWheatIsRipe(now)` through
`session.act`. Storage's owner calls `engine.learn(...)` for opening Storage, selecting Wheat, picking Max,
selling, and finally closing Storage in order (`openStorage`, `sellItem`, `pickMax`, `sell`, `closeStorage`).
When the final close succeeds, the owner can show the iOS toast `Have fun building your farm!` for four seconds.
The guide never advances lessons merely because it is composed or read.

## Launch

`LaunchScreen(stage, saveError, onRetry, onChooseFarm, onComplete, reduceMotion)` owns the existing core
`LaunchSequence` and advances it with the Compose frame clock only while the activity is resumed. Display it in
the root `Box` where MainActivity currently shows its plain progress indicator. For the complete reveal, keep it
as an overlay above the newly created farm until `onComplete` clears the overlay. Its dark background masks the
farm, fades the icon and then opens a circular reveal. `reduceMotion = true` uses core's shorter fade instead.
The reference `AppIcon.png` is copied unchanged to `assets/images/AppIcon.png` and drawn with nearest filtering.

Pass stages from **actual** startup work, not timers:

1. `loadingFarm` while `persistenceState.isLoading`.
2. `buildingScene` and `loadingTextures` at those renderer preparation boundaries, when exposed by the owner.
3. `waitingForFrame` after preparation but before the first real farm frame.
4. `ready` only after that frame is drawn; `onComplete` then removes the overlay after the reveal.
5. `failed` when loading is blocked, passing `persistenceState.error`, `onRetry = model::retry`, and an
   `onChooseFarm` callback that opens the separate save-slots screen **above** the splash.

If the current integration only exposes loading/success/failure, use loadingFarm/ready/failed initially; the
renderer stages can be supplied when their readiness callbacks exist. The composable never marks itself ready.
Keep existing explicit backup-restore controls available for a blocked farm. Retrying from failed resets the
sequence; key a new LaunchScreen instance when starting a different farm.

The stateless overload `LaunchScreen(sequence: LaunchSequence, ...)` is available for a host that already owns
startup timing. It neither advances the sequence nor invokes completion. Both overloads consume splash touches
so the farm underneath cannot be played before launch completes; failure buttons remain interactive. Block or
hide underlying farm accessibility and back actions in the root host while the overlay is present.

## Verification

Run Spotless, core tests, debug assembly, and `:app:testDebugUnitTest`. Pure JUnit tests cover changelog filtering,
folds, page boundaries, date ranges, lesson targets/messages, launch presentation, and performance recording,
foreground timing, frame-clock resets, peaks, hitch bounds, report output and exception-safe measurement.
Core's existing LaunchSequence/PerformanceMetrics tests cover the reused calculations.

No emulator or phone was used. Screen layout, calendar gestures, actual share targets, renderer instrumentation,
platform resource readings and launch layering remain device/integration verification work.

Verified in this worktree: `spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest`
with JDK 25, `--offline --no-daemon`: BUILD SUCCESSFUL; 1,019 core tests and 210 app tests, zero failures,
errors or skipped tests. Fourteen app tests were added by this slice.

The sandbox run used the existing writable temporary Gradle cache and Android user directory, the in-process
Kotlin compiler, and a temporary init script pointing at a copied debug key under `.gradle/`. As with the existing
screen test setup, that init script sets JUnit TempDir cleanup to NEVER to avoid Windows sandbox ancestor lookup
failures. No checks or assertions were excluded. These environment files and signing material are not source files.

## File inventory

New files under `app/src/main/kotlin/com/hunter/stockfarmer/ui/screens/`:

- `InformationScreens.kt`, `ChangelogState.kt`
- `PerformanceScreen.kt`, `PerformanceMonitor.kt`, `PerformanceReport.kt`, `PerformanceSampling.kt`
- `LessonGuide.kt`, `LessonGuideState.kt`
- `LaunchScreen.kt`, `LaunchPresentation.kt`

Existing screen helpers updated: `ScreenLayout.kt` (session-free layout, page scroll reset, themed content color),
`ScreenIcon.kt` (optional size modifier). Existing callers retain their defaults.

New tests under `app/src/test/kotlin/com/hunter/stockfarmer/ui/screens/`:
`RemainingScreenStateTest.kt`, `PerformanceMonitorTest.kt`.

New asset: `app/src/main/assets/images/AppIcon.png` (SHA-256 matches the iOS source).
Documentation: this file, `docs/PARITY.md`, and `ui/screens/README.md`.
No core, HUD, MainActivity, GameViewModel or GameSession sources were changed; no git commands were used.
