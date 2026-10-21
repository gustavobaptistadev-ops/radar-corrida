package com.motorista.radar

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.util.Locale

/**
 * Radar: lê o card da corrida e mostra um selo pequeno no canto.
 *
 * Por que é leve:
 *  - o sistema só envia eventos dos apps de motorista (filtro no XML);
 *  - throttle de 250 ms + descarte de cards repetidos;
 *  - lê só texto da árvore de acessibilidade (sem print, sem OCR, sem rede);
 *  - o selo é um único TextView reaproveitado, que não recebe toques.
 */
class RadarService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var badge: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hide = Runnable { badge?.visibility = View.GONE }
    private val sb = StringBuilder(1024)
    private var nodeCount = 0
    private var lastRun = 0L
    private var lastRide: Ride? = null
    private var tts: TextToSpeech? = null

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val now = SystemClock.uptimeMillis()
        if (now - lastRun < THROTTLE_MS) return
        lastRun = now

        val pkg = event.packageName ?: return
        val root = findRoot(pkg) ?: return
        sb.setLength(0)
        nodeCount = 0
        collect(root, 0)
        if (sb.indexOf("R$") < 0) return // não é card de corrida: sai já

        val ride = RideParser.parse(sb.toString()) ?: return
        if (ride == lastRide && badge?.visibility == View.VISIBLE) return
        lastRide = ride
        show(ride, RideEvaluator.evaluate(ride, Prefs.load(this)))
    }

    /** Janela do app que gerou o evento (pode ser o pop-up da Uber sobre outro app). */
    private fun findRoot(pkg: CharSequence): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName == pkg) return it }
        for (w in windows) {
            val r = w.root ?: continue
            if (r.packageName == pkg) return r
        }
        return null
    }

    private fun collect(n: AccessibilityNodeInfo, depth: Int) {
        if (depth > MAX_DEPTH || nodeCount++ > MAX_NODES) return
        n.text?.let { sb.append(it).append(' ') }
        n.contentDescription?.let { sb.append(it).append(' ') }
        for (i in 0 until n.childCount) {
            val child = n.getChild(i) ?: continue
            collect(child, depth + 1)
        }
    }

    private fun show(r: Ride, v: Verdict) {
        val tv = badge ?: createBadge().also { badge = it }
        val sb2 = StringBuilder()
            .append(v.signal.emoji).append(" R$ ").append(v.perKm.br()).append("/km")
        if (r.totalMin > 0) sb2.append("  •  R$ ").append(v.perHour.br(0)).append("/h")
        sb2.append("\nLucro R$ ").append(v.profit.br())
            .append(" • ").append(r.totalKm.br(1)).append(" km")
        if (r.totalMin > 0) sb2.append(" • ").append(r.totalMin).append(" min")
        if (v.reason.isNotEmpty()) sb2.append("\n").append(v.reason)
        tv.text = sb2
        (tv.background as GradientDrawable).setColor(v.signal.color)
        tv.visibility = View.VISIBLE
        handler.removeCallbacks(hide)
        handler.postDelayed(hide, SHOW_MS)

        if (Prefs.speak(this)) speak(v)
    }

    private fun speak(v: Verdict) {
        val phrase = "${v.signal.word}. ${v.perKm.br()} por quilômetro. Lucro ${v.profit.br(0)} reais"
        val t = tts
        if (t == null) {
            tts = TextToSpeech(this) { ok ->
                if (ok == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("pt", "BR")
                    tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "r")
                }
            }
        } else {
            t.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "r")
        }
    }

    private fun createBadge(): TextView {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding((10 * dp).toInt(), (6 * dp).toInt(), (10 * dp).toInt(), (6 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 12 * dp }
            visibility = View.GONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, // sem permissão extra
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or // nunca atrapalha o "Aceitar"
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * dp).toInt()
            y = (90 * dp).toInt()
        }
        wm.addView(tv, lp)
        return tv
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(hide)
        badge?.let { wm.removeView(it) }
        badge = null
        tts?.shutdown()
        super.onDestroy()
    }

    private companion object {
        const val THROTTLE_MS = 250L
        const val SHOW_MS = 15_000L
        const val MAX_DEPTH = 25
        const val MAX_NODES = 400
    }
}
