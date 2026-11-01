# Field-ready notifications

This ports the single iOS reminder: "Stock Farmer" / "All your crops are ready to harvest!"
Core's `wholeFieldReadyDate()` remains the source of truth, including growth modifiers and fruit trees.

Wiring in the app entry points is intentionally left to the integrating slice:

1. Keep `NotificationPermissionPolicy()` in the game session. During Activity `onCreate`, construct
   `FieldReadyNotificationPermission(activity, policy)` before the Activity starts.
2. Call `requestIfNeeded(hasCrops = true)` after the first successful planting. Also call
   `requestIfNeeded(engine.state.plots.isNotEmpty())` when returning to the foreground. The policy
   offers the API 33+ permission once per session; Android controls whether the dialog appears.
3. Construct `FieldReadyNotifier(WorkManagerFieldReadyScheduler(applicationContext))` once. This
   creates the harvest reminder channel. WorkManager uses its default manifest initializer.
4. On actual app backgrounding, call `notifier.onBackground(engine, now)` on the game's owning
   thread. Use the same lifecycle ordering as iOS: compute readiness before holding the game clock.
   Do not schedule just because focus is lost or the permission dialog appears.
5. On becoming active, call `notifier.onActive()` to cancel pending work. Also cancel when resetting
   or replacing the farm. Scheduling replaces the previous unique `fieldReady` work; an ineligible
   field clears any pending reminder. Delivered notifications are not removed, matching iOS.

The worker checks authorization again at delivery and does not retry denied notifications. Its
immutable tap intent opens the app. A platform notification icon is used because this slice cannot
add drawable resources. Android channel settings control sound and delivery.

WorkManager 2.10.5 was released September 24, 2025:
https://developer.android.com/jetpack/androidx/releases/work#2.10.5
Its initial delay is a minimum, so Doze and OS scheduling can deliver later than the calculated
ready time. Exact timing, the permission dialog, tap navigation, sound, reboot persistence, and
foreground cancellation need device verification.
