# Handoff: where the project stands and what to do next

Read this first in a fresh session, then `AGENTS.md`, `docs/DEVELOPMENT.md` and `docs/PARITY.md`.
Last updated at `main` = `715e01a` (2026-11-01).

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
- Spread load to Codex: hand independent slices to `codex exec -m gpt-6.1-sol` (planning tasks only: `gpt-6-astra`).
- A Stop hook keeps asking for: "code written, tests passing and everything building without errors; skip emulator and
  on-phone tests for now". Keep going until the app is complete, do not stop to ask.
- **Do not install, launch or test on the tablet until the very end of development** (see "The tablet").
- Never claim a check passed that was not run. Use they/them for people whose pronouns are unknown.

## Verified state (all green on `main`)

```
./gradlew spotlessCheck :core:test :app:assembleDebug :app:testDebugUnitTest   -> BUILD SUCCESSFUL
core tests: 1012, 0 failures.   app unit tests pass.   debug APK builds.
```

The app has never been run on a device or emulator. Everything visual is unverified.

### Core module (`core/`, pure Kotlin) - complete

The whole rules engine, models, catalogs, persistence, feedback (sound cue logic), market parser and presentation helpers are
ported with tests. Every Swift gameplay test file has a Kotlin counterpart. Deliberately not ported: the old-save migration
tests and the `Retired*` tests. See `docs/PARITY.md`.

### App module (`app/`)

On `main` and working in code (not yet seen running):

| Area | Where | Notes |
|---|---|---|
| libGDX in Compose | `farm/FarmFragment.kt`, `MainActivity.kt` | fragment placed by an `AndroidView`; natives unpacked per ABI by Gradle tasks in `app/build.gradle.kts` (16 KB aligned, verified) |
| Farm drawing | `farm/FarmRenderer.kt`, `farm/TextureLibrary.kt`, `farm/StandingSource.kt` | grass, farmland, paths, water, buildings, trees, rocks, decorations, fences, crops back to front; seasonal art; `StandingSource` hook for extra layers |
| Tractor | `farm/TractorLayer.kt`, `FarmGame.driveTractor` | parked and driving, moved every frame |
| Animals | `farm/animals/` | pets, chickens, livestock, eggs, fish (Codex); walking is `walkAnimals` in `game/GameTick.kt` |
| Camera, taps | `farm/FarmGame.kt`, `game/FarmTouch.kt` | tap = use tool, start placing, or carry a thing being placed; pan and pinch zoom |
| Placement | `game/FarmTouch.kt`, `ui/Hud.kt` | green/red preview, Place and Cancel |
| Tool drag | `game/ToolDrag.kt`, `ui/ToolCircle.kt`, `game/FarmView.kt` | HUD circle dragged onto the farm; `FarmView.tileAt` maps screen to tile |
| Game clock | `game/GameTick.kt`, `game/GameViewModel.kt` | once a second: hatch, births, eggs, fish, pets, grazing, trees, new day, animal steps |
| Events | `game/GameSession.kt` | `events` and `newDays` flows; `view` (camera snapshot), `photos` |
| Persistence | `game/GamePersistence.kt`, `SaveScheduler.kt`, `GameViewModel` | loads at start, autosaves, handles time away, backup recovery |
| Sound | `audio/` (Codex), wired in `MainActivity.listenForSound` | effects with distance and pan, seasonal music, night hush |
| Stocks, notifications | `market/`, `notifications/` (Codex), wired in `MainActivity` | Yahoo quotes; WorkManager "field ready" reminder, permission asked after first planting |
| Menus | `ui/menus/` (Codex) | build, decorate, terrain, storage; opened from `Hud` |
| Screens | `ui/screens/` (Codex) | quests, stock market, career, upgrades, level-up, notices, settings; opened from `Hud` |
| HUD | `ui/Hud.kt` | status bar, tools, seed and harvest-tool pickers, screen buttons, notices, Photo button |
| Night tint | `FarmGame.drawNightTint` | by `DayNightCycle.darkness` |
| Farm photo | `farm/FarmPhoto.kt`, `ui/PhotoSaver.kt`, `game/PhotoRequests.kt` | 4096 (falls back to 2048) PNG to gallery |
| Assets | `app/src/main/assets/images`, `audio` | images copied as is; effects converted CAF->WAV by `tools/convert-audio.mjs`; music copied |

## In flight right now (Codex worktrees, NOT merged)

