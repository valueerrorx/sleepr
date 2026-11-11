package com.valueerrorx.sleepr

import android.content.Context
import kotlin.math.pow

data class NoiseColor(val name: String, val slope: String, val hint: String, val color: Int)
data class Preset(val name: String, val levels: Map<Int, Int>, val tone: Int, val waves: Map<Int, Int> = emptyMap())

/** Gemeinsamer Zustand von UI und Audio-Thread, in SharedPreferences gespeichert */
object Mixer {
    val COLORS = listOf(
        NoiseColor("Weiß", "0 dB/Okt", "hell, zischend", 0xFFE9EDF0.toInt()),
        NoiseColor("Rosa", "−3 dB/Okt", "ausgewogen, wie Regen", 0xFFD99AA3.toInt()),
        NoiseColor("Braun", "−6 dB/Okt", "tief, wie Brandung", 0xFFB08465.toInt()),
        NoiseColor("Grau", "gehörangepasst", "wirkt überall gleich laut", 0xFF9BA3AA.toInt()),
        NoiseColor("Grün", "Mitte ~500 Hz", "weich, naturnah", 0xFF86B38D.toInt())
    )
    val PRESETS = listOf(
        Preset("Meer", mapOf(2 to 70, 1 to 35), 70, waves = mapOf(2 to 9, 1 to 13)),
        Preset("Fokus", mapOf(1 to 55, 2 to 35), 100),
        Preset("Einschlafen", mapOf(2 to 70), 55),
        Preset("Abschirmen", mapOf(3 to 50, 1 to 40), 100),
        Preset("Natur", mapOf(4 to 60, 2 to 25), 85),
        Preset("Klassisch weiß", mapOf(0 to 45), 80)
    )
    val TIMERS = intArrayOf(0, 15, 30, 45, 60, 90)

    val levels = intArrayOf(0, 20, 60, 0, 0)
    val waves = BooleanArray(5)              // Wellen-Modulation je Farbe
    val waveSec = intArrayOf(8, 8, 8, 8, 8)  // mittlere Wellenlänge in s
    const val WAVE_MIN = 3
    const val WAVE_MAX = 20
    @Volatile var master = 70
    @Volatile var tone = 100
    @Volatile var timer = 0

    fun gain(v: Int) = (v / 100.0).pow(2) // wahrnehmungsnahe Kurve
    fun gain(v: Double) = (v / 100.0).pow(2)
    fun toneHz(v: Int = tone) = 400.0 * 50.0.pow(v / 100.0) // 400 Hz … 20 kHz log

    fun fmtHz(f: Double) = when {
        f >= 19000 -> "offen"
        f >= 1000 -> String.format(java.util.Locale.GERMAN, if (f < 10000) "%.1f kHz" else "%.0f kHz", f / 1000)
        else -> "${f.toInt()} Hz"
    }

    fun summary(): String {
        val a = COLORS.indices.filter { levels[it] > 0 }.sortedByDescending { levels[it] }
        return if (a.isEmpty()) "Alle Regler auf null" else a.joinToString(" · ") { "${COLORS[it].name} ${levels[it]}" + if (waves[it]) " ∿" else "" }
    }

    fun applyPreset(p: Preset) {
        for (i in levels.indices) {
            levels[i] = p.levels[i] ?: 0
            waves[i] = p.waves.containsKey(i)
            p.waves[i]?.let { waveSec[i] = it }
        }
        tone = p.tone
    }

    private const val PREFS = "mixer"

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        for (i in levels.indices) {
            levels[i] = p.getInt("l$i", levels[i])
            waves[i] = p.getBoolean("w$i", waves[i])
            waveSec[i] = p.getInt("ws$i", waveSec[i])
        }
        master = p.getInt("master", master); tone = p.getInt("tone", tone); timer = p.getInt("timer", timer)
    }

    fun save(ctx: Context) {
        val e = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        for (i in levels.indices) e.putInt("l$i", levels[i]).putBoolean("w$i", waves[i]).putInt("ws$i", waveSec[i])
        e.putInt("master", master).putInt("tone", tone).putInt("timer", timer).apply()
    }
}
