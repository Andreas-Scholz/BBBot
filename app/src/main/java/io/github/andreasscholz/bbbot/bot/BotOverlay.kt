package io.github.andreasscholz.bbbot.bot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import io.github.andreasscholz.bbbot.R
import kotlin.math.abs

/**
 * The small panel shown on top of the game. It is an accessibility overlay, which needs no
 * permission beyond the service itself. The panel can be dragged by its text in case it covers
 * something.
 */
class BotOverlay(private val service: AccessibilityService, onToggle: () -> Unit, onCapture: () -> Unit) {

    private val windowManager = service.getSystemService(WindowManager::class.java)

    @SuppressLint("InflateParams") // A window root has no parent to take layout parameters from.
    private val root: View = LayoutInflater.from(service).inflate(R.layout.overlay, null)
    private val status: TextView = root.findViewById(R.id.overlay_status)
    private val toggle: ImageButton = root.findViewById(R.id.overlay_toggle)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        val density = service.resources.displayMetrics.density
        x = (START_X_DP * density).toInt()
        y = (START_Y_DP * density).toInt()
    }

    var isShown = false
        private set

    init {
        toggle.setOnClickListener { onToggle() }
        root.findViewById<View>(R.id.overlay_capture).setOnClickListener { onCapture() }
        makeDraggable(status, ViewConfiguration.get(service).scaledTouchSlop)
    }

    fun show() {
        if (isShown) return
        windowManager.addView(root, params)
        isShown = true
    }

    fun hide() {
        if (!isShown) return
        windowManager.removeView(root)
        isShown = false
    }

    fun setStatus(text: CharSequence) {
        status.text = text
    }

    /** Shows the button that stops the bot while it plays, and the one that starts it otherwise. */
    fun setPlaying(playing: Boolean) {
        toggle.setImageResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play)
        toggle.contentDescription = service.getString(if (playing) R.string.overlay_stop else R.string.overlay_start)
    }

    @SuppressLint("ClickableViewAccessibility") // The handle only moves the panel, there is nothing to click.
    private fun makeDraggable(handle: View, touchSlop: Int) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging && isShown) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        windowManager.updateViewLayout(root, params)
                    }
                }
            }
            true
        }
    }

    private companion object {
        const val START_X_DP = 8
        const val START_Y_DP = 40
    }
}
