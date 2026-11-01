package com.hunter.stockfarmer

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

internal class SystemBarsTest {
    @Test
    fun `the theme paints the navigation bar black`() {
        val theme = File("src/main/res/values/themes.xml").readText()
        assertTrue("""android:navigationBarColor">@android:color/black<""" in theme)
    }

    @Test
    fun `the activity asks for a dark black navigation bar and a black backdrop`() {
        val activity = File("src/main/kotlin/com/hunter/stockfarmer/MainActivity.kt").readText()
        assertTrue("navigationBarStyle = SystemBarStyle.dark(AndroidColor.BLACK)" in activity)
        assertTrue(".background(Color.Black)" in activity)
    }
}
