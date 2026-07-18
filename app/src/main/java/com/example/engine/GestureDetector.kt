package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import kotlin.math.abs
import kotlin.math.sqrt

class GestureDetector(
    private val context: Context,
    private val listener: GestureListener
) : ImageAnalysis.Analyzer {

    interface GestureListener {
        fun onMotionFrame(
            gridWidth: Int,
            gridHeight: Int,
            motionGrid: FloatArray, // 0.0 to 1.0 intensity for each cell
            centroidX: Float?,      // Null if no hand, 0.0 to 1.0 otherwise
            centroidY: Float?,
            motionDensity: Float,   // Percentage of grid with motion (0.0 to 1.0)
            handDetected: Boolean,  // True if a hand is detected
            handSkeleton: FloatArray? // 21-landmark hand skeleton (x, y) or null
        )
        fun onGestureDetected(gestureId: String)
        fun onPinchStateChanged(isPinch: Boolean, pinchX: Float, pinchY: Float)
    }

    private val gridWidth = 32
    private val gridHeight = 24
    private var lastHandSkeleton: FloatArray? = null

    // Track centroid history for gesture detection
    private val centroidHistory = mutableListOf<Pair<Float, Float>>()
    private val maxHistorySize = 15
    private var consecutiveMotionFrames = 0
    private var consecutiveNoMotionFrames = 0

    // Cooldown to prevent multi-triggering
    private var lastGestureTime = 0L
    private val gestureCooldownMs = 800L

    private var handLandmarker: HandLandmarker? = null

    private fun initDetector() {
        if (handLandmarker != null) return
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()
            val options = HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(0.40f) // Keep it sensitive for fast offline tracking
                .setMinTrackingConfidence(0.40f)
                .setNumHands(1) // Only track 1 hand for gesture actions
                .setRunningMode(RunningMode.IMAGE)
                .build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.d("GestureDetector", "MediaPipe HandLandmarker initialized successfully!")
        } catch (e: Exception) {
            Log.e("GestureDetector", "Failed to initialize HandLandmarker", e)
        }
    }

    override fun analyze(image: ImageProxy) {
        try {
            initDetector()
            val landmarker = handLandmarker
            if (landmarker == null) {
                // Fallback: Notify listener that nothing is detected
                listener.onPinchStateChanged(false, 0f, 0f)
                listener.onMotionFrame(
                    gridWidth, gridHeight, FloatArray(gridWidth * gridHeight),
                    null, null, 0f, false, null
                )
                return
            }

            // Convert ImageProxy to Bitmap using CameraX 1.3+ built-in utility
            val bitmap = image.toBitmap()

            // Handle image rotation & mirroring (front camera mirror effect)
            val rotationDegrees = image.imageInfo.rotationDegrees
            val matrix = Matrix().apply {
                if (rotationDegrees != 0) {
                    postRotate(rotationDegrees.toFloat())
                }
                // Front camera is mirrored horizontally
                postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
            }
            val finalBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Convert to MediaPipe's MPImage
            val mpImage = BitmapImageBuilder(finalBitmap).build()
            val result = landmarker.detect(mpImage)

            val landmarksList = result.landmarks()
            val handDetected = landmarksList.isNotEmpty()

            val motionGrid = FloatArray(gridWidth * gridHeight)
            var centroidX: Float? = null
            var centroidY: Float? = null
            var handSkeleton: FloatArray? = null
            var motionDensity = 0f

            if (handDetected) {
                val handLandmarks = landmarksList[0]
                val skeleton = FloatArray(42)
                var sumX = 0f
                var sumY = 0f

                for (i in 0 until 21) {
                    if (i < handLandmarks.size) {
                        val lm = handLandmarks[i]
                        skeleton[i * 2] = lm.x()
                        skeleton[i * 2 + 1] = lm.y()
                        sumX += lm.x()
                        sumY += lm.y()
                    }
                }

                handSkeleton = skeleton
                centroidX = sumX / 21
                centroidY = sumY / 21

                // Pinch detection between Thumb tip (4) and Index tip (8)
                val thumbTipX = skeleton[4 * 2]
                val thumbTipY = skeleton[4 * 2 + 1]
                val indexTipX = skeleton[8 * 2]
                val indexTipY = skeleton[8 * 2 + 1]
                val pinchDist = Math.hypot((thumbTipX - indexTipX).toDouble(), (thumbTipY - indexTipY).toDouble()).toFloat()
                
                // Pinch threshold: < 0.065f in normalized coordinates
                val isPinch = pinchDist < 0.065f
                val pinchX = (thumbTipX + indexTipX) / 2f
                val pinchY = (thumbTipY + indexTipY) / 2f
                listener.onPinchStateChanged(isPinch, pinchX, pinchY)

                // Temporal smoothing for smooth skeleton rendering
                val smoothed = lastHandSkeleton?.let { last ->
                    FloatArray(42) { i ->
                        last[i] * 0.35f + skeleton[i] * 0.65f
                    }
                } ?: skeleton
                lastHandSkeleton = smoothed
                handSkeleton = smoothed

                // Render connections on the retro 32x24 grid
                val connections = listOf(
                    Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 4), // Thumb
                    Pair(0, 5), Pair(5, 6), Pair(6, 7), Pair(7, 8), // Index
                    Pair(0, 9), Pair(9, 10), Pair(10, 11), Pair(11, 12), // Middle
                    Pair(0, 13), Pair(13, 14), Pair(14, 15), Pair(15, 16), // Ring
                    Pair(0, 17), Pair(17, 18), Pair(18, 19), Pair(19, 20), // Pinky
                    Pair(5, 9), Pair(9, 13), Pair(13, 17) // Palm knuckles
                )

                var gridActiveCount = 0
                for (gy in 0 until gridHeight) {
                    for (gx in 0 until gridWidth) {
                        val x = gx.toFloat() / (gridWidth - 1)
                        val y = gy.toFloat() / (gridHeight - 1)

                        // Shortest distance to any skeleton segment
                        var minDistance = 1f
                        for (conn in connections) {
                            val p1X = skeleton[conn.first * 2]
                            val p1Y = skeleton[conn.first * 2 + 1]
                            val p2X = skeleton[conn.second * 2]
                            val p2Y = skeleton[conn.second * 2 + 1]

                            val dist = distanceToSegment(x, y, p1X, p1Y, p2X, p2Y)
                            if (dist < minDistance) {
                                minDistance = dist
                            }
                        }

                        // Shortest distance to any joint
                        for (i in 0 until 21) {
                            val jX = skeleton[i * 2]
                            val jY = skeleton[i * 2 + 1]
                            val dist = Math.hypot((x - jX).toDouble(), (y - jY).toDouble()).toFloat()
                            if (dist < minDistance) {
                                minDistance = dist
                            }
                        }

                        val idx = gy * gridWidth + gx
                        if (minDistance < 0.045f) {
                            motionGrid[idx] = 1.0f // Draw active hand skeleton
                            gridActiveCount++
                        } else if (minDistance < 0.11f) {
                            motionGrid[idx] = 0.22f // Draw faint halo/contour effect
                        } else {
                            motionGrid[idx] = 0.0f
                        }
                    }
                }
                motionDensity = gridActiveCount.toFloat() / (gridWidth * gridHeight)

                // Gesture tracking: process frame count and centroid history
                consecutiveMotionFrames++
                consecutiveNoMotionFrames = 0

                centroidHistory.add(Pair(centroidX, centroidY))
                if (centroidHistory.size > maxHistorySize) {
                    centroidHistory.removeAt(0)
                }

                // AI landmarks are incredibly smooth, meaning we can detect gestures extremely fast with fewer frames
                if (consecutiveMotionFrames >= 4) {
                    val now = System.currentTimeMillis()
                    if (now - lastGestureTime > gestureCooldownMs) {
                        val detected = evaluateRealtimeGesture()
                        if (detected != null) {
                            listener.onGestureDetected(detected)
                            lastGestureTime = now
                            centroidHistory.clear()
                            consecutiveMotionFrames = 0
                        }
                    }
                }
            } else {
                listener.onPinchStateChanged(false, 0f, 0f)
                consecutiveNoMotionFrames++

                if (consecutiveNoMotionFrames >= 2 && consecutiveMotionFrames >= 2) {
                    val now = System.currentTimeMillis()
                    if (now - lastGestureTime > gestureCooldownMs) {
                        val detected = evaluateSwipeGesture()
                        if (detected != null) {
                            listener.onGestureDetected(detected)
                            lastGestureTime = now
                        }
                    }
                    centroidHistory.clear()
                    consecutiveMotionFrames = 0
                } else if (consecutiveNoMotionFrames >= 3) {
                    centroidHistory.clear()
                    consecutiveMotionFrames = 0
                }

                lastHandSkeleton = null
            }

            // Callback to update visualization and state
            listener.onMotionFrame(
                gridWidth,
                gridHeight,
                motionGrid,
                centroidX,
                centroidY,
                motionDensity,
                handDetected,
                handSkeleton
            )
        } catch (e: Exception) {
            Log.e("GestureDetector", "Error in frame analysis", e)
        } finally {
            image.close()
        }
    }

    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val l2 = (x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)
        if (l2 == 0f) return sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1))
        var t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2
        t = t.coerceIn(0f, 1f)
        val projX = x1 + t * (x2 - x1)
        val projY = y1 + t * (y2 - y1)
        return sqrt((px - projX) * (px - projX) + (py - projY) * (py - projY))
    }

    private fun evaluateRealtimeGesture(): String? {
        if (centroidHistory.size < 6) return null

        val xs = centroidHistory.map { it.first }
        val ys = centroidHistory.map { it.second }

        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f

        val xRange = maxX - minX
        val yRange = maxY - minY

        // WAVE: oscillating back and forth along the X axis
        var directionChanges = 0
        var lastDelta = 0f
        for (i in 1 until xs.size) {
            val delta = xs[i] - xs[i - 1]
            if (abs(delta) > 0.025f) {
                if (lastDelta != 0f && ((delta > 0 && lastDelta < 0) || (delta < 0 && lastDelta > 0))) {
                    directionChanges++
                }
                lastDelta = delta
            }
        }

        if (directionChanges >= 2 && xRange > 0.12f) {
            return "WAVE"
        }

        // HOVER: hand is stationary within a small boundary box
        if (xRange < 0.09f && yRange < 0.09f) {
            return "HOVER"
        }

        return null
    }

    private fun evaluateSwipeGesture(): String? {
        if (centroidHistory.size < 3) return null

        val start = centroidHistory.first()
        val end = centroidHistory.last()

        val deltaX = end.first - start.first
        val deltaY = end.second - start.second

        val minSwipeDistance = 0.12f // 12% traversal threshold for highly responsive gestures

        if (abs(deltaX) > abs(deltaY)) {
            if (abs(deltaX) > minSwipeDistance) {
                return if (deltaX > 0) "SWIPE_RIGHT" else "SWIPE_LEFT"
            }
        } else {
            if (abs(deltaY) > minSwipeDistance) {
                return if (deltaY > 0) "SWIPE_DOWN" else "SWIPE_UP"
            }
        }

        return null
    }

    fun close() {
        try {
            handLandmarker?.close()
            handLandmarker = null
        } catch (e: Exception) {
            Log.e("GestureDetector", "Failed to close HandLandmarker", e)
        }
    }
}
