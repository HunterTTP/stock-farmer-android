# Handoff: where the project stands and what to do next

Read this first in a fresh session, then `AGENTS.md`, `docs/DEVELOPMENT.md` and `docs/PARITY.md`.
Last updated at `main` = `4e24acf` (2026-11-01).

## The goal

An exact Android remake of the iOS game Stock Farmer (iOS repo: `C:\Users\hunter\workspace\stock-farmer-ios`, reference
commit `d01e7bb`; Swift sources under `StockFarmer/Sources/StockFarmer` and `StockFarmer/Core`). Same art, audio, rules and
the Yahoo stock feature. Kotlin, Jetpack Compose for screens, libGDX for the farm. Eventual Play Store release (not yet
started). iOS saves do not need to work on Android, so old-save migrations are deliberately not ported.

Standing instructions from the user:

- Clean code, SOLID, DRY, KISS, YAGNI, modern best practice, a linter (Spotless + ktlint, 120 columns).
- Commit often and push (`origin` = GitHub `HunterTTP/stock-farmer-android`), one branch per slice, fast-forward merge to
  `main`. Commit as `git -c user.name=HunterTTP -c user.email=hunterttp@gmail.com commit`, message ending with
  `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>` (add a `Co-Authored-By: Codex <noreply@openai.com>` line
  for Codex-written code).
- **Hand work to Codex (`codex exec -m gpt-6.1-sol`, "sol") so slices run in parallel with your own work** (planning tasks
  only: `gpt-6-astra`). The user reset the Codex usage limit on 2026-11-01; if it runs out again the log says
  `You've hit your usage limit ... try again at <time>` and the user can reset it.
- A Stop hook (`/goal`) keeps asking to finish porting the game per this file. Keep going until the app is complete, do
  not stop to ask.
- **Do not install, launch or test on the tablet until the very end of development** (see "The tablet").
- Never claim a check passed that was not run. Use they/them for people whose pronouns are unknown.
- Python is not installed; edit with the Edit/Write tools (the user said installing it is fine but it is not needed).

## Verified state (all green on `main`)

```
./gradlew spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest   -> BUILD SUCCESSFUL (at 4e24acf)
core tests: ~1020 (HapticCueTest added), 0 failures.   app unit tests pass.   debug APK builds.
```

The app has never been run on a device or emulator. Everything visual is unverified.

### Core module (`core/`, pure Kotlin) - complete

The whole rules engine, models, catalogs, persistence, feedback (sound cue logic, `HapticCue`), market parser and
presentation helpers are ported with tests. Every Swift gameplay test file has a Kotlin counterpart. Deliberately not
ported: the old-save migration tests and the `Retired*` tests. See `docs/PARITY.md`.

### App module (`app/`) - on `main`, working in code, not yet seen running

