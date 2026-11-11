package com.valueerrorx.sleepr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

const val FS = 48000

enum class FType { LOWPASS, HIGHPASS, PEAKING, LOWSHELF, HIGHSHELF }

data class FSpec(val type: FType, val freq: Double, val q: Double = 0.707, val gainDb: Double = 0.0)

// Filterketten: Grau auf Weiß (gehörangepasst), Grün auf Rosa (Mitte betont)
val GREY_FILTERS = listOf(
    FSpec(FType.LOWSHELF, 250.0, gainDb = 10.0),
    FSpec(FType.PEAKING, 3500.0, 1.0, -6.0),
    FSpec(FType.HIGHSHELF, 10000.0, gainDb = 4.0)
)
val GREEN_FILTERS = listOf(
    FSpec(FType.HIGHPASS, 60.0, 0.7),
    FSpec(FType.PEAKING, 500.0, 0.8, 10.0),
    FSpec(FType.LOWPASS, 5000.0, 0.7)
)

/** RBJ-Biquad (Transposed Direct Form II) */
class Biquad(spec: FSpec? = null) {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var z1 = 0.0; private var z2 = 0.0

    init { if (spec != null) set(spec) }

    fun set(s: FSpec, fs: Int = FS) {
        val w0 = 2 * PI * s.freq / fs
        val c = cos(w0); val sn = sin(w0)
        val a = 10.0.pow(s.gainDb / 40)
        val alpha = sn / (2 * s.q)
        val shelfAlpha = sn / 2 * sqrt(2.0) // Shelf-Slope S = 1
        val sa = 2 * sqrt(a) * shelfAlpha
        val n0: Double; val n1: Double; val n2: Double; val d0: Double; val d1: Double; val d2: Double
        when (s.type) {
            FType.LOWPASS -> { n0 = (1 - c) / 2; n1 = 1 - c; n2 = (1 - c) / 2; d0 = 1 + alpha; d1 = -2 * c; d2 = 1 - alpha }
            FType.HIGHPASS -> { n0 = (1 + c) / 2; n1 = -(1 + c); n2 = (1 + c) / 2; d0 = 1 + alpha; d1 = -2 * c; d2 = 1 - alpha }
            FType.PEAKING -> { n0 = 1 + alpha * a; n1 = -2 * c; n2 = 1 - alpha * a; d0 = 1 + alpha / a; d1 = -2 * c; d2 = 1 - alpha / a }
            FType.LOWSHELF -> {
                n0 = a * ((a + 1) - (a - 1) * c + sa); n1 = 2 * a * ((a - 1) - (a + 1) * c); n2 = a * ((a + 1) - (a - 1) * c - sa)
                d0 = (a + 1) + (a - 1) * c + sa; d1 = -2 * ((a - 1) + (a + 1) * c); d2 = (a + 1) + (a - 1) * c - sa
            }
            FType.HIGHSHELF -> {
                n0 = a * ((a + 1) + (a - 1) * c + sa); n1 = -2 * a * ((a - 1) + (a + 1) * c); n2 = a * ((a + 1) + (a - 1) * c - sa)
                d0 = (a + 1) - (a - 1) * c + sa; d1 = 2 * ((a - 1) - (a + 1) * c); d2 = (a + 1) - (a - 1) * c - sa
            }
        }
        b0 = n0 / d0; b1 = n1 / d0; b2 = n2 / d0; a1 = d1 / d0; a2 = d2 / d0
    }

    fun process(x: Double): Double {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    /** |H(f)|² für die Spektrumanzeige */
    fun magSq(f: Double, fs: Int = FS): Double {
        val w = 2 * PI * f / fs
        val c1 = cos(w); val s1 = sin(w); val c2 = cos(2 * w); val s2 = sin(2 * w)
        val nr = b0 + b1 * c1 + b2 * c2; val ni = -(b1 * s1 + b2 * s2)
        val dr = 1 + a1 * c1 + a2 * c2; val di = -(a1 * s1 + a2 * s2)
        return (nr * nr + ni * ni) / (dr * dr + di * di)
    }
}

/** Erzeugt pro Sample alle fünf Farben (roh, unnormiert) für einen Kanal */
class NoiseChannel(seed: Long) {
    private var s = seed or 1L
    private var p0 = 0.0; private var p1 = 0.0; private var p2 = 0.0; private var p3 = 0.0
    private var p4 = 0.0; private var p5 = 0.0; private var p6 = 0.0
    private var br = 0.0
    private val grey = GREY_FILTERS.map { Biquad(it) }
    private val green = GREEN_FILTERS.map { Biquad(it) }

    private fun white(): Double { // xorshift64
        s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17)
        return (s ushr 11).toDouble() / (1L shl 53).toDouble() * 2.0 - 1.0
    }

    fun next(v: DoubleArray) {
        val w = white()
        p0 = 0.99886 * p0 + w * 0.0555179; p1 = 0.99332 * p1 + w * 0.0750759
        p2 = 0.96900 * p2 + w * 0.1538520; p3 = 0.86650 * p3 + w * 0.3104856
        p4 = 0.55000 * p4 + w * 0.5329522; p5 = -0.7616 * p5 - w * 0.0168980
        val pink = p0 + p1 + p2 + p3 + p4 + p5 + p6 + w * 0.5362
        p6 = w * 0.115926
        br = (br + 0.02 * w) / 1.02
        var g = w; for (f in grey) g = f.process(g)
        var gn = pink; for (f in green) gn = f.process(gn)
        v[0] = w; v[1] = pink; v[2] = br; v[3] = g; v[4] = gn
    }

    companion object {
        /** Faktoren, die jede Farbe auf denselben RMS-Pegel bringen */
        val K: DoubleArray by lazy {
            val ch = NoiseChannel(0x5EED_CAFEL); val v = DoubleArray(5); val sum = DoubleArray(5)
            repeat(FS) { ch.next(v) }
            val n = FS * 3
            repeat(n) { ch.next(v); for (i in 0..4) sum[i] += v[i] * v[i] }
            DoubleArray(5) { 0.12 / sqrt(sum[it] / n) }
        }
    }
}
