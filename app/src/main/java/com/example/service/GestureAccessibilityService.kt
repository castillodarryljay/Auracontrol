package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class GestureAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("GestureAccessibility", "Service connected successfully")
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.d("GestureAccessibility", "Service unbound")
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: We only use this service to dispatch global navigation actions
    }

    override fun onInterrupt() {
        // No-op
    }

    fun performNavigation(actionId: String): Boolean {
        Log.d("GestureAccessibility", "Performing action: $actionId")
        return when (actionId) {
            "BACK" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "HOME" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "RECENTS" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            "SCROLL_UP" -> {
                simulateSwipe(scrollUp = true)
                true
            }
            "SCROLL_DOWN" -> {
                simulateSwipe(scrollUp = false)
                true
            }
            else -> false
        }
    }

    private fun simulateSwipe(scrollUp: Boolean) {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2f
        val endX = width / 2f

        val startY: Float
        val endY: Float

        if (scrollUp) {
            // Scroll Up = swipe down (top to bottom)
            startY = height * 0.3f
            endY = height * 0.7f
        } else {
            // Scroll Down = swipe up (bottom to top)
            startY = height * 0.7f
            endY = height * 0.3f
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 250L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("GestureAccessibility", "Swipe completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.d("GestureAccessibility", "Swipe cancelled")
            }
        }, null)
    }

    fun dispatchDragGesture(deltaX: Float, deltaY: Float): Boolean {
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY = height / 2f

        // Map normalized coordinate changes. Camera input delta: positive dy means hand moved down, which drags content down (scrolling up).
        // Let's add a robust sensitivity multiplier. 4.0f works wonderfully!
        val sensitivity = 4.0f
        
        // We invert camera horizontal flip if needed, but for vertical scroll dy is directly mapped
        val endX = (startX - deltaX * width * sensitivity).coerceIn(width * 0.05f, width * 0.95f)
        val endY = (startY + deltaY * height * sensitivity).coerceIn(height * 0.05f, height * 0.95f)

        // Prevent zero-length strokes
        val dxPx = endX - startX
        val dyPx = endY - startY
        if (Math.hypot(dxPx.toDouble(), dyPx.toDouble()) < 10.0) {
            return false
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        // We use a short duration of 100ms for fast real-time scroll dragging!
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        return dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d("GestureAccessibility", "Real-time scroll drag completed")
            }
        }, null)
    }

    companion object {
        @Volatile
        var instance: GestureAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }
}
