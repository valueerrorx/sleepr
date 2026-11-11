package com.valueerrorx.sleepr

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/** Zeichnet das theoretische Leistungsspektrum der aktuellen Mischung (dB, log-Frequenz) */
class SpectrumView(ctx: Context) : View(ctx) {
    var accent = 0xFF86B7CC.toInt()
    var grid = 0xFF27313A.toInt()
    var muted = 0xFF8E9BA6.toInt()

    private val dp = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    companion object {
        private const val FN = 200
        private val F = DoubleArray(FN) { 20.0 * 1000.0.pow(it / (FN - 1.0)) }
        private val SPEC: Array<DoubleArray> by lazy {
            val fl = DoubleArray(2398) { 20.0 + it * 10 }
            val grey = GREY_FILTERS.map { Biquad(it) }
            val green = GREEN_FILTERS.map { Biquad(it) }
            fun raw(id: Int, f: Double): Double {
                var s = when (id) { 0, 3 -> 1.0; 2 -> 1 / (1 + (f / 150).pow(2)); else -> 1 / f }
                if (id == 3) for (b in grey) s *= b.magSq(f)
                if (id == 4) for (b in green) s *= b.magSq(f)
                return s
            }
            Array(5) { id -> val total = fl.sumOf { raw(id, it) }; DoubleArray(FN) { raw(id, F[it]) / total } }
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val l = 30 * dp; val r = 6 * dp; val t = 8 * dp; val b = 20 * dp
        val pw = w - l - r; val ph = h - t - b
        fun x(f: Double) = (l + pw * ln(f / 20) / ln(1000.0)).toFloat()
        fun y(db: Double) = (t + ph * (-db / 60)).toFloat()

        p.style = Paint.Style.STROKE; p.strokeWidth = 1 * dp; p.pathEffect = null
        p.textSize = 10 * dp
        for ((f, lab) in listOf(50.0 to "50", 100.0 to "100", 200.0 to "200", 500.0 to "500",
            1000.0 to "1k", 2000.0 to "2k", 5000.0 to "5k", 10000.0 to "10k")) {
            p.color = grid; p.style = Paint.Style.STROKE; c.drawLine(x(f), t, x(f), t + ph, p)
            p.color = muted; p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
            c.drawText(lab, x(f), h - 5 * dp, p)
        }
        for (d in listOf(0.0, -20.0, -40.0, -60.0)) {
            p.color = grid; p.style = Paint.Style.STROKE; c.drawLine(l, y(d), l + pw, y(d), p)
            p.color = muted; p.style = Paint.Style.FILL; p.textAlign = Paint.Align.RIGHT
            c.drawText(if (d == 0.0) "0" else "−${(-d).toInt()}", l - 5 * dp, y(d) + 3 * dp, p)
        }

        val lp = Biquad(FSpec(FType.LOWPASS, Mixer.toneHz().coerceAtMost(0.45 * FS)))
        val tr = DoubleArray(FN) { lp.magSq(F[it]) }
        val sum = DoubleArray(FN)
        val comps = (0..4).filter { Mixer.levels[it] > 0 }.map { id ->
            val k = Mixer.gain(Mixer.levels[id]).pow(2)
            val a = DoubleArray(FN) { k * SPEC[id][it] * tr[it] }
            for (i in 0 until FN) sum[i] += a[i]
            id to a
        }
        val mx = sum.maxOrNull() ?: 0.0
        if (mx <= 0) {
            p.color = muted; p.textAlign = Paint.Align.CENTER; p.textSize = 13 * dp
            c.drawText("Alle Regler auf null", l + pw / 2, t + ph / 2, p); return
        }
        fun db(v: Double) = max(-60.0, 10 * log10(max(v, 1e-30) / mx))
        fun build(a: DoubleArray) { path.reset(); for (i in 0 until FN) { val px = x(F[i]); val py = y(db(a[i])); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) } }

        build(sum); path.lineTo(x(F[FN - 1]), t + ph); path.lineTo(x(F[0]), t + ph); path.close()
        p.style = Paint.Style.FILL; p.color = (accent and 0x00FFFFFF) or 0x2A000000; c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        if (comps.size > 1) {
            p.strokeWidth = 1.5f * dp; p.pathEffect = DashPathEffect(floatArrayOf(4 * dp, 3 * dp), 0f)
            for ((id, a) in comps) { build(a); p.color = Mixer.COLORS[id].color; c.drawPath(path, p) }
            p.pathEffect = null
        }
        build(sum); p.color = accent; p.strokeWidth = 2.25f * dp; c.drawPath(path, p)
    }
}
