# Timelapse integration

The app implementation is in `ui/timelapse`. It uses the existing core schedule, playback state and PNG archive store;
core behavior is unchanged. `Hud.kt`, `MainActivity`, `GameViewModel` and `GameSession` are deliberately unchanged.

## Recorder and screen hooks

At the Compose root that owns the current `GameSession`, create one recorder and provide it around the HUD and screens:

```kotlin
val recorder = rememberTimelapseRecorder(session)
val owner = LocalLifecycleOwner.current
val ready = persistenceState.isReady && persistenceState.error == null

LaunchedEffect(recorder, owner, ready) {
    recorder.reset()
    if (!ready) return@LaunchedEffect
    owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
        recorder.reset()
        try {
            while (isActive) {
                recorder.tick(Instant.now())
                delay(1_000)
            }
        } finally {
            recorder.reset()
        }
    }
}

CompositionLocalProvider(LocalTimelapseRecorder provides recorder) {
    // Existing farm and HUD content.
    if (showTimelapse && ready) {
        TimelapseScreen(session, onClose = { showTimelapse = false })
    }
}
```

Use `viewModel.persistenceState` for `persistenceState`. Add a HUD/menu action that sets the host's
`showTimelapse` state to true. The exact screen entry point is
`@Composable fun TimelapseScreen(session: GameSession, onClose: () -> Unit)`.
Always provide the same recorder that is being ticked; the viewer tells that recorder to suspend capture and resets
the schedule when it closes. The screen's fallback recorder permits standalone history browsing.

`rememberTimelapseRecorder` creates `TimelapseRecorder(session, TimelapseStore(filesDir.toPath().resolve("Timelapse")))`.
Non-Compose owners can construct that class directly and call `suspend fun tick(now: Instant, active: Boolean = true)`
from a lifecycle coroutine, resetting in `finally`. Inactive ticks also reset the schedule. Stop/reset capture before
loading, restoring or switching farms. Replace the recorder when the `GameSession` changes. Keep a single capture loop.

The existing persistence timer saves setting changes and capture markers through `GameSession.act`; refresh also
recovers the current day's marker from the archive if the app stopped between writing the image and saving the game.
`recorder.state` exposes archive counts, storage errors and clear progress for other UI, if needed.

## Behavior

- Core `TimelapseSchedule` decides when the active farm clock crosses the configured minute. Loading, resuming,
  enabling capture, changing the minute and leaving playback rebaseline without a catch-up screenshot.
- Each capture requests a 2048-pixel whole-farm PNG with a deep state snapshot and its timestamp. `PhotoRequests`
  still supports the existing manual `request(pixels, done)` call. `FarmGame` queues both requests on the same graphics
  thread and routes them through `FarmPhoto`; they cannot render concurrently.
- Store operations run on `Dispatchers.IO`. A mutex prevents overlapping capture/clear/refresh operations. Capture
  invalidation, state identity checks and the store's archive generation protect against stale work. Cancellation
  propagates without displaying a storage error. A late graphics callback cannot resume a cancelled capture.
- History remains separate from saves under `filesDir/Timelapse/<history UUID>`. Clearing asks for confirmation and
  preserves recording settings and the last-captured-day marker, as on iOS. Errors offer a retry; a failed capture
  does not mark the day as captured.
- The full-screen viewer autoplays archives with at least two frames at one frame per second, pauses in the
  background, stops at the final frame and restarts from the beginning when Play is pressed at the end. Scrubbing
  resumes only a previously playing timeline, except when scrubbed to the final frame.
- Current and adjacent decoded images are cached, with at most three retained. A missing neighbor does not obscure
  the current image. A missing/corrupt current image pauses playback and allows seeking to another frame.
- Automatic transitions crossfade for 200 ms when enabled; scrubs change immediately. System-disabled animations
  suppress crossfades and use the short opacity reveal instead of the circular reveal.

## Verification limits

Passed with JDK 25, offline: `spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest`.
The reports contain 1,019 core tests and 218 app tests, all passing, including 22 new timelapse tests. The core test task
was also forced to execute rather than using its cached result.

Sandbox-only build setup used a writable Gradle cache, the existing debug keystore copied into `.gradle`, in-process
Kotlin compilation, and an external init script setting JUnit's temporary-directory cleanup mode to `NEVER`.
The sandbox blocks JUnit's directory real-path lookup during cleanup; all test bodies and assertions still execute.
No repository build configuration or dependencies were changed for these accommodations.

JVM tests cover capture scheduling across eight hours, midnight, settings changes, lifecycle resets, cancellation,
overlap, stale farms, clearing pending work, storage failures, marker recovery, playback timing/scrubbing and bounded
image caching.
No emulator or device was used. Actual framebuffer capture, bitmap decoding, memory pressure, Compose layout,
accessibility interaction and lifecycle behavior on Android still require device verification. Host wiring is done in MainActivity and Hud.
