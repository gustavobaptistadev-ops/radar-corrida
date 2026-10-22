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
 * RadarService: Intercepta e avalia corridas em microssegundos.
 *
 * Eficiência energética extrema:
 * - Só é acordado pelos pacotes da Uber, 99 e inDrive (definido em accessibility_service_config.xml).
 * - Sem OCR, sem rede, sem alocação contínua de memória.
 * - Single-pass node collection com limites rígidos de nós e profundidade.
 * - Overlay flutuante com TYPE_ACCESSIBILITY_OVERLAY (não requer SYSTEM_ALERT_WINDOW).
 * - Flags NOT_FOCUSABLE e NOT_TOUCHABLE: o selo é totalmente transparente ao toque,
 *   garantindo que o motorista nunca erre o clique no botão "Aceitar" do app oficial.
 */
class RadarService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private var badge: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private val hideAction = Runnable { badge?.visibility = View.GONE }
    private val sb = StringBuilder(1024)
    private var nodeCount = 0
    private var lastRun = 0L
    private var lastRide: Ride? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        initTtsIfNeeded()
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

        // Se nem tem menção a moeda, não é tela de chamada de viagem
        if (sb.indexOf("R$") < 0) return

        val ride = RideParser.parse(sb.toString()) ?: return
        if (ride == lastRide && badge?.visibility == View.VISIBLE) return
        lastRide = ride

        val verdict = RideEvaluator.evaluate(ride, Prefs.load(this))
        showBadge(ride, verdict)
    }

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

    private fun showBadge(r: Ride, v: Verdict) {
        val tv = badge ?: createBadge().also { badge = it }
        val sbText = StringBuilder()
            .append(v.signal.emoji)
            .append(" R$ ").append(v.perKm.br()).append("/km")
            .append(" (líq. ").append(v.netPerKm.br()).append(")")

        if (r.totalMin > 0) {
            sbText.append(" • R$ ").append(v.perHour.br(0)).append("/h")
        }

        sbText.append("\nLucro R$ ").append(v.profit.br())
            .append(" • ").append(r.totalKm.br(1)).append(" km")
        if (r.totalMin > 0) sbText.append(" • ").append(r.totalMin).append(" min")

        if (r.hasDynamic) sbText.append(" ⚡ Dinâmica")
        if (r.rating != null) sbText.append(" • ").append(r.rating.br(2)).append("★")

        if (v.reason.isNotEmpty()) {
            sbText.append("\n⚠️ ").append(v.reason)
        }

        tv.text = sbText
        (tv.background as GradientDrawable).setColor(v.signal.color)
        tv.visibility = View.VISIBLE

        handler.removeCallbacks(hideAction)
        handler.postDelayed(hideAction, SHOW_DURATION_MS)

        if (Prefs.speak(this)) {
            announce(v, r)
        }
    }

    private fun initTtsIfNeeded() {
        if (tts == null && Prefs.speak(this)) {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("pt", "BR")
                    ttsReady = true
                }
            }
        }
    }

    private fun announce(v: Verdict, r: Ride) {
        initTtsIfNeeded()
        if (!ttsReady && tts == null) return

        val phrase = buildString {
            append(v.signal.word).append(". ")
            append(v.perKm.br()).append(" por quilômetro. ")
            append("Lucro ").append(v.profit.br(0)).append(" reais. ")
            if (r.hasDynamic) append("Tarifa dinâmica. ")
            if (v.reason.isNotEmpty()) append(v.reason).append(".")
        }

        tts?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "ride_verdict")
    }

    private fun createBadge(): TextView {
        val dp = resources.displayMetrics.density
        val tv = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding((12 * dp).toInt(), (7 * dp).toInt(), (12 * dp).toInt(), (7 * dp).toInt())
            background = GradientDrawable().apply { cornerRadius = 14 * dp }
            visibility = View.GONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (8 * dp).toInt()
            y = (80 * dp).toInt()
        }

        wm.addView(tv, lp)
        return tv
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(hideAction)
        badge?.let { wm.removeView(it) }
        badge = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
        super.onDestroy()
    }

    private companion object {
        const val THROTTLE_MS = 250L
        const val SHOW_DURATION_MS = 14_000L
        const val MAX_DEPTH = 25
        const val MAX_NODES = 400
    }
}
