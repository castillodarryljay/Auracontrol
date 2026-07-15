package com.example.engine

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.abs

class GestureDetector(private val listener: GestureListener) : ImageAnalysis.Analyzer {

    interface GestureListener {
        fun onMotionFrame(
            gridWidth: Int,
            gridHeight: Int,
            motionGrid: FloatArray, // 0.0 to 1.0 intensity for each cell
            centroidX: Float?,      // Null if no motion, 0.0 to 1.0 otherwise
            centroidY: Float?,
            motionDensity: Float,   // Percentage of grid with motion (0.0 to 1.0)
            handDetected: Boolean,  // True if a hand is detected
            handSkeleton: FloatArray? // 21-landmark hand skeleton (x, y) or null
        )
        fun onGestureDetected(gestureId: String)
    }

    private val gridWidth = 24
    private val gridHeight = 18
    private var lastLumaGrid = FloatArray(gridWidth * gridHeight)
    private var isFirstFrame = true
    private var lastHandSkeleton: FloatArray? = null

    // Track centroid history for gesture detection
    private val centroidHistory = mutableListOf<Pair<Float, Float>>()
    private val maxHistorySize = 15
    private var consecutiveMotionFrames = 0
    private var consecutiveNoMotionFrames = 0

    // Cooldown to prevent multi-triggering
    private var lastGestureTime = 0L
    private val gestureCooldownMs = 800L

