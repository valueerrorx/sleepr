package com.valueerrorx.sleepr

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.tanh

/** Echtzeit-Rauschgenerator: eigener Audio-Thread schreibt direkt in einen AudioTrack */
class NoiseEngine(private val onFinished: () -> Unit) {
    @Volatile var running = false; private set
    @Volatile private var stopReq = false
    @Volatile private var timerReset = true
    @Volatile private var frames = 0L
    @Volatile private var endFrame = 0L      // 0 = kein Timer
    @Volatile private var fadeStart = 0L

    fun start() {
        if (running) return
        running = true
        Thread(::loop, "sleepr-audio").start()
    }

    fun stop() { stopReq = true }
    fun restartTimer() { timerReset = true }

    /** Restzeit in ms, -1 ohne Timer */
    fun remainingMs(): Long = if (endFrame == 0L) -1 else max(0L, (endFrame - frames) * 1000 / FS)
    fun fading() = endFrame != 0L && frames >= fadeStart

    private fun softClip(x: Double): Double { // schützt vor Übersteuern, linear bis 0.7
        val a = abs(x)
        return if (a <= 0.7) x else sign(x) * (0.7 + 0.3 * tanh((a - 0.7) / 0.3))
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val k = NoiseChannel.K
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val fmt = AudioFormat.Builder().setSampleRate(FS)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val minBuf = AudioTrack.getMinBufferSize(FS, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val track = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt)
            .setBufferSizeInBytes(max(minBuf * 4, FS * 8 * 3 / 10)) // ≥ 300 ms Puffer
            .setTransferMode(AudioTrack.MODE_STREAM).build()

        val gen = arrayOf(NoiseChannel(System.nanoTime()), NoiseChannel(System.nanoTime() * 31 + 7))
        val tone = arrayOf(Biquad(), Biquad())
        val n = 1024
        val out = FloatArray(n * 2)
        val v = DoubleArray(5)
        val g = DoubleArray(5); val tg = DoubleArray(5)
        var m = 0.0
        val smooth = 1.0 - exp(-1.0 / (0.06 * FS))
        var lastTone = -1.0
        val fadeIn = 1.5 * FS
        val fadeOutLen = (0.35 * FS).toLong()
        var fadeOutFrom = -1L
        var f0 = 0L

        try {
            track.play()
            while (true) {
                if (timerReset) {
                    timerReset = false
                    val t = Mixer.timer
                    if (t > 0) {
                        val total = t * 60L * FS
                        val fd = min(120L, t * 12L) * FS
                        endFrame = f0 + total; fadeStart = endFrame - fd
                    } else endFrame = 0L
                }
                val hz = min(Mixer.toneHz(), 0.45 * FS)
                if (hz != lastTone) { for (b in tone) b.set(FSpec(FType.LOWPASS, hz)); lastTone = hz }
                for (i in 0..4) tg[i] = Mixer.gain(Mixer.levels[i]) * k[i]
                val tm = Mixer.gain(Mixer.master)
                if (stopReq && fadeOutFrom < 0) fadeOutFrom = f0
                val end = endFrame; val fs0 = fadeStart

                for (j in 0 until n) {
                    for (i in 0..4) g[i] += (tg[i] - g[i]) * smooth
                    m += (tm - m) * smooth
                    val f = f0 + j
                    var env = min(1.0, f / fadeIn)
                    if (end > 0) env *= when {
                        f >= end -> 0.0
                        f >= fs0 -> (end - f).toDouble() / (end - fs0)
                        else -> 1.0
                    }
                    if (fadeOutFrom >= 0) env *= max(0.0, 1.0 - (f - fadeOutFrom).toDouble() / fadeOutLen)
                    for (c in 0..1) {
                        gen[c].next(v)
                        var s = 0.0
                        for (i in 0..4) s += g[i] * v[i]
                        s = tone[c].process(s)
                        out[2 * j + c] = (softClip(s * m) * env).toFloat()
                    }
                }
                f0 += n
                track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
                frames = f0
                val done = (fadeOutFrom >= 0 && f0 - fadeOutFrom >= fadeOutLen) || (end > 0 && f0 >= end)
                if (done) break
            }
            out.fill(0f) // Puffer mit Stille auslaufen lassen, damit nichts knackt
            repeat(15) { track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING) }
        } finally {
            try { track.pause(); track.flush() } catch (_: Exception) {}
            track.release()
            running = false
        }
        onFinished()
    }
}