| Area | Where | Notes |
|---|---|---|
| libGDX in Compose | `farm/FarmFragment.kt`, `MainActivity.kt` | fragment placed by an `AndroidView`; natives unpacked per ABI by Gradle tasks in `app/build.gradle.kts` (16 KB aligned, verified) |
| Farm drawing | `farm/FarmRenderer.kt`, `farm/TextureLibrary.kt`, `farm/StandingSource.kt` | grass, farmland, paths, water, buildings, trees, rocks, decorations, fences, crops back to front; seasonal art |
| Farm effects | `farm/effects/`, hooks in `FarmGame.render` and `FarmRenderer(groundEffects=)` | rain, snow/leaves, fireflies, butterflies, night lights, wind, shadows, water, fireworks; see `docs/FARM_EFFECTS.md`. **Wired** (all four phases drawn each frame). Fireworks `celebrateAround` and butterfly catching (`effects.butterflies.catchable`) are NOT hooked to game events yet |
| Tractor, animals | `farm/TractorLayer.kt`, `farm/animals/`, `game/GameTick.kt` | parked and driving; pets, chickens, livestock, eggs, fish; walking in `walkAnimals` |
| Camera, taps, holds | `farm/FarmGame.kt`, `game/FarmTouch.kt`, `game/FarmHold.kt` | tap = open sheet (building/animal/fish via `SheetTargets.at`), else place or use tool; long press = pick up building/fruit tree/decoration/player tree to move, or `Confirmation` to destroy crops/fertilizer |
| Placement, tool drag | `game/FarmTouch.kt`, `game/ToolDrag.kt`, `ui/ToolCircle.kt`, `ui/Hud.kt` | green/red preview, Place and Cancel; HUD circle dragged onto the farm |
| Sheets | `ui/sheets/` (Codex, merged) | building and animal sheets; `GameSession.sheets` state opened from `touch`, shown by `Hud` via `SheetHost` |
| Fishing | `ui/fishing/` (Codex, merged) | `useTool` hooks a fish -> `FishingCast.start` -> `GameSession.casts` -> `FishingOverlay` in `Hud` |
| Game clock, events | `game/GameTick.kt`, `game/GameSession.kt` | `events`, `newDays`, `casts`, `sheets`, `confirmations`, `view`, `photos` |
| Persistence, save slots | `game/GamePersistence.kt`, `game/FarmSlots.kt`, `ui/screens/SaveSlotsScreen.kt` | autosave, backup recovery, time away; `switchTo/startNew/rename/delete/restoreSaved/slots/backups`; "Farms" button in `Hud`. JVM-tested; screen unverified |
| Sound, haptics | `audio/` (Codex), `haptics/Haptics.kt`, `MainActivity.listenForSound` | effects with distance/pan, seasonal music, night hush; `HapticCue.cue(events)` -> `Haptics.play` (throttled `performHapticFeedback`) |
| Stocks, notifications | `market/`, `notifications/` (Codex) | Yahoo quotes; WorkManager "field ready" reminder |
| Menus, screens | `ui/menus/`, `ui/screens/` (Codex) | build, decorate, terrain, storage; quests, stock market, career, upgrades, level-up, notices, settings (includes privacy + changelog) |
| HUD | `ui/Hud.kt` | status bar, tools, pickers, screen buttons, notices, Photo button, rejection `Toast`s, confirmation dialog |
| Night tint, farm photo | `FarmGame.drawNightTint`, `farm/FarmPhoto.kt`, `ui/PhotoSaver.kt` | 4096 (falls back to 2048) PNG to gallery |
| Assets | `app/src/main/assets/images`, `audio` | images copied as is; effects converted CAF->WAV by `tools/convert-audio.mjs`; music copied |

## In flight right now (Codex, NOT merged)

Three Codex runs were started at about 10:10 on 2026-11-01 from `docs/codex/{timelapse,screens2,ambience}.md`
(prompt = `docs/codex/common.md` + the brief). They were still running when this was written. Each is in a sibling
worktree on branch `codex/<name>`; Codex cannot commit from its sandbox, so its work is **uncommitted files in the
worktree**. Check whether they finished with `tasklist | grep -i codex` (no `codex.exe` left = done) and
`git -C ..\stock-farmer-android-wt-<name> status --short`. Logs are in the old session's scratchpad (may be gone); a
finished run ends its log with a report and a `tokens used` line.

| Slice | Worktree | Brief | Expected result |
|---|---|---|---|
| timelapse | `..\stock-farmer-android-wt-timelapse` | `docs/codex/timelapse.md` | `ui/timelapse/` (`TimelapseRecorder(session, store).tick(now)`, `TimelapseScreen`, viewer), edits to `FarmGame.kt` and `PhotoRequests.kt` |
| screens2 | `..\stock-farmer-android-wt-screens2` | `docs/codex/screens2.md` | `ui/screens/`: `PerformanceScreen`, `ChangelogState`, `LessonGuide(State)`, `LaunchScreen`/`LaunchPresentation` (reuses core `LaunchSequence`); save slots were done by hand, ignore them in the brief |
| ambience | `..\stock-farmer-android-wt-ambience` | `docs/codex/ambience.md` | `ui/rewards/` (`RewardFlightTracker`, `RewardFlights` composable), `audio/` `AmbienceDriver`, `AnimalVoices`, `ReadyChime`, edits to `SoundCenter` |

Finish each slice (this worked for fishing, sheets and effects):

