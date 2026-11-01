package com.hunter.stockfarmer.notifications

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationPermissionPolicyTest {
    @Test
    fun `waits for planting then asks only once per session even after denial`() {
        val policy = NotificationPermissionPolicy()
        assertFalse(policy.shouldRequest(apiLevel = 33, isGranted = false, hasCrops = false))
        assertTrue(policy.shouldRequest(apiLevel = 33, isGranted = false, hasCrops = true))
        assertFalse(policy.shouldRequest(apiLevel = 33, isGranted = false, hasCrops = true))
    }

    @Test
    fun `existing crops on return also qualify for authorization`() {
        assertTrue(NotificationPermissionPolicy().shouldRequest(apiLevel = 36, isGranted = false, hasCrops = true))
    }

    @Test
    fun `older Android and granted permission never prompt`() {
        assertFalse(NotificationPermissionPolicy().shouldRequest(apiLevel = 32, isGranted = false, hasCrops = true))
        assertFalse(NotificationPermissionPolicy().shouldRequest(apiLevel = 33, isGranted = true, hasCrops = true))
    }
}
