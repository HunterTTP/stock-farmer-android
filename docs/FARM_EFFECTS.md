# Farm effect integration

`effectLayers(textures)` constructs one `EffectLayers` on the GL thread. It owns presentation state only;
`GameState` and all core behavior remain unchanged. Keep that instance for the scene's lifetime. Its generated
textures belong to `TextureLibrary` and are released with that library. No additional disposal call is needed.

The integration files were deliberately left unchanged in this slice. The effects are dormant until these hooks
are added. `sources` alone draws butterflies; wind also needs its ground-phase update.

## Renderer hook

Add an optional callback after the existing `sources` constructor parameter in `FarmRenderer`:

```kotlin
private val groundEffects: ((SpriteBatch, GameState, Instant) -> Unit)? = null,
```

Call it in `FarmRenderer.draw`, immediately after `drawGround(batch, state, season)` and before `standing.clear()`:

```kotlin
groundEffects?.invoke(batch, state, now)
```

This position puts animated water, the midnight wave, shadows and firefly pixels above ground and below every
standing object. Wind's ground overlay advances its shared gust/leaf model before its source collects wisps.
The sources use the existing standing sort: wind wisps at their original row plus `0.0009`, butterflies in front
of all standing objects. Firefly halos are a separate overlay above the tint, as on iOS.

## Scene hooks

Add an `EffectLayers` field to `FarmGame`. After creating `textures`, create the effects and renderer:

```kotlin
effects = effectLayers(textures)
renderer = FarmRenderer(
    textures,
    listOf(TractorLayer(textures)) + animalSources(textures) + effects.sources,
    groundEffects = { batch, state, now ->
        effects.draw(EffectPhase.GROUND, batch, state, now, camera)
    },
)
```

With the batch begun and its projection set as today, use the same `now` and current state for all phases:

```kotlin
session.read { state ->
    effects.draw(EffectPhase.BACKGROUND, batch, state, now, camera)
    renderer.draw(batch, state, now)
    effects.draw(EffectPhase.BEFORE_TINT, batch, state, now, camera)
}
drawNightTint(now) // Existing FarmGame tint; no second night tint.
session.read { state ->
    effects.draw(EffectPhase.AFTER_TINT, batch, state, now, camera)
}
session.preview(now)?.let { renderer.drawPreview(batch, textures, it) }
```

The preview goes last so precipitation, lights and fireworks do not obscure placement feedback.
`BACKGROUND` paints seasonal sky and fades in `images/backgrounds/space.jpg`, visible outside the map.
`BEFORE_TINT` draws fall leaves. `AFTER_TINT` applies rain's multiply tint, precipitation, firefly halos,
door torches and fireworks, in that order. Each overlay/source restores batch color and blend functions.

Call every phase every frame, including dry weather and daylight, so clocks and particle cleanup advance.
Ambient frame deltas are capped at 0.1 seconds, as in the Swift insect update, so resuming does not create a
catch-up burst. Core farm time still determines rain, wind direction, darkness, sun position and midnight waves.
The midnight wave captures the camera center on its first frame each farm day and retains that origin.
Create a new factory instance when replacing the farm, to reset its ambient population and clock history.

## Event hooks and options

At an existing successful first-building celebration event, call:

```kotlin
effects.fireworks.celebrateAround(origin, building.width, building.height, darkness)
```

For a screen-wide celebration, pass world bounds to
`effects.fireworks.celebrateAcross(FireworkArea(left, bottom, width, height), darkness)`.
Call `effects.fireworks.reset()` to cancel pending rockets. Celebrations are explicit events; the layer never
infers them from loaded buildings or alters career records.

`effects.butterflies.catchable(tile)` returns wings and the released UUID for the Bucket interaction.
After a successful catch, call `effects.butterflies.remove(catchable)`; released identities also sync from
`state.releasedButterflies`. These methods do not perform the core inventory transaction themselves.
A `FireflyLayer` in `effects.overlays` has `onSighting` for the existing career-record action, if desired.

`effects.shadows.enabled` and `effects.shadows.intensity` (0..1, default 0.5) expose the reference shadow
controls without inventing persistent settings. Shadow geometry follows core `Sun` and `ShadowProjection`;
solid buildings/fences and sprite-alpha trees, rocks, decorations and fruit trees share cached images, with one
image generated per frame. The CPU raster uses three box-blur passes to approximate UIKit's soft blur.
Caster placement follows the current Android renderer's sprite sizing. Crops and sprinklers cast no shadows.

`WaterDrops` ports the palette, washed spray colors, foam colors and pixel size for future sprinkler/duck callers;
this slice does not add sprinkler gameplay or edit the existing animal wake implementation.
There is no grass wind-sway implementation in the frozen iOS Farm source: grass remains static. Tree bending
would require changing the existing tree renderer and is outside this effects-only integration.

## Verification

JVM tests exercise clocks, rain/snow fill and cleanup, water frames/palettes, insect flights/resting/released
identity, gust cadence and leaf lifetime, midnight coverage/origin, torch placement, fireworks and shadow masks.
No emulator or phone was available: visual match, blend appearance, GPU context recreation and frame-time/memory
behavior on Android need device verification after wiring the hooks.
