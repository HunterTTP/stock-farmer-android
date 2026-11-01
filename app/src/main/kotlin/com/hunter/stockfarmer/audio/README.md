# App audio

Create one `SoundCenter(applicationContext)` per activity on the main thread and add it with
`lifecycle.addObserver(soundCenter)`. It resumes on `onResume`, stops effects and pauses streams on `onPause`, and
releases players, loaders, callbacks and audio focus on `onDestroy`. It can also be driven explicitly with
`resume()`, `pause()` and `close()`. Do not retain a closed center in a view model across activity recreation.

After applying an action, forward its `List<GameEvent>` to `react(events, landscapeTool, petSpecies, placement)`.
Resolve pet species from the resulting farm state. The placement callback accepts a tile and returns core
`SoundPlacement(zoomFraction, horizontalOffset, verticalOffset, viewWidth, viewHeight)`, using screen-space offsets
from the camera center and matching units for view dimensions. Positive horizontal offsets pan right. Core cue
mapping handles deduplication and refusal/fanfare priority. UI actions and periodic animal/bird cues use
`play(SoundCue, placement)` directly. Calls from libGDX must be dispatched to the Android main thread.

On the ambience tick and when the clock/season changes, call
`updateMusic(season, minuteOfDay, chimeForCurrentNight)`. The last argument means the game has started this night's
midnight sequence, matching the iOS `chimeNight == NightPulse.day(...)` check. Use `play(SoundCue.nightPulse)` for
the chime, retry a dropped chime while core `NightPulse.isMusicHushed` holds, and `stop(SoundCue.nightPulse)` when
moving the clock by hand. Hushing fades over two seconds while the track continues. Supply `Weather.eased(rain)`
and whether it is snowing to `updateRain`. Bird/animal scheduling remains with the game and core policies.

Sliders read `settings[channel]` and call `setVolume(channel, level)`; zero mutes. Levels default to one and persist
in the private `sound` SharedPreferences using the iOS channel keys. No old-save migration is needed. Use
`trackName`, `onTrackChange` and `skipMusic()` for soundtrack controls. Music yields to existing media and audio
focus interruptions; rain and effects mix with other apps, as on iOS. Android uses the device's media-volume
controls rather than the iOS silent-switch policy.

Effects use the converted mono WAVs and SoundPool; music and rain use platform MediaPlayer, with no new runtime
dependency. Missing or invalid recordings are skipped and logged. Cues fired before loading or while paused are
dropped. The native adapters require a device to verify audible balance, decoding, routing and interruptions.

Run the pure playback, bundled-asset and lifecycle-policy tests with:

```text
./gradlew --offline -I app/src/test/audio-tests.init.gradle :app:testDebugUnitTest --no-daemon
```

The test-only init script supplies JUnit 5 to the otherwise unconfigured app test source set, keeping this slice
within the audio package and test files. The normal core/build checks still run without this script. The JVM
tests use fake outputs and fixed time; they do not instantiate Android media classes.
