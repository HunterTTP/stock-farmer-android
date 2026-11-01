# Quest, stock, career and settings screens

Reference: iOS `d01e7bb`. These composables take `session: GameSession` and `onClose: () -> Unit`:

- `MarketQuestScreen`: persistent quest boards, automatic rewards, replacement confirmation and stand upgrades.
- `StockMarketScreen`: Yahoo ticker searches, holdings, displayed-price trades, profit previews and house upgrades.
- `CareerScreen`: career totals, XP/income/stock-profit charts and all nine searchable, filterable collections.
- `BuildingUpgradeScreen`: market and animal-home upgrades; pass `origin` for a building menu.
- `LevelUpScreen`: catalog unlocks; pass the `LevelUnlocks` from the event that opened it.
- `DaySummaryBanner`: pass the `DaySummary` returned by the clock action.
- `QuestBanner`: pass `QuestReward.from(events)` for completed quests.
- `SettingsScreen`: persisted audio channels, mute switches, privacy and the reference changelog.

No HUD, activity, game-session, view-model, farm or menu files are changed. Core behavior is unchanged.

## Connecting to `ui/Hud.kt`

Keep the selected screen in the HUD's Compose state and draw the selected entry above the existing HUD in its
parent `Box`. Buttons set that selection; `onClose` clears it. For example:

```kotlin
when (openScreen) {
    "quests" -> MarketQuestScreen(session, onClose = { openScreen = null }, onEvents = ::presentEvents)
    "stocks" -> StockMarketScreen(session, onClose = { openScreen = null }, online = isOnline)
    "career" -> CareerScreen(session, onClose = { openScreen = null })
    "settings" -> SettingsScreen(session, onClose = { openScreen = null })
}
```

Open stocks from a House and quests from a Market Stand. A building menu can embed
`MarketQuestSection(session, origin, onEvents)` or open `BuildingUpgradeScreen(session, onClose, origin)`.
Screen snapshots refresh while started; owned-stock polling runs only while resumed and stops when the screen closes.
The provider and engine enforce five-minute quote spacing, including owned-ticker searches and clock rollback.
Pass the app's connectivity state as `online`; false closes trading and shows the offline notice. Provider failures
also appear on screen. The default provider is the app market package's `StockQuoteProvider`, never fabricated prices.
That provider is shared for the process lifetime, preserving its quote cache when the screen is closed and reopened.

`onEvents` is optional on the quest, stock and upgrade screens. Forward it to the owner's feedback/save handling.
Mutations happen inside `GameSession.act`; trade previews use `engine.copy()`. Screen actions claim completed quests
afterward, matching the iOS commit policy. The quest screen also claims quests completed before it was opened.
The general gameplay owner should use the same core claim policy after farm actions outside these screens.

Keep transient notices with the owner that received their events, so opening a screen never invents history:

```kotlin
events.filterIsInstance<GameEvent.LeveledUp>().lastOrNull()?.let {
    levelUnlocks = LevelUnlocks.unlocked(it.from, it.level)
}
// Later, while levelUnlocks != null:
LevelUpScreen(session, onClose = { levelUnlocks = null }, unlocks = levelUnlocks!!)
```

Queue the level notice after the quest banner when both occur together, as iOS does. `QuestBanner` dismisses after
four seconds; `DaySummaryBanner` dismisses after twelve seconds. Pass their actual event values and clear them in
`onClose`. Neither banner advances the day or awards XP. The level screen's two-argument fallback shows only the
current level's unlocks; supplying the event range preserves multi-level gains.

For live sound changes, provide the activity-owned audio facade above the HUD:

```kotlin
val screenAudio = remember(soundCenter) { SoundCenterScreenAudio(soundCenter) }
CompositionLocalProvider(LocalScreenAudio provides screenAudio) {
    SettingsScreen(session, onClose)
}
```

Without an audio owner, Settings still reads and writes the existing private `sound` preferences for the next
`SoundCenter`. It creates no second playback engine. Music and effects switches restore the previous channel mix.
The provided facade also enables the current-track label and Next Song button. The changelog is copied from the
reference and line-wrapped without changing its entries; core `Changelog` parses the unwrapped text. Privacy keeps
the reference's local-data and Yahoo disclosures and describes Android permissions and disabled backups.

## Verification

JUnit 5 tests cover screen projections, search/filter order, catalog asset paths, chart scaling, overflow/quantity
rejection, isolated trade previews, quote caching/partial failures/cancellation, quest settlement and audio toggles.
Run the repository's Spotless, core tests, debug assembly and `:app:testDebugUnitTest` tasks from the worktree root.

In this Windows sandbox, verification uses a copied Gradle cache, writable Android home and the in-process Kotlin
compiler. A temporary init script selects an existing debug key under a different filename to avoid Android's
default-key `toRealPath` lock failure. It also sets JUnit TempDir cleanup to `NEVER`: Windows canonical-path lookup
cannot enumerate sandbox ancestors during cleanup. Every test and assertion still executes; no checks are excluded.
These environment settings, debug keys and init scripts are not committed.

No emulator or phone is available. Layout, accessibility, actual network connectivity changes and audible playback
still need device verification after HUD integration.
