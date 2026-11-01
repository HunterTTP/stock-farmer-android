# Fishing overlay

Reference: iOS `d01e7bb`, `FishingOverlay.swift`, `FishingScene.swift` and `GameViewModel+Fishing.swift`.
`FishingOverlay(session, cast, onFinish)` draws above the farm and HUD in a full-screen root `Box`.
It owns pointer input, the frame loop, circular opening/closing, reeling haptics and catch controls.
`FishingSession` is pure Kotlin and delegates swimming, difficulty, reach and meter changes to core `FishingContest`.
Core behavior and the existing HUD, activity, session, farm, menus and screens are unchanged.

## Starting from a tap

The rod must target the tile the fish is **currently swimming on**, not its pond anchor. In the rod tool's
tap/drag handler, with no cast already open, capture the hook and its details inside the same session lock:

```kotlin
val (events, cast) = session.act { engine ->
    val events = engine.castRod(tappedTile)
    events to FishingCast.start(engine, events)
}
session.publish(events)
if (cast != null) fishingCast = cast
```

Imports: `com.hunter.stockfarmer.core.rules.castRod`, plus this package's `FishingCast` and `FishingOverlay`.
`castRod` returns `GameEvent.FishHooked(point)` when that tile has a fish, otherwise
`GameEvent.Rejected(Rejection.FishNotBiting)`. It spends nothing and leaves the fish in its pond.
`FishingCast.start` resolves `fishBiting`, `hookedPond`, weight, sale value, water style and the rod upgrade,
captures the first-time hint, and calls `markFishingHintSeen`. A refusal or stale hook returns null.
Call it only once per hook; the owner must guard against starting a second overlay.

The selected-tool route is `engine.useTool(tappedTile, now)` with `state.mode == PlayMode.harvest` and
`state.harvestTool == HarvestTool.rod`; that delegates to `castRod`. Pass its events to `FishingCast.start`
in the same way. **The current `GameSession.tap` alone does not start fishing**: it calls `engine.tap`,
whose harvest-mode branch returns an empty list. Intercept the rod tap or use the tool route above.

```kotlin
fishingCast?.let { cast ->
    FishingOverlay(session, cast) { events ->
        session.publish(events)
        // Forward to the gameplay owner's usual feedback, quest and save/commit handling.
        // An empty batch means the player gave up; that pond mutation still needs saving.
        fishingCast = null
    }
}
```

Keep the cast until `onFinish`: it runs once after the 550 ms closing reveal. The overlay mutates the real
engine through `session.act`; final events are returned to the owner, not published twice. Storage refusals
appear inside the overlay and keep it open, so `onFinish` is not called for a refusal. Starting the cast
also changes the saved hint flag; include that change in the owner's normal persistence flow.

## Keeping the hooked fish alive

While the cast is open, pass `setOf(cast.pond)` to `engine.updateFish(now, onTheLine = ..., using = random)`
so its natural lifetime cannot expire mid-contest. The current `GameTick` does not pass this argument;
that integration is deliberately left to the owner. Other fish and the farm clock continue normally.
Keep any shared rain/snow layer above the fishing water, as on iOS; this slice draws water shimmer,
night glow and the reference's night/rain/snow tints. It does not replace shared precipitation or audio owners.
Completion events use the existing gameplay sound handling; the iOS quest-complete sound at meter fill
needs the owner's audio integration. Android reeling/success haptics are supplied locally.

## Contest behavior

- Screen points are Android dp, with pointer pixels converted into the same coordinate system. Bounds reserve
  the meter, tool circle and system insets. Resizing an existing composition scales the arena without resetting
  the contest. Keep the same cast object while it is displayed.
- Frames use `withFrameNanos`, cap each step at 50 ms, and pause below the RESUMED lifecycle state. Pausing
  releases the rod. A resumed frame starts with zero elapsed time, with no background-time catch-up.
- Holding the finger over the fish fills the meter; moving away or releasing drains it at core's rate.
  **There is no line snap, timer failure or failure at zero in this reference.** An empty meter can refill.
- At full progress, the fish stops and appears in color, upright and bobbing. Sell calls
  `landFish(cast.pond, cast.point, now, selling = true)`; Store uses `selling = false`.
  Full storage preserves the catch for Sell or another Store attempt. The rod's auto-sell setting is ignored.
- Give up/Back during play calls `loseFish(cast.pond, now)` and schedules the normal respawn delay.
  Back after catching attempts Store and remains open if storage is full. An empty completion batch can show
  the reference's toast, `The ${cast.kind.name} swam away`, in the owner's feedback UI.

## Verification

JUnit 5 tests under `app/src/test/kotlin/com/hunter/stockfarmer/ui/fishing` use fixed times and a local seeded
`RandomSource`. They cover hook resolution, hint persistence, upgraded reach, tracking/drain, unlimited retry,
pause, stalled frames, disposition, refusal/retry, give-up, deterministic swimming, layout, orientation, shimmer
and every species' bundled PNG. Run `:app:testDebugUnitTest` alongside the required Spotless/core/APK checks.
No device is available; touch feel, haptics, visuals, lifecycle transitions and final host integration need
on-device verification.