Sibling worktrees of this repo (`git worktree list`), each on its own branch `codex/<name>`. Codex cannot commit from its
sandbox (it cannot write the worktree's git dir), so its work is **uncommitted files in the worktree**: review, build,
commit and merge it yourself.

| Slice | Worktree | State when this was written |
|---|---|---|
| sheets | `..\stock-farmer-android-wt-sheets` | still running: `ui/sheets/` (building and animal menus: workshops, pet house, coop, livestock homes, house, tractor, storage; `targetAt(tile)` classifier and `SheetHost`) |
| fishing | `..\stock-farmer-android-wt-fishing` | reported finished and a build passing: `ui/fishing/` (`FishingOverlay`, `FishingSession`) plus a `docs/PARITY.md` edit; **not yet reviewed or committed** |
| effects | `..\stock-farmer-android-wt-effects` | still running: `farm/effects/` (rain, snow/leaves, fireflies, butterflies, night lights, wind, shadows, water) and a `TextureLibrary.kt` edit |

Prompts for every slice are saved in `docs/codex/` (`common.md` is the shared preamble). To finish a slice:

1. `cd` into its worktree, `git stash -u && git rebase main && git stash pop` (resolve conflicts; usually `Hud.kt`,
   `GameViewModel.kt`, `app/build.gradle.kts`, `docs/PARITY.md` - prefer `main`'s version of shared files).
2. Run the full check below; fix leftovers (Codex runs often stop with lines over 120 columns or a flaky test).
3. `git add -A && git commit` (with the co-author lines), then from the main checkout `git merge --ff-only codex/<name>` and push.
4. Then wire it in (see "Next steps").

Older Codex worktrees (`audio`, `persistence`, `market`, `animals`, `menus`, `screens`) are already merged and can be removed
with `git worktree remove --force <path>` and `git branch -D codex/<name>`.

To start a new slice:

```powershell
git worktree add -b codex/<name> ..\stock-farmer-android-wt-<name> main
copy local.properties ..\stock-farmer-android-wt-<name>\
# prompt = docs/codex/common.md + a slice brief; run in the background from the worktree:
codex exec -m gpt-6.1-sol -c model_reasoning_effort='"high"' -s workspace-write --add-dir C:\Users\hunter\workspace\stock-farmer-ios -C <worktree> - < prompt.md
```

Codex logs are only on stdout; redirect them to a file and check the tail for `tokens used` (done) or `usage limit` (hit the
account limit: it resets on its own, and the user can also reset it).

## Next steps, in order

1. Finish and merge the three in-flight slices above.
2. **Wire them in:**
   - Taps on buildings and animals -> `GameSession.targetAt(tile)` -> `SheetHost` (extend `FarmTouch.touch`/`Hud`; iOS rule: a
     tap acts on whatever is there, crops first, then fences, buildings, animals; see `App/GameViewModel+Gestures.swift`).
     Holding on a building, fruit tree, decoration or tree picks it up to move it (`requestRemoval`); holding on crops or
     fertilizer offers to destroy them.
   - Fishing: start from the rod tap (`castRod` in core `EngineFish.kt`), show `FishingOverlay`.
   - Effects: add `effectLayers(textures)` sources/overlays to `FarmRenderer`/`FarmGame` (night tint already drawn).
3. Remaining iOS features not yet ported (check the iOS `HUD` and `App` folders): timelapse capture and playback
   (`GameViewModel+Timelapse.swift`, core `TimelapseStore`/`TimelapsePlayback`), save-slots screen, performance screen,
   lessons guide (`LessonGuide.swift`, core `Lesson` rules), launch screen, rewards flying to the HUD, haptics (vibration),
   the stock quote session / network monitor, ambience (`tendAmbience`), "everything is ready" chime, animal voices.
4. Quality passes: the blurred menu backdrops spike (iOS blurs behind menus; `Modifier.blur` needs API 31, `minSdk` is 26 -
   decide between raising `minSdk` to 31 or a no-blur fallback), encode the WAV effects to OGG to cut the 115 MB debug APK
   (no ffmpeg on this machine; a JVM or Node encoder is needed), 16 KB page-size check of the whole APK, tablet layouts.
5. **Final testing on the tablet** (only when everything above is done): install, launch, play through the first
   session, fix what breaks. Capture crashes with `adb logcat`.
6. Play Store prep (deferred): applicationId, signing (never commit keys), AAB, store listing, privacy policy, target SDK.

Always keep `docs/PARITY.md` current when something is added or skipped.

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
- Codex stops with "usage limit" when the account runs out; partial work stays in the worktree. It can also not commit
  from its sandbox. Its test and formatting leftovers (lines over 120 columns, flaky timing asserts) are normal.
- Spotless/ktlint rewrites `when` branches with extra indentation; lines near 117+ columns can exceed 120 after formatting.
  Spotless failures report a bogus line number (`L1`); look for long lines in the file.
- New dependencies cannot resolve with `--offline` until downloaded once.
- Core times are rounded to microseconds so saves round-trip; the seeded random helpers follow Swift's algorithms but were
  never compared against a Swift toolchain.
- `core` mutable game state: `GameState` is a mutable class owned by one `GameEngine`; the app accesses it only through
  `GameSession.read/act` (the render thread, main thread and tick take turns).
- The farm uses Y-up world coordinates: `worldY(row) = (GRID_SIZE - 1 - row) * TILE`, tile = 100 units, art is 48 px/tile.
- Do not start new work in the iOS repo; it is the read-only reference.