1. `cd` into its worktree, `git stash -u && git rebase main && git stash pop` (main has moved: `Hud.kt`, `FarmGame.kt`,
   `GameSession.kt`, `GamePersistence.kt`, `docs/PARITY.md` are the likely conflicts; prefer `main`'s shared files).
2. `export JAVA_HOME=...; ./gradlew spotlessApply spotlessCheck :app:assembleDebug :app:testDebugUnitTest --no-daemon --offline`.
   Codex output is unformatted: if `spotlessApply` reports `ktlint max-line-length` at a bogus line (`L1`/other), the
   culprit is a line that is over 120 columns **after** formatting; find lines over ~110 columns
   (`awk 'length>110'`) and split them with local vals. Fix flaky tests (e.g. an exact float count).
3. `git add -A && git commit` (both co-author lines), then from the main checkout `git merge --ff-only codex/<name>` and
   `git push`.
4. Then wire it in (below).

Old Codex worktrees (`audio`, `persistence`, `market`, `animals`, `menus`, `screens`, `sheets`, `fishing`, `effects`) are
merged and can be removed with `git worktree remove --force <path>` and `git branch -D codex/<name>`.

To start a new slice:

```powershell
git worktree add -b codex/<name> ..\stock-farmer-android-wt-<name> main
copy local.properties ..\stock-farmer-android-wt-<name>\
# prompt = docs/codex/common.md + a slice brief; run in the background from the worktree:
codex exec -m gpt-6.1-sol -c model_reasoning_effort='"high"' -s workspace-write --add-dir C:\Users\hunter\workspace\stock-farmer-ios -C <worktree> - < prompt.md
```

Do not wait for a log with a grep for `usage limit` or `tokens used`: the logs echo this file and match early. Match `^ERROR`
for failures and a line starting `tokens used` for done.

## Next steps, in order

1. Finish and merge the three in-flight slices above.
2. **Wire them in** (all through `Hud.kt` / `MainActivity.kt` / `GameViewModel`; the Codex slices expose composables and
   small drivers and report how to plug them in):
   - timelapse: start `TimelapseRecorder` from a coroutine tied to the session, add a "Timelapse" screen button; the iOS
     entry points are `GameViewModel+Timelapse.swift` (`configureTimelapse`, called when a farm is switched too).
   - `LaunchScreen` replaces the plain "Opening your farm…" in `MainActivity`; `LessonGuide` over the tool circle;
     `PerformanceScreen` and a "Farms"-style button for it.
   - `RewardFlights` over the HUD; `AmbienceDriver.tick(now)`, `AnimalVoices`, `ReadyChime` from `MainActivity.listenForSound`.
   - Hook `effects.fireworks.celebrateAround(...)` to the first-building celebration event and the butterfly net
     (`effects.butterflies.catchable(tile)` + `remove`) to the Bucket tool tap (iOS `GameViewModel+Butterflies.swift`).
3. Remaining iOS features not yet ported (compare the iOS `HUD` and `App` folders): stock quote session / network monitor
   (`StockMarketSession.swift`, `NetworkMonitor.swift`), tool upgrade flow (`GameViewModel+ToolUpgrade.swift`),
   tractor controls (`GameViewModel+Tractor.swift`), animal menus' scene-only gestures (pet/nudge via
   `onAnimalInteraction`), unlock "new" dots (`UnlockKeys.swift`, `NewDot.swift`), chick appearance screen, item
   detail screens, `FeedbackPresenter` quest banner/toast text, `HUDSheet`, `ToolRing`/`ToolPalette` (the Android HUD uses
   plain Material chips and buttons, not the iOS look - decide how close to match visually; "exact remake" is the goal).
4. Quality passes: blurred menu backdrops (`Modifier.blur` needs API 31, `minSdk` is 26 - decide between raising `minSdk`
   to 31 or a no-blur fallback), encode the WAV effects to OGG to cut the 115 MB debug APK (no ffmpeg here; a JVM or Node
   encoder is needed), 16 KB page-size check of the whole APK, tablet layouts, farm photo should not include ground effects
   tied to the live camera (`FarmPhoto` reuses `FarmRenderer`, which now runs `groundEffects`).
