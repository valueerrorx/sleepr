package com.valueerrorx.sleepr

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.StateListAnimator
import android.animation.TimeInterpolator
import android.app.Activity
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.TextView

class MainActivity : Activity() {
    private val BG = 0xFF10161B.toInt()
    private val SURFACE = 0xFF172028.toInt()
    private val FG = 0xFFDCE3E8.toInt()
    private val MUTED = 0xFF8E9BA6.toInt()
    private val LINE = 0xFF27313A.toInt()
    private val TRACK = 0xFF29343D.toInt()
    private val ACCENT = 0xFF86B7CC.toInt()
    private val ON_ACCENT = 0xFF0D1A20.toInt()

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var play: LinearLayout
    private lateinit var playIcon: ImageView
    private lateinit var playLabel: TextView
    private var playActive: Boolean? = null
    private val glow = GradientDrawable().apply { gradientType = GradientDrawable.RADIAL_GRADIENT }
    private lateinit var status: TextView
    private lateinit var sub: TextView
    private lateinit var spectrum: SpectrumView
    private val seeks = arrayOfNulls<SeekBar>(5)
    private val values = arrayOfNulls<TextView>(5)
    private val waveBtns = arrayOfNulls<ImageButton>(5)
    private val waveSeeks = arrayOfNulls<SeekBar>(5)
    private val waveVals = arrayOfNulls<TextView>(5)
    private val waveBoxes = arrayOfNulls<View>(5)
    private var touching: SeekBar? = null
    private lateinit var masterSeek: SeekBar
    private lateinit var masterVal: TextView
    private lateinit var toneSeek: SeekBar
    private lateinit var toneVal: TextView
    private val timerChips = mutableListOf<Pair<Int, TextView>>()
    private var syncing = false

