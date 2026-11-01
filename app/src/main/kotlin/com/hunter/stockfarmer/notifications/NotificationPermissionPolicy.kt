package com.hunter.stockfarmer.notifications

/** The permission prompt is offered on first planting or on returning to a farm with crops, once per session. */
class NotificationPermissionPolicy {
    private var hasAsked = false

    fun shouldRequest(
        apiLevel: Int,
        isGranted: Boolean,
        hasCrops: Boolean,
    ): Boolean {
        if (!hasCrops || hasAsked) return false
        hasAsked = true
        return apiLevel >= 33 && !isGranted
    }
}