    override fun analyze(image: ImageProxy) {
        val yPlane = image.planes[0]
        val buffer = yPlane.buffer
        val width = image.width
        val height = image.height
        val rowStride = yPlane.rowStride
        val pixelStride = yPlane.pixelStride
        val rotation = image.imageInfo.rotationDegrees

        // Downsample the Y-plane to our grid
        val currentLumaGrid = FloatArray(gridWidth * gridHeight)

        for (gy in 0 until gridHeight) {
            for (gx in 0 until gridWidth) {
                // Map screen space coordinates (u, v) to image buffer (ix, iy) based on rotation/mirroring
                val u = (gx + 0.5f) / gridWidth
                val v = (gy + 0.5f) / gridHeight

                var ix = u
                var iy = v

                when (rotation) {
                    90 -> {
                        ix = v
                        iy = u
                    }
                    180 -> {
                        ix = 1f - u
                        iy = v
                    }
                    270 -> {
                        ix = 1f - v
                        iy = 1f - u
                    }
                    0 -> {
                        ix = 1f - u
                        iy = 1f - v
                    }
                }

                // Map back to pixel offsets
                val px = (ix * width).toInt().coerceIn(0, width - 1)
                val py = (iy * height).toInt().coerceIn(0, height - 1)

                // Define a small local sampling block around px, py
                val sampleRadius = 2
                val startX = (px - sampleRadius).coerceIn(0, width - 1)
                val endX = (px + sampleRadius).coerceIn(startX + 1, width)
                val startY = (py - sampleRadius).coerceIn(0, height - 1)
                val endY = (py + sampleRadius).coerceIn(startY + 1, height)

                var lumaSum = 0L
                var pixelCount = 0

                for (sY in startY until endY) {
                    val rowOffset = sY * rowStride
                    for (sX in startX until endX) {
                        val offset = rowOffset + sX * pixelStride
                        if (offset < buffer.capacity()) {
                            val luma = buffer.get(offset).toInt() and 0xFF
                            lumaSum += luma
                            pixelCount++
                        }
                    }
                }

                val avgLuma = if (pixelCount > 0) lumaSum.toFloat() / pixelCount else 0f
                currentLumaGrid[gy * gridWidth + gx] = avgLuma
            }
        }

        image.close()

        if (isFirstFrame) {
            lastLumaGrid = currentLumaGrid
            isFirstFrame = false
            return
        }

        // Compute frame difference
        val motionGrid = FloatArray(gridWidth * gridHeight)
        var motionCellCount = 0
        var sumMotionX = 0f
        var sumMotionY = 0f

        val lumaDiffThreshold = 18f // Minimum luminance change to consider motion

        for (i in 0 until (gridWidth * gridHeight)) {
            val diff = abs(currentLumaGrid[i] - lastLumaGrid[i])
            if (diff > lumaDiffThreshold) {
                motionGrid[i] = (diff / 255f).coerceIn(0f, 1f)
                motionCellCount++

                // Grid Coordinates
                val gx = i % gridWidth
                val gy = i / gridWidth
                sumMotionX += gx.toFloat() / (gridWidth - 1)
                sumMotionY += gy.toFloat() / (gridHeight - 1)
            } else {
                motionGrid[i] = 0f
            }
        }

        lastLumaGrid = currentLumaGrid

        val totalCells = gridWidth * gridHeight
        val motionDensity = motionCellCount.toFloat() / totalCells

        // Gesture decision logic
        var handDetected = false
        val centroidX: Float?
        val centroidY: Float?
        var handSkeleton: FloatArray? = null

        if (motionCellCount > 0) {
            centroidX = sumMotionX / motionCellCount
            centroidY = sumMotionY / motionCellCount

            // Compute spatial spread of motion cells to verify it's a localized hand
            var totalDistance = 0f
            var minX = 1f
            var maxX = 0f
            var minY = 1f
            var maxY = 0f
            val activePoints = mutableListOf<Pair<Float, Float>>()

            for (i in 0 until totalCells) {
                val gx = (i % gridWidth).toFloat() / (gridWidth - 1)
                val gy = (i / gridWidth).toFloat() / (gridHeight - 1)
                
                if (motionGrid[i] > 0.15f) {
                    if (gx < minX) minX = gx
                    if (gx > maxX) maxX = gx
                    if (gy < minY) minY = gy
                    if (gy > maxY) maxY = gy
                    activePoints.add(Pair(gx, gy))
                }
                
                if (motionGrid[i] > 0f) {
                    val dx = gx - centroidX
                    val dy = gy - centroidY
                    totalDistance += kotlin.math.sqrt(dx * dx + dy * dy)
                }
            }
            val handSpread = totalDistance / motionCellCount

            // A hand is characterized as a medium-sized (4% to 28% screen area), compact, localized cluster (spread <= 0.20)
            val minHandDensity = 0.04f
            val maxHandDensity = 0.28f
            handDetected = (motionDensity in minHandDensity..maxHandDensity) && (handSpread <= 0.20f)

            if (handDetected) {
                val currentSkeleton = FloatArray(42)
                val cx = centroidX
                val cy = centroidY

                // 1. Wrist (Point 0)
                val wristX = cx
                val wristY = maxY.coerceAtLeast(cy + 0.08f).coerceIn(0f, 1f)
                currentSkeleton[0] = wristX
                currentSkeleton[1] = wristY

                val handWidth = (maxX - minX).coerceIn(0.12f, 0.40f)
                val handHeight = (maxY - minY).coerceIn(0.12f, 0.40f)

                // 2. Compute 5 finger joints: MCP, PIP, DIP, Tip
                val angles = floatArrayOf(155f, 115f, 90f, 65f, 25f) // Thumb, Index, Middle, Ring, Pinky
                val mcpOffsets = arrayOf(
                    Pair(-handWidth * 0.38f, handHeight * 0.15f),  // Thumb MCP
                    Pair(-handWidth * 0.18f, -handHeight * 0.16f), // Index MCP
                    Pair(0f, -handHeight * 0.22f),                  // Middle MCP
                    Pair(handWidth * 0.15f, -handHeight * 0.15f),  // Ring MCP
                    Pair(handWidth * 0.35f, handHeight * 0.1f)     // Pinky MCP
                )
                val fingerLengths = floatArrayOf(
                    handHeight * 0.55f, // Thumb
                    handHeight * 0.82f, // Index
                    handHeight * 0.90f, // Middle
                    handHeight * 0.85f, // Ring
                    handHeight * 0.68f  // Pinky
                )

                for (f in 0 until 5) {
                    val mcpIndex = 1 + f * 4
                    val mcpX = (cx + mcpOffsets[f].first).coerceIn(0f, 1f)
                    val mcpY = (cy + mcpOffsets[f].second).coerceIn(0f, 1f)
                    currentSkeleton[mcpIndex * 2] = mcpX
                    currentSkeleton[mcpIndex * 2 + 1] = mcpY

                    // Track finger tip using motion angular search
                    val targetAngle = angles[f]
                    val fingerLen = fingerLengths[f]
                    val angleRad = Math.toRadians(targetAngle.toDouble())
                    val defaultTipX = (mcpX + fingerLen * Math.cos(angleRad)).toFloat().coerceIn(0f, 1f)
                    val defaultTipY = (mcpY - fingerLen * Math.sin(angleRad)).toFloat().coerceIn(0f, 1f)

                    var bestTipX = defaultTipX
                    var bestTipY = defaultTipY
                    var bestDistSq = 0f

                    for (p in activePoints) {
                        val dx = p.first - cx
                        val dy = p.second - cy
                        val dSq = dx * dx + dy * dy
                        val angle = Math.toDegrees(Math.atan2(-dy.toDouble(), dx.toDouble()))
                        val normalizedAngle = if (angle < 0) angle + 360 else angle

                        var angleDiff = abs(normalizedAngle - targetAngle)
                        if (angleDiff > 180) angleDiff = 360 - angleDiff

                        if (angleDiff < 22f && dSq > bestDistSq && dSq < 0.25f) {
                            bestDistSq = dSq
                            bestTipX = p.first
                            bestTipY = p.second
                        }
                    }

                    // Interpolate middle joints PIP and DIP
                    val pipIndex = mcpIndex + 1
                    val dipIndex = mcpIndex + 2
                    val tipIndex = mcpIndex + 3

                    currentSkeleton[pipIndex * 2] = (mcpX + 0.35f * (bestTipX - mcpX)).coerceIn(0f, 1f)
                    currentSkeleton[pipIndex * 2 + 1] = (mcpY + 0.35f * (bestTipY - mcpY)).coerceIn(0f, 1f)

                    currentSkeleton[dipIndex * 2] = (mcpX + 0.70f * (bestTipX - mcpX)).coerceIn(0f, 1f)
                    currentSkeleton[dipIndex * 2 + 1] = (mcpY + 0.70f * (bestTipY - mcpY)).coerceIn(0f, 1f)

                    currentSkeleton[tipIndex * 2] = bestTipX
                    currentSkeleton[tipIndex * 2 + 1] = bestTipY
                }

                // Smooth joints to prevent jitter
                val smoothed = lastHandSkeleton?.let { last ->
                    FloatArray(42) { i ->
                        last[i] * 0.72f + currentSkeleton[i] * 0.28f
                    }
                } ?: currentSkeleton
                lastHandSkeleton = smoothed
                handSkeleton = smoothed
            } else {
                lastHandSkeleton = null
            }
        } else {
            centroidX = null
            centroidY = null
            lastHandSkeleton = null
        }

        if (handDetected) {
            consecutiveMotionFrames++
            consecutiveNoMotionFrames = 0

            centroidHistory.add(Pair(centroidX!!, centroidY!!))
            if (centroidHistory.size > maxHistorySize) {
                centroidHistory.removeAt(0)
            }

            // Real-time hover/wave detection if hand motion continues
            if (consecutiveMotionFrames >= 12) {
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
            consecutiveNoMotionFrames++

            // If hand motion just stopped after being sustained, evaluate terminal swipe
            if (consecutiveNoMotionFrames >= 2 && consecutiveMotionFrames >= 3) {
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
                // Fully clear state and history if no hand is detected for a short while
                centroidHistory.clear()
                consecutiveMotionFrames = 0
            }
        }

        // Notify listener for real-time visualization with handDetected status
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
    }

    private fun evaluateRealtimeGesture(): String? {
        if (centroidHistory.size < 8) return null

        val xs = centroidHistory.map { it.first }
        val ys = centroidHistory.map { it.second }

        val minX = xs.minOrNull() ?: 0f
        val maxX = xs.maxOrNull() ?: 0f
        val minY = ys.minOrNull() ?: 0f
        val maxY = ys.maxOrNull() ?: 0f

        val xRange = maxX - minX
        val yRange = maxY - minY

        // Check for WAVE: rapidly oscillating X coordinate
        var directionChanges = 0
        var lastDelta = 0f
        for (i in 1 until xs.size) {
            val delta = xs[i] - xs[i - 1]
            if (abs(delta) > 0.03f) {
                if (lastDelta != 0f && ((delta > 0 && lastDelta < 0) || (delta < 0 && lastDelta > 0))) {
                    directionChanges++
                }
                lastDelta = delta
            }
        }

        if (directionChanges >= 3 && xRange > 0.15f) {
            return "WAVE"
        }

        // Check for HOVER: sustained motion inside a tight box
        if (xRange < 0.12f && yRange < 0.12f) {
            return "HOVER"
        }

        return null
    }

    private fun evaluateSwipeGesture(): String? {
        if (centroidHistory.size < 4) return null

        val start = centroidHistory.first()
        val end = centroidHistory.last()

        val deltaX = end.first - start.first
        val deltaY = end.second - start.second

        val minSwipeDistance = 0.22f // Must traverse at least 22% of image dimension

        if (abs(deltaX) > abs(deltaY)) {
            // Horizontal movement
            if (abs(deltaX) > minSwipeDistance) {
                // Since our grid is in screen space (mirrored and oriented),
                // deltaX > 0 means movement from left to right (SWIPE_RIGHT)
                // deltaX < 0 means movement from right to left (SWIPE_LEFT)
                return if (deltaX > 0) "SWIPE_RIGHT" else "SWIPE_LEFT"
            }
        } else {
            // Vertical movement
            if (abs(deltaY) > minSwipeDistance) {
                // Image Y: 0 is TOP, 1 is BOTTOM.
                // Swipe Up: hand moves upwards (from bottom of screen to top, deltaY is negative).
                // Swipe Down: hand moves downwards (from top to bottom, deltaY is positive).
                return if (deltaY > 0) "SWIPE_DOWN" else "SWIPE_UP"
            }
        }

        return null
    }
}
