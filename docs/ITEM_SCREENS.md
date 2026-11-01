# Item screens

Reference: iOS `d01e7bb`. No core behavior changes. No device or emulator was available; visual parity remains unverified.

## Entry points for the concurrent HUD rebuild

- Keep calling `MenusHost(session, openMenu: Menu, onClose)` for catalog/storage menus. Catalog rows now have info buttons
  and new dots. The info pages return to the catalog; Select closes the menus.
- Call `MenusHost(session, screen: ItemScreen, onClose, onSelected, onUpgradeResult)` from the floating tool choices:
  `ItemScreen.Item(shopItem)` for seeds, fertilizer and other shop items, `Eraser` for Grass,
  `Harvest(tool)` for implement info, and `Upgrade(tool)` for the purple Upgrade action. Show Upgrade when core
  `offersUpgrade(tool)` is true. The upgrade page also handles level locks and fully upgraded tools.
- Forward `onUpgradeResult`: on success play the built feedback and display `result.message` in the HUD toast after the
  sheet dismisses. Rejections remain visible on the upgrade page. The core upgrade intentionally returns an empty event
  list on success, so this callback supplies the success feedback.
- Tool/category dots: `session.hasNewItems(UnlockCategory.PLANT / FERTILIZE / TERRAIN / BUILD / DECORATE)`.
  Individual choices: `session.act { it.isNew(item) }`, using `ui.menus.isNew`. Use the existing `newDot` modifier.
  Teaching suppresses item dots. Selecting a shop item through a HUD path outside `MenusHost` must call
  `engine.markSeen(item)` in the same `session.act` block after successful selection.
- Keep `SheetHost(session, target, onClose, onAnimalInteraction)` and pass the scene callback for Pet/Nudge. The callback
  receives a stable `AnimalRef` and `AnimalInteraction`. These gestures have no core action and remain disabled when
  the callback is absent. Sheets dismiss after forwarding the gesture. Naming and release are wired through the session.
- `SheetHost` owns Next Chick, Recipes and home-upgrade navigation. Standalone `ChickAppearanceScreen(session, origin,
  onClose)` and `WorkshopRecipeScreen(session, origin, onClose)` are also exposed. Successful choices return to the
  building; rejected choices stay open. Pet Summon dismisses the building only on success.

Unlock state uses the existing serialized `GameState.seenUnlocks`. `GameSession` and each persistence farm load count
the initial unlocks once. Later unlocks remain new, including each increased farmland allowance. Recipe and chicken
selection use the existing core methods that record their seen keys. No separate preferences or save schema were added.

## Reference coverage

| iOS file | Status | Android implementation |
|---|---|---|
| `HUD/ItemDetailScreens.swift` | Ported | Item, Grass and harvest implement detail pages, stats, requirements, variants, fountain toggle and gathered-item destination |
| `HUD/ItemInfo.swift` | Ported | Pure `ItemInfo` and `ToolInfo` builders; shared `EffectLine` semantic colors and `EffectsCard` |
| `HUD/ToolUpgradeScreen.swift` | Ported | Next/current level, effects, price, level requirement and purchase/refusal UI |
| `App/GameViewModel+ToolUpgrade.swift` | Ported | `GameSession.upgradeTool`; success feedback returned to the HUD callback |
| `HUD/ChickAppearanceScreen.swift` | Ported | Separate screen, Random Hen, variant art, rooster text, selection and new dots |
| `HUD/TractorCropSection.swift` | Already covered; layout/text aligned | Existing core overview and actions reused; readiness, cargo, harvest, cancellation and farmland usage |
| `HUD/WorkshopRecipes.swift` | Ported | Separate recipe screen and Choose Recipe link with aggregate/individual new dots |
| `HUD/PetHouseSection.swift` | Already covered; gaps filled | Existing pet form retained; resident art/text aligned and successful Summon now closes |
| `HUD/BuildingSheet.swift` | Ported over existing actions/state | HUD sheet chrome, shared building descriptions, specialized tractor/market content, home/recipe navigation |
| `HUD/AnimalMenu.swift` | Ported over existing actions/state | Pet/Nudge/Name/Release/Cancel options and separate naming alert; scene callback hookup remains with HUD |
| `HUD/NewDot.swift` usage | Ported | Existing primitive reused on catalog, chicken, recipe and recipe-link choices; category-dot query exposed |
| `App/UnlockKeys.swift` | Ported | Tested key builders, initial counting, teaching suppression and saved seen keys |

`ui/Hud.kt` and `ui/hud/` were not edited. Rendering, tablet sizing, native sheet gestures, scene petting/nudging and
post-dismissal upgrade sound/toast need device/integration verification.

## Files changed

- `game/`: `GameSession.kt`, `GamePersistence.kt` (initial unlock counting).
- `ui/menus/`: new `ItemDetailScreens.kt`, `ItemInfo.kt`, `ToolInfo.kt`, `UnlockTracking.kt`; updated `MenusHost.kt`,
  `MenuState.kt`, `MenuSelection.kt` (routes, catalog presentation, selection/seen tracking).
- `ui/sheets/`: new `BuildingChoiceScreens.kt`; updated `BuildingSections.kt`, `SheetHost.kt`, `SheetActions.kt`,
  `SheetState.kt`, `AnimalSection.kt`; deleted `BuildingDetails.kt`, replaced by the shared item builder.
- JVM tests: `ui/menus/ItemInfoTest.kt`, `ToolInfoTest.kt`, `UnlockTrackingTest.kt` and
  `ui/sheets/BuildingChoiceTest.kt` under `app/src/test/kotlin/com/hunter/stockfarmer/`.
- Documentation: this file and `docs/PARITY.md`.

All production Kotlin paths above are relative to `app/src/main/kotlin/com/hunter/stockfarmer/`.
