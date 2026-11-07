# Syncing with the iOS repo

The iOS game at `../stock-farmer-ios` and this remake port gameplay changes to each other. The rules (commit trailers, `Ported-From`, the size-to-agent table and the porting contract) are in [AGENTS.md](../AGENTS.md), under Syncing with the iOS repo.

iOS synced through: 5279b68

This repo has everything iOS had up to and including that iOS commit, except the open rows below that sit before it. Move it forward only when ported work is merged here.

## How to port from iOS

1. Fetch the iOS repo, then list what is left: `python scripts/port-backlog.py --from ../stock-farmer-ios`. It reads iOS's `origin/main` (or `main`) after the watermark above, adds the open rows of the legacy table, skips `Port: none` and anything a `Ported-From: ios@<sha>` line here already credits, and suggests an agent from each commit's size.
2. Port each item with its Swift Core tests as the spec: port the tests to `core` first, then the code, until the Kotlin tests and the gate in `docs/HANDOFF.md` pass. If this repo's tests already disagree with them, stop and ask the owner.
3. Commit with `Port: none` and one `Ported-From: ios@<sha>` per source commit, then move the watermark when the ported work is merged.

`--since <sha>` starts the list somewhere else; `python scripts/port-backlog.py --self-test` checks the script.

## How the watermark was set

This remake was ported from iOS `d01e7bb` (2026-10-22). On 2026-11-06 the iOS history was squashed into a single root commit, `5279b68`, with exactly the tree of the old `6d8a184`; the commits from `d01e7bb` to `6d8a184` now live only on local iOS branches, so their shas below are pre-squash. Going through them against this repo's log and code:

- Android already has every gameplay change among them except the two open rows below (`da3bb70`, `6d8a184`). Several started here and were ported to iOS (ducks, placed trees and rocks, Progress, the Storage sell sound, the instant planting drag, the notifications question, the quiet-farm save); the Dynamic Shadows removal was ported here as `aa3edd4`.
- The rest are iOS platform work, iOS performance and save-path tuning, tests or docs: `none`.

So the watermark is the squash root `5279b68`, with the open pre-squash rows kept in the table until a `Ported-From` credits them. The table also classifies the untagged iOS commits after it.

## Legacy classification

iOS commits made before the trailers existed. The script uses a row when an untagged commit matches its sha or, failing that, its subject (so a rebased commit still matches). A row for a commit that is neither after the watermark nor behind it (the squashed history) stays in the backlog until a `Ported-From` credits it, unless it is `none`.

Commits still in flight on iOS when this table was written (item description stat rows, and anything after `bfa8f5f`) carry their own trailers once they land. Tag-derived app versioning (`353b261`, `Port: none`) is listed in case it lands untagged.

| Commit | Date | Subject | Port | Size | Note |
| --- | --- | --- | --- | --- | --- |
| ios@3df2df5 | 2026-11-01 | Document the Windows/WSL/xtool device setup and make the changelog parser accept CRLF | none | | iOS device setup; the Android changelog is LF-only |
| ios@3f5a675 | 2026-11-03 | Keep a successful save from clearing unrelated messages | none | | Android keeps save failures apart already (c0c9707, 266b30f) |
| ios@0261839 | 2026-11-03 | Remove the Dynamic Shadows setting and everything that drew it | none | | Ported as aa3edd4 |
| ios@38131f4 | 2026-11-06 | Give no logs or stone for trees and rocks the player placed | none | | Started on Android (54bf3ac) |
| ios@29bb3f5 | 2026-11-06 | Rename the Career screen to Progress | none | | Started on Android (042e16d) |
| ios@3720e82 | 2026-11-06 | Ducks lay no eggs, make no sound and are sold for $25 instead of released | none | | Started on Android (6c11229, b5eab16) |
| ios@81552ac | 2026-11-06 | Re-record golden digests for the combined duck, rock and Progress changes | none | | iOS test digests only |
| ios@1437a88 | 2026-11-06 | Play the sell sound when harvesting into Storage, and when the tractor stores its load | none | | Started on Android (993e5c9); PlacedCue already sells on TractorCargoStored |
| ios@4fe8f1a | 2026-11-06 | Start planting the instant a dragged seed has moved one button width | none | | Started on Android (c237dc0); Storage note line break already here |
| ios@cb75089 | 2026-11-06 | Ask about notifications after the planting drag ends, not in the middle of it | none | | Started on Android (dfbd6e6) |
| ios@262b8ab | 2026-11-06 | Stop saving a quiet farm with ponds or animal buildings every second | none | | Started on Android (74123d9) |
| ios@da3bb70 | 2026-11-06 | Rebalance audio around Master, Music, Effects and Ambience buses | rules | L | Port SoundMixTests and SoundCueTests first: Master bus, sliders in dB over 40 dB, one limiter per bus, music at -30 LUFS ducking 6 dB under fanfares, rain -10 dB; convert the re-mastered clips; skip SoundSettingsMigration |
| ios@6d8a184 | 2026-11-06 | Hide the Time sheet presets behind a version-tap unlock | ui | M | Port RepeatedTapTests first; Time sheet shows only seasons, 7 taps on the About version line toggle a device-wide developer time controls preference |
| ios@b91cbe8 | 2026-11-06 | Remove the night-hours line from the Time sheet | ui | S | Delete the night-hours text from TimeSheet |
| ios@43798fe | 2026-11-06 | Grey out the whole row of locked items, name included | ui | S | Dim a locked choice row's name too, not only when unaffordable or at its limit (ChoiceList) |
| ios@8760285 | 2026-11-06 | Hold the loading screen until the audio is ready | ui | M | Launch overlay waits for effect clips loaded, the rain loop opened and the season's first music track prepared; a clip that fails is logged and skipped |
| ios@bfa8f5f | 2026-11-06 | Balance the effect trims and lower the Music, Effects and Ambience buses | rules | M | After da3bb70: port SoundCueTests trims (shared -8, fanfares -4, drag-repeated cues 3 dB lower) and SoundMixTests (100% = -8/-10/-12 dB for Music/Effects/Ambience) |
| ios@353b261 | 2026-11-06 | Set the app version from the release tag in Xcode Cloud | none | | iOS release tooling |
