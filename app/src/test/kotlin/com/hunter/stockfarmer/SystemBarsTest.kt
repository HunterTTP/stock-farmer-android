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
        val root = File("src/main/kotlin/com/hunter/stockfarmer/GameRoot.kt").readText()
        assertTrue(".background(Color.Black)" in root)
    }

    @Test
    fun `the activity alone hides the system bars, not libGDX`() {
        val activity = File("src/main/kotlin/com/hunter/stockfarmer/MainActivity.kt").readText()
        assertTrue("hide(WindowInsetsCompat.Type.systemBars())" in activity)
        val farm = File("src/main/kotlin/com/hunter/stockfarmer/farm/FarmFragment.kt").readText()
        assertTrue("useImmersiveMode = false" in farm)
    }
}