5. **Final testing on the tablet** (only when everything above is done): install, launch, play through the first
   session, fix what breaks. Capture crashes with `adb logcat`.
6. Play Store prep (deferred): applicationId, signing (never commit keys), AAB, store listing, privacy policy, target SDK.

Always keep `docs/PARITY.md` current when something is added or skipped (its "Slices" table is behind: add rows for
fishing, sheets, effects, save slots, haptics, holds).

## Build and check

JDK 25 only: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot`. A fresh worktree needs the git-ignored
`local.properties` (`sdk.dir=C:/Users/hunter/AppData/Local/Android/Sdk`, forward slashes) copied in.

```bash
export JAVA_HOME="C:\\Program Files\\Eclipse Adoptium\\jdk-25.0.4.7-hotspot"
./gradlew spotlessApply spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Add `--offline` only when no new dependency is needed. To see just the verdict:
`... 2>&1 | grep -E "^e: |^BUILD|FAILED|What went wrong"`. Count core tests by summing `tests=` in
`core/build/test-results/test/*.xml`.

Versions: AGP 9.5.0, Kotlin 2.4.21, Compose BOM 2026.09.00, libGDX 1.14.2, WorkManager 2.10.5, JUnit 5.12.2,
`compileSdk` 37 (Compose needs it), `targetSdk` 36, `minSdk` 26. Android SDK licenses were accepted by the user on
2026-11-01; platforms 36 and 37.0 and platform-tools are installed. `sdkmanager` now just forwards to
`%LOCALAPPDATA%\Android\Sdk\cmdline-tools\latest\bin\android.exe sdk install <package>`.

## The tablet

Lenovo Idea Tab Pro Gen 2 (`TB390FU`, Android 16), wireless debugging, **paired and connected** on 2026-11-01
(`adb devices` lists `192.168.1.109:37725`). Wireless-debugging ports change whenever the dialog or the Wi-Fi changes and the
pairing code expires quickly: if `adb devices` no longer lists it, ask the user for a fresh `ip:pairing-port` and code, then
`adb pair <ip:port> <code>` and `adb connect <ip>:<connect-port>`. `adb` is at
`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe`. Do not deploy until the end of development.

## Gotchas learned the hard way

- Bash heredocs containing certain quotes sometimes break ("unexpected EOF"); write files with the Write tool instead.
- The Write/Edit tools refuse a file you have not Read in the session; read first.
- Function references cannot use default parameters: `session::touch` fails because `touch(point, now = ...)`; use a lambda.
- Rejections (`GameEvent.Rejected`) are shown by `Hud` as a `Toast` of `rejection.message`; the engine's `reject(...)`
  is `internal` to core, so app code builds `GameEvent.Rejected(Rejection.X)` itself (see `FarmHold.kt`).
- Codex stops with "usage limit" when the account runs out; partial work stays in the worktree. It cannot commit from
  its sandbox. Its test and formatting leftovers (lines over 120 columns, flaky timing asserts) are normal.
- Spotless/ktlint rewrites `when` branches with extra indentation; lines near 117+ columns can exceed 120 after formatting.
  Spotless failures report a bogus line number; look for long lines in the file.
- New dependencies cannot resolve with `--offline` until downloaded once.
- Core times are rounded to microseconds so saves round-trip; the seeded random helpers follow Swift's algorithms but were
  never compared against a Swift toolchain.
- `core` mutable game state: `GameState` is a mutable class owned by one `GameEngine`; the app accesses it only through
  `GameSession.read/act` (the render thread, main thread and tick take turns).
- The farm uses Y-up world coordinates: `worldY(row) = (GRID_SIZE - 1 - row) * TILE`, tile = 100 units, art is 48 px/tile.
- `GamePersistence` slot changes (`switchTo`, `startNew`, `restoreSaved` on the active slot) save the farm, change the
  slot on the disk queue, then run `load()`, which briefly publishes `isReady = false` (the Hud is torn down and rebuilt).
- Do not start new work in the iOS repo; it is the read-only reference.