    private val Int.dp get() = (this * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Mixer.load(this)
        window.statusBarColor = BG
        window.navigationBarColor = BG

        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18.dp, 22.dp, if (land) 24.dp else 18.dp, 24.dp)
        }
        fun add(v: View, top: Int = 18) {
            col.addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top.dp })
        }

        val title = text("sleepr", 30f, FG, bold = true)
        val intro = text("Farbiges Rauschen zum Abschirmen und Runterkommen. Mischen, Timer setzen, laufen lassen.", 15f, MUTED)

        // Player: Status über der Hauptaktion (Pille unten mittig wie in den aapps)
        status = text("Bereit", 17f, FG, bold = true).apply { gravity = Gravity.CENTER }
        sub = text("", 13f, MUTED).apply { gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        play = pill()
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = LayerDrawable(arrayOf(ColorDrawable(BG), glow))
            addView(status, lp(0)); addView(sub, lp(2))
            addView(play, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 14.dp })
            // Glow folgt der Pillenmitte
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                if (v.height > 0) glow.setGradientCenter(0.5f, (play.top + play.height / 2f) / v.height)
            }
        }

        if (!land) { add(title, 0); add(intro, 4) }

        // Spektrum
        spectrum = SpectrumView(this).apply { accent = ACCENT; grid = LINE; muted = MUTED }
        add(card().apply {
            addView(row(label("Spektrum der Mischung"), text("Leistung/Hz · dB", 11f, MUTED)))
            addView(spectrum, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 150.dp).apply { topMargin = 8.dp })
        })

        // Presets
        add(label("Mischungen"), 22)
        add(chipRow(Mixer.PRESETS.map { p -> chip(p.name) { Mixer.applyPreset(p); changed() } }), 8)

        // Rauschfarben
        add(card().apply {
            addView(label("Rauschfarben"))
            Mixer.COLORS.forEachIndexed { i, c ->
                val v = text("", 13f, FG).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.END }
                values[i] = v
                val head = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    addView(View(this@MainActivity).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c.color) } },
                        LinearLayout.LayoutParams(12.dp, 12.dp).apply { rightMargin = 10.dp })
                    val names = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
                            addView(text(c.name, 16f, FG, bold = true))
                            addView(text("  " + c.slope, 11f, MUTED).apply { typeface = Typeface.MONOSPACE })
                        })
                        addView(text(c.hint, 12f, MUTED))
                    }
                    addView(names, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val wb = ImageButton(this@MainActivity).apply {
                        setImageResource(R.drawable.ic_wave)
                        scaleType = android.widget.ImageView.ScaleType.CENTER
                        setOnClickListener {
                            Mixer.waves[i] = !Mixer.waves[i]
                            syncWaves(); changed()
                        }
                    }
                    waveBtns[i] = wb
                    addView(wb, LinearLayout.LayoutParams(40.dp, 32.dp).apply { leftMargin = 6.dp })
                    addView(v, LinearLayout.LayoutParams(48.dp, ViewGroup.LayoutParams.WRAP_CONTENT))
                }
                val tint = if (i == 0) 0xFFC9D2D8.toInt() else c.color
                addView(head, lp(if (i == 0) 10 else 14))
                seeks[i] = seek(tint) { Mixer.levels[i] = it }
                addView(seeks[i], lp(2))

                // Wellenlänge, nur sichtbar bei aktiven Wellen
                val sv = text("", 12f, MUTED).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.END }
                waveVals[i] = sv
                val ss = seek(tint, Mixer.WAVE_MAX - Mixer.WAVE_MIN) { Mixer.waveSec[i] = Mixer.WAVE_MIN + it }
                ss.alpha = 0.85f
                waveSeeks[i] = ss
                val box = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(22.dp, 0, 0, 0)
                    addView(row(text("∿ Wellenlänge  kurz ↔ lang", 12f, MUTED), sv))
                    addView(ss)
                }
                waveBoxes[i] = box
                addView(box, lp(0))
            }
        })

        // Lautstärke / Höhen
        masterVal = text("", 13f, FG).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.END }
        toneVal = text("", 13f, FG).apply { typeface = Typeface.MONOSPACE; gravity = Gravity.END }
        masterSeek = seek(ACCENT) { Mixer.master = it }
        toneSeek = seek(ACCENT) { Mixer.tone = it }
        add(card().apply {
            addView(row(text("Lautstärke", 16f, FG, bold = true), masterVal))
            addView(masterSeek, lp(2))
            addView(row(text("Höhen", 16f, FG, bold = true), toneVal), lp(14))
            addView(text("links dumpfer, rechts heller", 12f, MUTED))
            addView(toneSeek, lp(2))
        })

        // Timer
        add(label("Timer mit sanftem Ausblenden"), 22)
        add(chipRow(Mixer.TIMERS.map { t ->
            chip(if (t == 0) "Aus" else "$t min") {
                Mixer.timer = t; Mixer.save(this); syncTimers()
                NoiseService.engine?.restartTimer(); render()
                NoiseService.send(this, NoiseService.ACTION_UPDATE)
            }.also { timerChips += t to it }
        }), 8)

        add(text("Leise reicht: Störgeräusche sollen verschwimmen, lauter als ein ruhiges Gespräch muss es nicht sein. " +
                "Bei Kindern lieber über Lautsprecher als über Kopfhörer.\n\n" +
                "Grau und Grün sind Näherungen: Grau gleicht die Lautheitswahrnehmung des Ohrs grob aus, Grün betont den mittleren Bereich um 500 Hz.",
            13f, MUTED), 26)

        val scroll = ScrollView(this).apply { setBackgroundColor(BG); isFillViewport = true; addView(col) }
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        setContentView(if (land) {
            // Querformat: Kopf, Status und Pille links, Inhalt rechts
            bar.setPadding(0, 0, 0, 16.dp)
            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(24.dp, 22.dp, 12.dp, 0)
                addView(title); addView(intro, lp(4))
                addView(Space(this@MainActivity), LinearLayout.LayoutParams(0, 18.dp, 1f))
                addView(bar, lp(0))
            }
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; setBackgroundColor(BG)
                addView(ScrollView(this@MainActivity).apply { isFillViewport = true; addView(left) }, LinearLayout.LayoutParams(0, match, 0.42f))
                addView(scroll, LinearLayout.LayoutParams(0, match, 0.58f))
            }
        } else {
            // Hochformat: feste Leiste unten, Inhalt scrollt darüber und blendet weich aus
            bar.setPadding(18.dp, 4.dp, 18.dp, 16.dp)
            val fade = View(this).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG and 0x00FFFFFF, BG)) }
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setBackgroundColor(BG)
                addView(FrameLayout(this@MainActivity).apply {
                    addView(scroll)
                    addView(fade, FrameLayout.LayoutParams(match, 24.dp, Gravity.BOTTOM))
                }, LinearLayout.LayoutParams(match, 0, 1f))
                addView(bar, LinearLayout.LayoutParams(match, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        })
        syncInputs(); syncTimers(); render()
    }

    override fun onResume() { super.onResume(); syncInputs(); syncTimers(); tick.run() }
    override fun onPause() { super.onPause(); ui.removeCallbacks(tick) }

    private val tick = object : Runnable {
        private var n = 0
        override fun run() {
            showLive()
            if (n++ % 5 == 0) render()
            ui.postDelayed(this, 100)
        }
    }

    private fun toggle() {
        if (NoiseService.playing) { NoiseService.send(this, NoiseService.ACTION_STOP); ui.postDelayed({ render() }, 600); return }
        val prefs = getSharedPreferences("app", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notifAsked", false) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            prefs.edit().putBoolean("notifAsked", true).apply()
            explainNotifications() // erst erklären, dann Systemabfrage, dann abspielen
            return
        }
        startPlay()
    }

    private fun startPlay() {
        Mixer.save(this)
        NoiseService.send(this, NoiseService.ACTION_PLAY)
        ui.postDelayed({ render() }, 150)
    }

    private fun explainNotifications() {
        AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle("Benachrichtigung erlauben?")
            .setMessage("Damit das Rauschen auch bei gesperrtem Bildschirm weiterläuft, verlangt Android eine dauerhafte Benachrichtigung, solange sleepr spielt.\n\n" +
                "Darüber kannst du die Wiedergabe direkt vom Sperrbildschirm aus stoppen.\n\n" +
                "sleepr schickt keine anderen Benachrichtigungen und hat keinen Internetzugriff.\n\n" +
                "Ohne Erlaubnis läuft der Ton trotzdem, nur der Stopp-Knopf in der Benachrichtigung fehlt.")
            .setPositiveButton("Weiter") { _, _ -> requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
            .setNegativeButton("Ohne Benachrichtigung") { _, _ -> startPlay() }
            .setOnCancelListener { startPlay() }
            .show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) startPlay() // spielt unabhängig von der Antwort
    }

    private fun changed() {
        syncInputs(); Mixer.save(this); render()
        NoiseService.send(this, NoiseService.ACTION_UPDATE)
    }

    private fun syncInputs() {
        syncing = true
        for (i in 0..4) {
            seeks[i]?.progress = Mixer.levels[i]; values[i]?.text = Mixer.levels[i].toString()
            waveSeeks[i]?.progress = Mixer.waveSec[i] - Mixer.WAVE_MIN
            waveVals[i]?.text = "~${Mixer.waveSec[i]} s"
        }
        syncWaves()
        masterSeek.progress = Mixer.master; masterVal.text = "${Mixer.master} %"
        toneSeek.progress = Mixer.tone; toneVal.text = Mixer.fmtHz(Mixer.toneHz())
        syncing = false
        spectrum.invalidate()
    }

    private fun syncWaves() {
        for (i in 0..4) {
            val on = Mixer.waves[i]
            val c = if (i == 0) 0xFFC9D2D8.toInt() else Mixer.COLORS[i].color
            waveBtns[i]?.apply {
                background = GradientDrawable().apply {
                    cornerRadius = 10.dp.toFloat()
                    setColor(if (on) c else SURFACE); setStroke(1.dp, if (on) c else LINE)
                }
                imageTintList = ColorStateList.valueOf(if (on) BG else MUTED)
                contentDescription = "Wellen ${Mixer.COLORS[i].name} " + if (on) "aus" else "ein"
            }
            waveBoxes[i]?.visibility = if (on) View.VISIBLE else View.GONE
        }
    }

    /** Regler folgen den Wellen sichtbar, solange niemand sie anfasst */
    private fun showLive() {
        val e = NoiseService.engine
        val live = if (NoiseService.playing && e != null) e.live else null
        for (i in 0..4) {
            val s = seeks[i] ?: continue
            if (s === touching) continue
            if (live != null && Mixer.waves[i] && Mixer.levels[i] > 0) {
                val l = live[i].toInt()
                s.progress = l; values[i]?.text = "~$l"
            } else if (s.progress != Mixer.levels[i]) {
                s.progress = Mixer.levels[i]; values[i]?.text = Mixer.levels[i].toString()
            }
        }
    }

    private fun syncTimers() {
        for ((t, v) in timerChips) styleChip(v, t == Mixer.timer)
    }

    private fun render() {
        val on = NoiseService.playing
        val e = NoiseService.engine
        stylePill(!on)
        status.text = when {
            !on -> "Bereit"
            e != null && e.remainingMs() >= 0 -> (if (e.fading()) "Blendet aus · " else "Läuft · noch ") + fmtT(e.remainingMs())
            else -> "Läuft"
        }
        sub.text = Mixer.summary() + if (Mixer.timer > 0 && !on) " · Timer ${Mixer.timer} min" else ""
    }

    private fun fmtT(ms: Long): String {
        val s = (ms + 999) / 1000; val h = s / 3600; val m = s % 3600 / 60; val x = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
    }

    // ---------- kleine UI-Helfer ----------
    private fun text(s: String, size: Float, color: Int, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setLineSpacing(0f, 1.15f)
    }

    // Die eine Hauptaktion: Pille, gefüllt mit Akzent solange bereit (wie AappsPillButton)
    private fun pill(): LinearLayout {
        val fs = resources.configuration.fontScale.coerceAtMost(2f)
        playIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        playLabel = text("", 13.8f, FG, bold = true).apply { letterSpacing = 0.14f; importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            minimumWidth = 176.dp; minimumHeight = 52.dp
            setPadding(24.dp, 12.dp, 24.dp, 12.dp)
            isClickable = true; isFocusable = true
            val icon = (20 * fs * resources.displayMetrics.density).toInt() // wächst mit der Schrift
            addView(playIcon, LinearLayout.LayoutParams(icon, icon).apply { rightMargin = 10.dp })
            addView(playLabel)
            setOnClickListener { toggle() }
            // Feder statt Ripple: kurz eindrücken, federnd zurück
            fun scale(s: Float, ms: Long, ip: TimeInterpolator) = ObjectAnimator.ofPropertyValuesHolder(this,
                PropertyValuesHolder.ofFloat(View.SCALE_X, s), PropertyValuesHolder.ofFloat(View.SCALE_Y, s)).apply { duration = ms; interpolator = ip }
            stateListAnimator = StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_pressed), scale(0.95f, 90, DecelerateInterpolator()))
                addState(intArrayOf(), scale(1f, 260, OvershootInterpolator(3f)))
            }
            setOnFocusChangeListener { _, _ -> playActive = null; render() }
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info); info.className = Button::class.java.name
                }
            }
        }
    }

    private fun stylePill(active: Boolean) {
        if (playActive == active) return
        playActive = active
        val fg = if (active) ON_ACCENT else FG
        play.background = GradientDrawable().apply {
            cornerRadius = 100.dp.toFloat()
            setColor(if (active) ACCENT else SURFACE)
            setStroke(if (play.isFocused) 2.dp else 1.dp, if (play.isFocused) FG else if (active) ACCENT else LINE)
        }
        playIcon.setImageResource(if (active) R.drawable.ic_play else R.drawable.ic_stop)
        playIcon.imageTintList = ColorStateList.valueOf(fg)
        playLabel.setTextColor(fg)
        playLabel.text = if (active) "START" else "STOP"
        play.contentDescription = if (active) "Abspielen" else "Stoppen"
        // weicher Halo nur im Bereit-Zustand
        glow.colors = if (active) intArrayOf(alpha(ACCENT, 0.28f), alpha(ACCENT, 0.07f), alpha(ACCENT, 0f)) else intArrayOf(0, 0)
        glow.gradientRadius = 72.dp.toFloat()
    }

    private fun alpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).toInt() shl 24)

    private fun label(s: String) = text(s.uppercase(), 11f, MUTED, bold = true).apply { letterSpacing = 0.09f }

    private fun lp(top: Int) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = top.dp }

    private fun row(a: View, b: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(a, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(b)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        background = GradientDrawable().apply { setColor(SURFACE); setStroke(1.dp, LINE); cornerRadius = 14.dp.toFloat() }
    }

    private fun seek(color: Int, range: Int = 100, onChange: (Int) -> Unit) = SeekBar(this).apply {
        max = range
        progressTintList = ColorStateList.valueOf(color)
        thumbTintList = ColorStateList.valueOf(color)
        progressBackgroundTintList = ColorStateList.valueOf(TRACK)
        setPadding(12.dp, 10.dp, 12.dp, 10.dp)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) { if (fromUser && !syncing) { onChange(p); changed() } }
            override fun onStartTrackingTouch(s: SeekBar) { touching = s }
            override fun onStopTrackingTouch(s: SeekBar) { touching = null; syncInputs() }
        })
    }

    private fun chip(s: String, onClick: () -> Unit) = text(s, 14f, FG).apply {
        setPadding(16.dp, 10.dp, 16.dp, 10.dp)
        styleChip(this, false)
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
    }

    private fun styleChip(v: TextView, on: Boolean) {
        v.background = GradientDrawable().apply {
            cornerRadius = 100.dp.toFloat()
            setColor(if (on) ACCENT else SURFACE); setStroke(1.dp, if (on) ACCENT else LINE)
        }
        v.setTextColor(if (on) ON_ACCENT else FG)
    }

    private fun chipRow(chips: List<View>) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            chips.forEachIndexed { i, c -> addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) leftMargin = 8.dp }) }
        })
    }
}
