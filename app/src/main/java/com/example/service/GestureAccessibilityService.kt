package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
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
            "SCREENSHOT" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
                } else {
                    false
                }
            }
            "LOCK_SCREEN" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                } else {
                    false
                }
            }
            "NOTIFICATIONS" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            "QUICK_SETTINGS" -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            else -> false
        }
    }

    fun performClick(x: Float, y: Float): Boolean {
        Log.d("GestureAccessibility", "Clicking at: ($x, $y)")
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 50L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gestureDescription, null, null)
    }

    fun performLongPress(x: Float, y: Float): Boolean {
        Log.d("GestureAccessibility", "Long pressing at: ($x, $y)")
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 800L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gestureDescription, null, null)
    }

    fun performDrag(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        Log.d("GestureAccessibility", "Dragging from ($startX, $startY) to ($endX, $endY)")
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 500L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gestureDescription, null, null)
    }

    fun performSwipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        Log.d("GestureAccessibility", "Swiping from ($startX, $startY) to ($endX, $endY)")
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 250L)
        val gestureDescription = GestureDescription.Builder()
            .addStroke(stroke)
            .build()
        return dispatchGesture(gestureDescription, null, null)
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

    companion object {
        @Volatile
        var instance: GestureAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }
}
