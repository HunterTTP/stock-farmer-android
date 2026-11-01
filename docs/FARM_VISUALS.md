# Farm visuals

The farm renderer owns only presentation. `AnimalSceneGestures` uses existing core movement queries and actions;
`GameTick` yields animals to a pending nudge, and the scene advances that driver under `GameSession.act`.
No core files or rules were changed. `Hud.kt` and `GameSession.kt` were left untouched.

## Reference audit

| iOS file | Result |
|---|---|
| `CobblestonePath.swift` | Ported: staggered 48 px stones, mortar, highlights and spring grass luminosity grain. The shared terrain loader also restores the generated dirt path; neither path has a bundled JPG. |
| `GridLinesNode.swift` | Ported: 0.12 gray, 0.9 opacity, one screen point, fade from 60% of maximum zoom to zero at maximum zoom. Drawn below fertilizer and standing objects. |
| `TilePreviewNode.swift` | Ported: exact allowed/blocked colors, 0.35 fill, 0.9 stroke, three screen point outlines, optional 0.35 second hold and 0.35 second fade. Placement preview is wired; `FarmGame.flashPreview` exposes transient feedback. |
| `GrowthReveal.swift` | Ported: explicit origins, 1.4 second object fade, 0.6/4/2.4 second glow rise/hold/fall, 0.5 strength, 1.7 spread and depth behind the object. `FarmGame.revealGrowth` is the entry point. Wild-growth scheduling is outside this visual slice; the current game tick has no wave-growth caller. |
| `ProduceBadge.swift` | Ported: eggs, fertilizer, wool, milk, favorite crops and tractor cargo with count/shadow, exact world sizes and 6 unit/0.6 second bob. Android supplies emoji glyphs and a heavy system font; Avenir Next and Apple emoji are not bundled. |
| `FertilizerNode.swift` | Ported: four 35 unit sprites at quarter-tile centers, below crops. |
| `AnimalNode.swift` | Ported missing presentation: independent petting hearts, pending-step nudge, doorway entry/exit fades, inside hit exclusion, initial random pause, sun/daylight shadows, hunger/produce badges and look replacement on growth. Existing walk interpolation, cat mirroring, footprint centering and duck masking/wakes retained. |
| `CatAnimation.swift` | Already covered: standing, stand-to-sit, wagging, sitting, optional sleep and reversed sit-to-stand, with reference frame counts and timings. |
| `DogSprites.swift` | Already covered: directional idle/walk/sniff and breed sheet sizes. Bark/run/doghouse sleep rows are unused by the frozen Farm implementation; no additional reaction was invented. Petting keeps the current body animation, as on iOS. |
| `ChickenSprites.swift` | Ported difference: lying duration now derives from a uniform 4...9 second draw divided by the 1.2 second cycle, rather than a uniform loop count. Existing chick/standing/walk frames retained; chicks never lie down. The named hop row is unused on iOS. |
| `LivestockSprites.swift` | Already covered: directional standing/walk/graze, sheared rows, babies, and duck waterline/tail visibility with no duck grazing. |
| `SeasonSky.swift` | Ported difference: exact floating-point colors instead of 8 bit approximations. Farm-photo sky now uses the same season colors. Existing space backdrop fill and darkness fade retained. |
| `WeatherSceneView.swift` | Ported differences: rain tolerance of 0.002, one second wind checks, solid-center snowflake texture and density-aware screen points for precipitation sizes/speeds. Existing intensity easing, prefill, seasonal switch and cleanup retained. Android precipitation remains owned by FarmGame; a persistent weather view over fishing transitions still needs later UI integration. |

All standing offsets use the same row units, including wind and tractor, so growth glows sort correctly across row boundaries.
There is no standalone tap/bark/emote body animation in the frozen `AnimalNode`: taps open the options sheet;
petting emits hearts and nudging walks. The later UI slice should supply voice and haptic feedback at those actions.

## UI hook

Import `com.hunter.stockfarmer.game.interactWithAnimal` and add this argument to the existing `SheetHost` call:

```kotlin
onAnimalInteraction = session::interactWithAnimal,
```

`session.animalGestures.targets` contains the latest interpolated world centers and doorway visibility.
`animalAtTouch` and `reactionAtTouch` are pure functions for scene gestures; nudge reaction delays preserve the
remainder of the current half-second step. These functions never open a menu or change game state.
`FarmGame.flashPreview(points, allowed)` and `FarmGame.revealGrowth(trees, rocks)` marshal their work to the GL thread.
Call the latter only with origins returned by successful wild growth, not by comparing arbitrary farm snapshots.

## Verification limits

Passed: `spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest` with JDK 25;
1,019 core tests and 217 app tests, zero failures. The debug APK assembles successfully.
The sandbox needs a writable Gradle cache and Android home, the Kotlin compiler in process,
and an explicitly supplied copy of the standard debug keystore. JUnit's default temporary-directory
cleanup is disabled for the verification process because Java `toRealPath` fails in the sandbox during
cleanup; all test bodies and assertions still execute. No source test or signing configuration was weakened.

JVM tests cover hit boundaries, nearest animal selection, inside exclusion, reaction delays, heart lifetimes,
doorway motion/fades, legal nudge movement and blocking, sprite/rest states, weather tolerance, sky colors,
snow opacity, grid fade, preview fade, growth envelopes, stone geometry and badge bobbing.
No emulator or device is available: GPU blending, visual equivalence, fonts/emoji, photo output, frame time and
weather continuity across fishing transitions remain unverified. Platform glyphs and procedural rasterization
can differ from UIKit even where timing, geometry and colors match.

## Files in this slice

Paths below are relative to `app/src/main/kotlin/com/hunter/stockfarmer/` unless stated otherwise.

- `farm/`: `CobblestonePath.kt`, `FarmGame.kt`, `FarmPhoto.kt`, `FarmRenderer.kt`, `GridLines.kt`,
  `GrowthReveal.kt`, `ProduceBadge.kt`, `SceneText.kt`, `TerrainTextures.kt`, `TilePreview.kt`, `TractorLayer.kt`,
  `VisualTiming.kt`.
- `farm/animals/`: `AnimalGestures.kt`, `AnimalMotion.kt`, `AnimalShelter.kt`, `AnimalSnapshot.kt`,
  `AnimalSources.kt`, `AnimalSprites.kt`.
- `farm/effects/`: `EffectLayers.kt`, `RainLayer.kt`, `RainMotion.kt`, `WindLayers.kt`.
- `game/`: `AnimalSceneGestures.kt`, `GameTick.kt`.
- App JVM tests: `farm/VisualTimingTest.kt`, `farm/animals/AnimalGesturesTest.kt`,
  `farm/animals/AnimalSnapshotTest.kt`, `farm/animals/AnimalSpritesTest.kt`, `farm/effects/WeatherMotionTest.kt`,
  `game/AnimalSceneGesturesTest.kt`.
- Documentation: `docs/FARM_VISUALS.md`, `docs/PARITY.md`.
