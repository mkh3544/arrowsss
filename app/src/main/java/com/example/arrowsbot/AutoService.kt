package com.example.arrowsbot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.Toast

class AutoService : AccessibilityService() {
    companion object {
        @Volatile var instance: AutoService? = null
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var idle = 0
    private var windowManager: WindowManager? = null
    private var floatBtn: Button? = null
    private var params: WindowManager.LayoutParams? = null

    override fun onServiceConnected() {
        instance = this
        handler.post { showFloatButton() }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() { stopBot() }
    override fun onDestroy() {
        stopBot()
        removeFloatButton()
        instance = null
        super.onDestroy()
    }

    fun startBot() {
        if (running) return
        running = true
        idle = 0
        updateBtn()
        handler.postDelayed({ cycle() }, 600)
        Toast.makeText(this, "START", Toast.LENGTH_SHORT).show()
    }
    fun stopBot() {
        running = false
        handler.removeCallbacksAndMessages(null)
        updateBtn()
    }

    private fun cycle() {
        if (!running) return
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hb = result.hardwareBuffer
                        val bmp = try {
                            Bitmap.wrapHardwareBuffer(hb, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } catch (_: Exception) { null }
                        try { hb.close() } catch (_: Exception) {}
                        if (bmp == null) {
                            handler.postDelayed({ cycle() }, 1200)
                            return
                        }
                        val taps = try { Solver.findTaps(bmp) } catch (_: Exception) { emptyList() }
                        bmp.recycle()
                        if (taps.isEmpty()) {
                            idle++
                            if (idle >= 2) {
                                val dm = resources.displayMetrics
                                tap(dm.widthPixels * Config.CONT_X, dm.heightPixels * Config.CONT_Y)
                                idle = 0
                            }
                            handler.postDelayed({ cycle() }, Config.CYCLE_DELAY)
                        } else {
                            idle = 0
                            taps.forEachIndexed { i, p ->
                                handler.postDelayed({
                                    if (running) tap(p.x.toFloat(), p.y.toFloat())
                                }, i * Config.TAP_DELAY)
                            }
                            handler.postDelayed({ cycle() },
                                taps.size * Config.TAP_DELAY + Config.CYCLE_DELAY)
                        }
                    }
                    override fun onFailure(errorCode: Int) {
                        handler.postDelayed({ cycle() }, 1500)
                    }
                })
        } catch (_: Exception) {
            handler.postDelayed({ cycle() }, 1500)
        }
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 40)
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, null)
    }

    private fun showFloatButton() {
        if (floatBtn != null || !Settings.canDrawOverlays(this)) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 58f,
            resources.displayMetrics).toInt()
        floatBtn = Button(this).apply {
            text = "START"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = true
            background = GradientDrawable().apply {
                cornerRadius = size / 2f
                setColor(0xFF2E7D32.toInt())
            }
            setOnClickListener {
                if (running) {
                    stopBot()
                    Toast.makeText(this@AutoService, "STOP", Toast.LENGTH_SHORT).show()
                } else startBot()
            }
        }
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        floatBtn?.setOnTouchListener { view, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params?.x ?: 0
                    iy = params?.y ?: 0
                    tx = e.rawX
                    ty = e.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let { lp ->
                        lp.x = ix + (e.rawX - tx).toInt()
                        lp.y = iy + (e.rawY - ty).toInt()
                        windowManager?.updateViewLayout(view, lp)
                    }
                    true
                }
                else -> false
            }
        }
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 280
        }
        try { windowManager?.addView(floatBtn, params) } catch (_: Exception) { floatBtn = null }
    }

    private fun removeFloatButton() {
        try { floatBtn?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        floatBtn = null
    }
    private fun updateBtn() {
        handler.post {
            floatBtn?.let {
                if (running) {
                    it.text = "STOP"
                    (it.background as? GradientDrawable)?.setColor(0xFFC62828.toInt())
                } else {
                    it.text = "START"
                    (it.background as? GradientDrawable)?.setColor(0xFF2E7D32.toInt())
                }
            }
        }
    }
}
