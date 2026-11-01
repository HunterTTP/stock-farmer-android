package com.hunter.stockfarmer.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/** Construct during Activity.onCreate; retain the policy in the session across activity recreation. */
class FieldReadyNotificationPermission(
    private val activity: ComponentActivity,
    private val policy: NotificationPermissionPolicy,
) {
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** Call after a successful planting, and when becoming active with existing crops. */
    fun requestIfNeeded(hasCrops: Boolean) {
        val isGranted =
            Build.VERSION.SDK_INT < 33 ||
                activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (policy.shouldRequest(Build.VERSION.SDK_INT, isGranted, hasCrops)) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
