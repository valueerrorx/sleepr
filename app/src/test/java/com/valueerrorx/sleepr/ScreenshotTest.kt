package com.valueerrorx.sleepr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Rendert den Hauptbildschirm als PNG nach app/build/screenshots (kein Vergleich, nur zum Ansehen) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class ScreenshotTest {

    @After fun reset() = setEngine(null)

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun idle() = shoot("idle")

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun playing() { setEngine(fakeEngine()); shoot("playing") }

    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun idleLand() = shoot("idle_land")

    @Test @Config(qualifiers = "w800dp-h1280dp-port-xhdpi")
    fun tablet() = shoot("tablet")

    @Test @Config(qualifiers = "w411dp-h891dp-port-xxhdpi", fontScale = 2f)
    fun idleFont200() = shoot("idle_font200")

    private fun shoot(name: String) {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView
        val dm = activity.resources.displayMetrics
        root.measure(
            View.MeasureSpec.makeMeasureSpec(dm.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dm.heightPixels, View.MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, dm.widthPixels, dm.heightPixels)
        val bmp = Bitmap.createBitmap(dm.widthPixels, dm.heightPixels, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // Engine mit laufendem Status und Timer, ohne Audio-Thread zu starten
    private fun fakeEngine() = NoiseEngine {}.also { e ->
        fun set(f: String, v: Any) = NoiseEngine::class.java.getDeclaredField(f).apply { isAccessible = true }.set(e, v)
        set("running", true)
        set("endFrame", 48000L * 60 * 24 + 48000L * 17)
        set("fadeStart", 48000L * 60 * 20)
    }

    private fun setEngine(e: NoiseEngine?) =
        NoiseService::class.java.getDeclaredField("engine").apply { isAccessible = true }.set(null, e)
}
