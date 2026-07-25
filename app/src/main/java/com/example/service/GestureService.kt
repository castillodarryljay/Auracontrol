package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.pm.ServiceInfo
import com.example.ui.theme.MyApplicationTheme
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.repeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.GestureMapping
import com.example.engine.GestureDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border

class GestureService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner, SensorEventListener, android.content.SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var lifecycleRegistry: LifecycleRegistry
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService? = null

    private lateinit var windowManager: WindowManager
    private var overlayView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null

    private var gestureMappings = mapOf<String, GestureMapping>()
    private var isFlashlightOn = false
    private var isTrackingPaused = false
    private var gestureDetectorInstance: GestureDetector? = null
    private var lastActivityTime = System.currentTimeMillis()
    private var inactivityJob: kotlinx.coroutines.Job? = null

    private var isPinchScrolling = false
    private var startPinchY = 0f

    private var sensorManager: SensorManager? = null
    private var proximitySensor: Sensor? = null
    private var isProximityActive = false
    private var proximityNearStartTime = 0L


    companion object {
        const val NOTIFICATION_ID = 1010
        const val CHANNEL_ID = "gesture_service_channel"
        
        @Volatile
        var isServiceRunning = false
            private set

        @Volatile
        var previewUseCase: Preview? = null

        private val activePreviews = mutableListOf<PreviewView>()

        @Synchronized
        fun registerPreview(previewView: PreviewView) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                if (!activePreviews.contains(previewView)) {
                    activePreviews.add(previewView)
                }
                updateSurfaceProvider()
            }
        }

        @Synchronized
        fun unregisterPreview(previewView: PreviewView) {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            handler.post {
                activePreviews.remove(previewView)
                updateSurfaceProvider()
            }
        }

        @Synchronized
        fun updateSurfaceProvider() {
            val topPreview = activePreviews.lastOrNull()
            if (topPreview != null) {
                previewUseCase?.setSurfaceProvider(topPreview.surfaceProvider)
            } else {
                previewUseCase?.setSurfaceProvider(null)
            }
        }

        val lastGesture = MutableStateFlow<String>("None")
        val lastAction = MutableStateFlow<String>("None")
        val motionDensity = MutableStateFlow(0f)
        val centroid = MutableStateFlow<Pair<Float, Float>?>(null)
        val motionGrid = MutableStateFlow(FloatArray(24 * 18))
        val handDetected = MutableStateFlow(false)
        val handSkeleton = MutableStateFlow<FloatArray?>(null)
        val imageWidth = MutableStateFlow(480)
        val imageHeight = MutableStateFlow(640)
        val indicatorStatus = MutableStateFlow<String>("searching")
        val isBatterySaverSleeping = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        lifecycleRegistry = LifecycleRegistry(this)
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        cameraExecutor = Executors.newSingleThreadExecutor()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        createNotificationChannel()
        startForegroundServiceWithNotification()

        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        // Load custom mappings reactively from database
        serviceScope.launch {
            val db = AppDatabase.getDatabase(this@GestureService)
            db.gestureMappingDao().getAllMappings().collectLatest { mappings ->
                gestureMappings = mappings.associateBy { it.gestureId }
                Log.d("GestureService", "Loaded ${mappings.size} gesture mappings from DB")
            }
        }

        // Add Floating overlay if we have overlay permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && android.provider.Settings.canDrawOverlays(this)) {
            showFloatingOverlay()
        }

        lastActivityTime = System.currentTimeMillis()
        startInactivityMonitoring()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // Start Camera tracking
        startCameraTracking()

        return START_STICKY
    }

    override fun onDestroy() {
        isServiceRunning = false
        previewUseCase = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceScope.cancel()
        cameraExecutor?.shutdown()
        gestureDetectorInstance?.close()
        gestureDetectorInstance = null
        removeFloatingOverlay()
        removePointerOverlay()

        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        if (isProximityActive) {
            sensorManager?.unregisterListener(this)
            isProximityActive = false
        }

        // Turn off flashlight if left on
        if (isFlashlightOn) {
            toggleFlashlight(forceOff = true)
        }

        store.clear()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Touchless Gesture Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps camera gesture analyzer running in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Gesture Control Active")
            .setContentText("Camera gesture tracking is running in the background.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCameraTracking() {
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val proximityModeEnabled = prefs.getBoolean("proximity_mode_enabled", false)
        if (proximityModeEnabled) {
            updateProximityAndCameraState()
            return
        }

        try {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            cameraProviderFuture.addListener({
                try {
                    cameraProvider = cameraProviderFuture.get()
                    bindCameraUseCases()
                } catch (e: Exception) {
                    Log.e("GestureService", "Failed to retrieve camera provider", e)
                }
            }, ContextCompat.getMainExecutor(this))
        } catch (e: Exception) {
            Log.e("GestureService", "Failed to get ProcessCameraProvider instance", e)
        }
    }

    private fun bindCameraUseCases() {
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val proximityModeEnabled = prefs.getBoolean("proximity_mode_enabled", false)
        if (proximityModeEnabled) return

        val cameraProvider = cameraProvider ?: return
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetResolution(android.util.Size(640, 480))
            .build()

        val preview = Preview.Builder().build()
        previewUseCase = preview
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.post {
            updateSurfaceProvider()
        }

        val gestureDetector = GestureDetector(this, object : GestureDetector.GestureListener {
            override fun onMotionFrame(
                gridWidth: Int,
                gridHeight: Int,
                motionGrid: FloatArray,
                centroidX: Float?,
                centroidY: Float?,
                motionDensity: Float,
                handDetected: Boolean,
                handSkeleton: FloatArray?,
                imgW: Int,
                imgH: Int
            ) {
                if (isTrackingPaused) return
                
                GestureService.motionGrid.value = motionGrid
                GestureService.motionDensity.value = motionDensity
                GestureService.centroid.value = if (handDetected && centroidX != null && centroidY != null) Pair(centroidX, centroidY) else null
                GestureService.handDetected.value = handDetected
                GestureService.handSkeleton.value = if (handDetected) handSkeleton else null
                GestureService.imageWidth.value = imgW
                GestureService.imageHeight.value = imgH

                if (handDetected) {
                    resetInactivityTimer()
                }

                if (handDetected && handSkeleton != null) {
                    serviceScope.launch(Dispatchers.Main) {
                        updatePointerOverlay(handSkeleton)
                    }
                } else {
                    serviceScope.launch(Dispatchers.Main) {
                        isCursorVisible.value = false
                        isHandPointerActive = false
                        isScrollPinching = false
                        isGestureActiveFromDetector = false
                        GestureService.indicatorStatus.value = "searching"
                    }
                }
            }

            override fun onGestureDetected(gestureId: String) {
                if (isTrackingPaused) return
                resetInactivityTimer()
                if (isHandPointerActive) {
                    Log.d("GestureService", "Gesture ignored because air pointer is active: $gestureId")
                    return
                }
                
                val mapping = gestureMappings[gestureId]
                val actionId = mapping?.actionId ?: "NONE"
                val actionName = mapping?.actionName ?: "No Action"

                GestureService.lastGesture.value = mapping?.gestureName ?: gestureId
                GestureService.lastAction.value = actionName

                Log.d("GestureService", "Detected $gestureId -> triggering $actionId")
                
                setGestureActiveIndicator()

                serviceScope.launch(Dispatchers.Main) {
                    executeAction(actionId)
                }
            }
        })
        gestureDetectorInstance = gestureDetector


        imageAnalysis.setAnalyzer(cameraExecutor!!, gestureDetector)

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, imageAnalysis, preview)
        } catch (e: Exception) {
            Log.e("GestureService", "Use case binding failed", e)
        }
    }

    private fun executeAction(actionId: String) {
        when (actionId) {
            "BACK", "HOME", "RECENTS", "SCROLL_UP", "SCROLL_DOWN", "SCREENSHOT", "LOCK_SCREEN", "NOTIFICATIONS", "QUICK_SETTINGS" -> {
                val accService = GestureAccessibilityService.instance
                if (accService != null) {
                    accService.performNavigation(actionId)
                } else {
                    Toast.makeText(
                        this,
                        "Please enable GestureControl Accessibility in System Settings",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            "PLAY_PAUSE", "NEXT_TRACK", "PREVIOUS_TRACK" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val eventTime = SystemClock.uptimeMillis()
                val keyCode = when (actionId) {
                    "PLAY_PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    "NEXT_TRACK" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "PREVIOUS_TRACK" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    else -> 0
                }
                if (keyCode != 0) {
                    audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0))
                    audioManager.dispatchMediaKeyEvent(KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0))
                }
            }
            "VOLUME_UP" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            }
            "VOLUME_DOWN" -> {
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            }
            "TOGGLE_FLASHLIGHT" -> {
                toggleFlashlight()
            }
        }
    }

    private fun toggleFlashlight(forceOff: Boolean = false) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val rearCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                hasFlash
            }
            if (rearCameraId != null) {
                isFlashlightOn = if (forceOff) false else !isFlashlightOn
                cameraManager.setTorchMode(rearCameraId, isFlashlightOn)
            }
        } catch (e: Exception) {
            Log.e("GestureService", "Failed to toggle flashlight", e)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        overlayParams = params
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GestureService)
            setViewTreeViewModelStoreOwner(this@GestureService)
            setViewTreeSavedStateRegistryOwner(this@GestureService)
            setContent {
                MyApplicationTheme {
                    var isExpanded by remember { mutableStateOf(false) }
                    var showVisualizer by remember { mutableStateOf(true) }

                    FloatingOverlayContent(
                        isExpanded = isExpanded,
                        showVisualizer = showVisualizer,
                        onToggleExpand = { isExpanded = !isExpanded },
                        onToggleVisualizer = { showVisualizer = !showVisualizer },
                        lastGestureFlow = GestureService.lastGesture,
                        lastActionFlow = GestureService.lastAction,
                        motionDensityFlow = GestureService.motionDensity,
                        centroidFlow = GestureService.centroid,
                        motionGridFlow = GestureService.motionGrid,
                        handDetectedFlow = GestureService.handDetected,
                        handSkeletonFlow = GestureService.handSkeleton,
                        indicatorStatusFlow = GestureService.indicatorStatus,
                        isPaused = isTrackingPaused,
                        onTogglePause = {
                            isTrackingPaused = !isTrackingPaused
                            if (isTrackingPaused) {
                                GestureService.motionGrid.value = FloatArray(24 * 18)
                                GestureService.motionDensity.value = 0f
                                GestureService.centroid.value = null
                                GestureService.handSkeleton.value = null
                            }
                        },
                        onOpenApp = {
                            val launchIntent = Intent(this@GestureService, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(launchIntent)
                        },
                        modifier = Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                params.x = (params.x + dragAmount.x.toInt()).coerceAtLeast(0)
                                params.y = (params.y + dragAmount.y.toInt()).coerceAtLeast(0)
                                windowManager.updateViewLayout(this@apply, params)
                            }
                        }
                    )
                }
            }
        }

        overlayView = composeView
        windowManager.addView(composeView, params)
    }

    private fun removeFloatingOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("GestureService", "Failed to remove overlay view", e)
            }
        }
        overlayView = null
    }

    private var pointerView: ComposeView? = null
    private var pointerParams: WindowManager.LayoutParams? = null
    private var cursorX = 500f
    private var cursorY = 1000f
    private var centerX = 0.5f
    private var centerY = 0.5f
    private var isJoystickCenterCaptured = false
    private var lastClickTime = 0L

    private var isPinching = false
    private var pinchStartX = 0f
    private var pinchStartY = 0f
    private var pinchStartTime = 0L
    private var hasTriggeredLongPress = false

    private var isHandPointerActive = false
    private var isScrollPinching = false
    private var lastTipX = 0f
    private var lastTipY = 0f
    private var isPointerTrackingFirstFrame = true
    private var lastPointingOrPinchingTime = 0L
    private var scrollStartHandX = 0f
    private var scrollStartHandY = 0f

    private var gestureResetJob: kotlinx.coroutines.Job? = null
    private var isGestureActiveFromDetector = false

    private fun setGestureActiveIndicator() {
        isGestureActiveFromDetector = true
        gestureResetJob?.cancel()
        gestureResetJob = serviceScope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(1200)
            isGestureActiveFromDetector = false
            updateIndicatorStatus()
        }
        updateIndicatorStatus()
    }

    private fun updateIndicatorStatus() {
        val hasHand = GestureService.handDetected.value
        if (!hasHand) {
            GestureService.indicatorStatus.value = "searching"
            return
        }

        if (isScrollPinching || isGestureActiveFromDetector) {
            GestureService.indicatorStatus.value = "gesture"
        } else if (isHandPointerActive) {
            GestureService.indicatorStatus.value = "pointer"
        } else {
            GestureService.indicatorStatus.value = "detected"
        }
    }

    private val isCursorVisible = MutableStateFlow(false)
    private val clickAnimationTrigger = MutableStateFlow(false)

    private fun showPointerOverlay() {
        if (pointerView != null) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        cursorX = screenWidth / 2f
        cursorY = screenHeight / 2f

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = cursorX.toInt()
            y = cursorY.toInt()
        }
        pointerParams = params

        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@GestureService)
            setViewTreeViewModelStoreOwner(this@GestureService)
            setViewTreeSavedStateRegistryOwner(this@GestureService)
            setContent {
                MyApplicationTheme {
                    PointerCursorView(isCursorVisible, clickAnimationTrigger)
                }
            }
        }
        pointerView = composeView
        try {
            windowManager.addView(composeView, params)
        } catch (e: Exception) {
            Log.e("GestureService", "Failed to add pointer view", e)
        }
    }

    private fun removePointerOverlay() {
        pointerView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                Log.e("GestureService", "Failed to remove pointer view", e)
            }
        }
        pointerView = null
        pointerParams = null
    }

    private fun triggerCursorClickAnimation() {
        serviceScope.launch {
            clickAnimationTrigger.value = true
            kotlinx.coroutines.delay(300)
            clickAnimationTrigger.value = false
        }
    }

    private fun updatePointerOverlay(sk: FloatArray) {
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val pointerEnabled = prefs.getBoolean("pointer_enabled", true)
        if (!pointerEnabled) {
            removePointerOverlay()
            return
        }

        if (pointerView == null) {
            showPointerOverlay()
        }

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels.toFloat()
        val screenHeight = displayMetrics.heightPixels.toFloat()

        fun jointDist(j1: Int, j2: Int): Float {
            val dx = sk[j1 * 2] - sk[j2 * 2]
            val dy = sk[j1 * 2 + 1] - sk[j2 * 2 + 1]
            return sqrt(dx * dx + dy * dy)
        }

        val handSize = jointDist(0, 9)
        
        // Hysteresis thresholds to keep tracking super smooth when fingers fold/pinch
        val wasTracking = isHandPointerActive || isScrollPinching || isPinching
        val minHandSize = if (wasTracking) 0.035f else 0.055f
        val minUprightRatio = if (wasTracking) 0.40f else 0.60f
        val maxKnuckleY = if (wasTracking) 0.90f else 0.82f
        
        val isUpright = (sk[0 * 2 + 1] - sk[9 * 2 + 1]) > handSize * minUprightRatio
        val isNotInLowerEdge = sk[9 * 2 + 1] < maxKnuckleY
        
        if (handSize < minHandSize || !isUpright || !isNotInLowerEdge) {
            isCursorVisible.value = false
            isHandPointerActive = false
            isScrollPinching = false
            isPointerTrackingFirstFrame = true
            GestureService.indicatorStatus.value = "searching"
            return
        }

        val isIndexRaised = jointDist(0, 8) > jointDist(0, 6) * 1.05f
        val isMiddleRaised = jointDist(0, 12) > jointDist(0, 10) * 1.05f
        val isIndexPinch = jointDist(4, 8) < (handSize * 0.22f).coerceIn(0.025f, 0.055f)
        val isThumbRaised = jointDist(4, 9) > jointDist(2, 9) * 1.15f

        val isPointingPose = isIndexRaised && !isMiddleRaised

        val currentTime = System.currentTimeMillis()
        if (isPointingPose || isIndexPinch) {
            lastPointingOrPinchingTime = currentTime
        }

        // Determine if we should be in Hand Pointer mode
        if (isPointingPose) {
            isHandPointerActive = true
        } else {
            // Check if we are outside the grace period (500ms) and not pinching
            val timeSinceLastActive = currentTime - lastPointingOrPinchingTime
            if (timeSinceLastActive > 500L && !isIndexPinch) {
                isHandPointerActive = false
            }
        }

        // Only show pointer overlay when in hand pointer mode
        isCursorVisible.value = isHandPointerActive

        val pointerMode = prefs.getString("pointer_mode", "absolute") ?: "absolute"

        if (isHandPointerActive) {
            // Move cursor if index is raised (pointing pose) and thumb is NOT raised, OR if we are pinching (needed to drag!)
            val shouldMoveCursor = (isPointingPose && !isThumbRaised) || isIndexPinch
            if (shouldMoveCursor) {
                val currentTipX = sk[5 * 2]
                val currentTipY = sk[5 * 2 + 1]
                if (pointerMode == "joystick") {
                    if (!isJoystickCenterCaptured) {
                        centerX = currentTipX
                        centerY = currentTipY
                        isJoystickCenterCaptured = true
                    } else {
                        val dx = currentTipX - centerX
                        val dy = currentTipY - centerY
                        val speed = 45f
                        if (abs(dx) > 0.02f || abs(dy) > 0.02f) {
                            cursorX = (cursorX + dx * speed).coerceIn(0f, screenWidth)
                            cursorY = (cursorY + dy * speed).coerceIn(0f, screenHeight)
                        }
                    }
                } else {
                    // Absolute mode behaves as smooth relative tracking to support user's requirement (starts from last position)
                    if (isPointerTrackingFirstFrame) {
                        lastTipX = currentTipX
                        lastTipY = currentTipY
                        isPointerTrackingFirstFrame = false
                    } else {
                        val dx = currentTipX - lastTipX
                        val dy = currentTipY - lastTipY
                        val sensitivity = 1.6f
                        cursorX = (cursorX + dx * screenWidth * sensitivity).coerceIn(0f, screenWidth)
                        cursorY = (cursorY + dy * screenHeight * sensitivity).coerceIn(0f, screenHeight)
                        lastTipX = currentTipX
                        lastTipY = currentTipY
                    }
                    isJoystickCenterCaptured = false
                }
            } else {
                isJoystickCenterCaptured = false
                isPointerTrackingFirstFrame = true
            }

            // Pointer Pinch Actions (Click, Drag, Long Press) using isIndexPinch
            if (isIndexPinch) {
                if (!isPinching) {
                    isPinching = true
                    pinchStartX = cursorX
                    pinchStartY = cursorY
                    pinchStartTime = System.currentTimeMillis()
                    hasTriggeredLongPress = false
                } else {
                    val distanceMoved = sqrt((cursorX - pinchStartX) * (cursorX - pinchStartX) + (cursorY - pinchStartY) * (cursorY - pinchStartY))
                    if (!hasTriggeredLongPress && (System.currentTimeMillis() - pinchStartTime > 600) && distanceMoved <= 30f) {
                        hasTriggeredLongPress = true
                        GestureAccessibilityService.instance?.performLongPress(pinchStartX, pinchStartY)
                        triggerCursorClickAnimation()
                    }
                }
            } else {
                if (isPinching) {
                    isPinching = false
                    val distanceMoved = sqrt((cursorX - pinchStartX) * (cursorX - pinchStartX) + (cursorY - pinchStartY) * (cursorY - pinchStartY))
                    if (hasTriggeredLongPress) {
                        // Long press was already executed
                    } else if (distanceMoved > 30f) {
                        // Perform Drag
                        GestureAccessibilityService.instance?.performDrag(pinchStartX, pinchStartY, cursorX, cursorY)
                    } else {
                        // Perform Click
                        GestureAccessibilityService.instance?.performClick(pinchStartX, pinchStartY)
                        triggerCursorClickAnimation()
                    }
                }
            }
        } else {
            isJoystickCenterCaptured = false
            isPinching = false
        }

        // Open Hand Pinch Scroll (scrolling in any direction)
        if (!isHandPointerActive) {
            if (isIndexPinch) {
                if (!isScrollPinching) {
                    isScrollPinching = true
                    scrollStartHandX = sk[5 * 2]
                    scrollStartHandY = sk[5 * 2 + 1]
                } else {
                    val currentHandX = sk[5 * 2]
                    val currentHandY = sk[5 * 2 + 1]
                    val dx = currentHandX - scrollStartHandX
                    val dy = currentHandY - scrollStartHandY

                    // Scroll threshold (adjusted for sensitivity: 0.05f is perfect)
                    val threshold = 0.05f
                    if (abs(dx) > threshold || abs(dy) > threshold) {
                        if (abs(dx) > abs(dy)) {
                            // Horizontal scroll
                            if (dx > threshold) {
                                // Hand moved right -> swipe right (Scroll Left)
                                val startX = screenWidth * 0.2f
                                val endX = screenWidth * 0.8f
                                GestureAccessibilityService.instance?.performSwipe(startX, screenHeight / 2f, endX, screenHeight / 2f)
                            } else {
                                // Hand moved left -> swipe left (Scroll Right)
                                val startX = screenWidth * 0.8f
                                val endX = screenWidth * 0.2f
                                GestureAccessibilityService.instance?.performSwipe(startX, screenHeight / 2f, endX, screenHeight / 2f)
                            }
                        } else {
                            // Vertical scroll
                            if (dy > threshold) {
                                // Hand moved down -> swipe down (Scroll Up)
                                val startY = screenHeight * 0.2f
                                val endY = screenHeight * 0.8f
                                GestureAccessibilityService.instance?.performSwipe(screenWidth / 2f, startY, screenWidth / 2f, endY)
                            } else {
                                // Hand moved up -> swipe up (Scroll Down)
                                val startY = screenHeight * 0.8f
                                val endY = screenHeight * 0.2f
                                GestureAccessibilityService.instance?.performSwipe(screenWidth / 2f, startY, screenWidth / 2f, endY)
                            }
                        }
                        // Reset scroll baseline to current position to allow continuous scrolling
                        scrollStartHandX = currentHandX
                        scrollStartHandY = currentHandY
                    }
                }
            } else {
                isScrollPinching = false
            }
        } else {
            isScrollPinching = false
        }

        // Update Layout Params
        pointerParams?.let { params ->
            params.x = cursorX.toInt()
            params.y = cursorY.toInt()
            pointerView?.let { view ->
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }

        updateIndicatorStatus()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: android.content.SharedPreferences?, key: String?) {
        if (key == "proximity_mode_enabled") {
            updateProximityAndCameraState()
        } else if (key == "battery_saver_enabled" || key == "battery_saver_timeout") {
            resetInactivityTimer()
            val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("battery_saver_enabled", true)
            if (!enabled && isBatterySaverSleeping.value) {
                wakeFromEcoSleep()
            }
        }
    }

    private fun updateProximityAndCameraState() {
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val proximityModeEnabled = prefs.getBoolean("proximity_mode_enabled", false)

        if (proximityModeEnabled) {
            // Unbind camera completely to save battery and hide camera indicator
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                Log.e("GestureService", "Error unbinding camera for proximity mode", e)
            }
            isTrackingPaused = true
            
            // Start Proximity Sensor
            if (!isProximityActive) {
                val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
                val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
                if (sensor != null) {
                    sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                    isProximityActive = true
                    sensorManager = sm
                    proximitySensor = sensor
                    Log.d("GestureService", "Proximity Mode active: Camera released, Proximity Sensor registered.")
                } else {
                    Log.e("GestureService", "Proximity Sensor not found!")
                }
            }
        } else {
            isTrackingPaused = false
            // Stop Proximity Sensor
            if (isProximityActive) {
                sensorManager?.unregisterListener(this)
                isProximityActive = false
                Log.d("GestureService", "Proximity Mode inactive: Proximity Sensor unregistered.")
            }
            // Re-bind camera use cases
            bindCameraUseCases()
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val value = event.values[0]
        val maxRange = event.sensor.maximumRange
        // Is near? Typically value is less than 5.0 cm and less than maxRange
        val isNear = value < maxRange && value < 5.0f

        Log.d("GestureService", "Proximity sensor changed: value = $value, maxRange = $maxRange, isNear = $isNear")

        if (isNear) {
            if (isBatterySaverSleeping.value) {
                wakeFromEcoSleep()
                return
            }
        }

        val now = System.currentTimeMillis()

        if (isNear) {
            if (proximityNearStartTime == 0L) {
                proximityNearStartTime = now
            }
        } else {
            val startTime = proximityNearStartTime
            if (startTime > 0L) {
                val duration = now - startTime
                proximityNearStartTime = 0L

                // Detect short wave vs longer hover
                if (duration in 50L..700L) {
                    // This is a "PROXIMITY_WAVE" gesture!
                    GestureService.lastGesture.value = "PROXIMITY_WAVE"
                    val mapping = gestureMappings["PROXIMITY_WAVE"]
                    val actionId = mapping?.actionId ?: "NONE"
                    val actionName = mapping?.actionName ?: "No Action"
                    GestureService.lastAction.value = actionName
                    
                    Log.d("GestureService", "Proximity Wave detected -> Action: $actionId")
                    serviceScope.launch(Dispatchers.Main) {
                        executeAction(actionId)
                    }
                } else if (duration > 700L) {
                    // This is a "PROXIMITY_HOVER" gesture!
                    GestureService.lastGesture.value = "PROXIMITY_HOVER"
                    val mapping = gestureMappings["PROXIMITY_HOVER"]
                    val actionId = mapping?.actionId ?: "NONE"
                    val actionName = mapping?.actionName ?: "No Action"
                    GestureService.lastAction.value = actionName

                    Log.d("GestureService", "Proximity Hover detected -> Action: $actionId")
                    serviceScope.launch(Dispatchers.Main) {
                        executeAction(actionId)
                    }
                }
            }
        }
    }

    private fun resetInactivityTimer() {
        lastActivityTime = System.currentTimeMillis()
    }

    private fun startInactivityMonitoring() {
        inactivityJob?.cancel()
        inactivityJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(1000)
                checkInactivity()
            }
        }
    }

    private fun checkInactivity() {
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val batterySaverEnabled = prefs.getBoolean("battery_saver_enabled", true)
        val timeoutSeconds = prefs.getInt("battery_saver_timeout", 30)

        if (!batterySaverEnabled || isBatterySaverSleeping.value || isTrackingPaused) {
            return
        }

        val elapsedSeconds = (System.currentTimeMillis() - lastActivityTime) / 1000
        if (elapsedSeconds >= timeoutSeconds) {
            enterEcoSleepMode()
        }
    }

    private fun enterEcoSleepMode() {
        if (isBatterySaverSleeping.value) return
        isBatterySaverSleeping.value = true
        Log.d("GestureService", "Inactivity timeout reached! Entering Eco Sleep Mode...")

        // Unbind camera entirely to save CPU and battery
        try {
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("GestureService", "Failed to unbind camera for Eco Sleep", e)
        }

        // Clear gesture/hand visualization state
        GestureService.handDetected.value = false
        GestureService.handSkeleton.value = null
        isCursorVisible.value = false
        isHandPointerActive = false
        isScrollPinching = false

        // Register Proximity Sensor if not already registered (so we can wake up on wave)
        if (!isProximityActive) {
            val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            if (sensor != null) {
                sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                isProximityActive = true
                sensorManager = sm
                proximitySensor = sensor
                Log.d("GestureService", "Proximity Sensor registered for Eco Sleep wake up.")
            }
        }
    }

    private fun wakeFromEcoSleep() {
        if (!isBatterySaverSleeping.value) return
        isBatterySaverSleeping.value = false
        Log.d("GestureService", "Waking up from Eco Sleep Mode...")

        resetInactivityTimer()

        // Unregister Proximity Sensor unless proximity mode is actually fully enabled
        val prefs = getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        val proximityModeEnabled = prefs.getBoolean("proximity_mode_enabled", false)
        if (!proximityModeEnabled && isProximityActive) {
            sensorManager?.unregisterListener(this)
            isProximityActive = false
            Log.d("GestureService", "Unregistered proximity sensor after wake up.")
        }

        // Re-bind camera use cases
        bindCameraUseCases()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

@Composable
fun FloatingOverlayContent(
    isExpanded: Boolean,
    showVisualizer: Boolean,
    onToggleExpand: () -> Unit,
    onToggleVisualizer: () -> Unit,
    lastGestureFlow: StateFlow<String>,
    lastActionFlow: StateFlow<String>,
    motionDensityFlow: StateFlow<Float>,
    centroidFlow: StateFlow<Pair<Float, Float>?>,
    motionGridFlow: StateFlow<FloatArray>,
    handDetectedFlow: StateFlow<Boolean>,
    handSkeletonFlow: StateFlow<FloatArray?>,
    indicatorStatusFlow: StateFlow<String>,
    isPaused: Boolean,
    onTogglePause: () -> Unit,
    onOpenApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastGesture by lastGestureFlow.collectAsState()
    val lastAction by lastActionFlow.collectAsState()
    val motionDensity by motionDensityFlow.collectAsState()
    val centroid by centroidFlow.collectAsState()
    val motionGrid by motionGridFlow.collectAsState()
    val handDetected by handDetectedFlow.collectAsState()
    val handSkeleton by handSkeletonFlow.collectAsState()
    val indicatorStatus by indicatorStatusFlow.collectAsState()
    val isBatterySaverSleeping by GestureService.isBatterySaverSleeping.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "scanline_and_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scan_progress"
    )

    val surfaceColor = Color(0xFF1E1E24).copy(alpha = 0.92f)
    val accentColor = if (isPaused) {
        Color(0xFFFFB4AB)
    } else if (isBatterySaverSleeping) {
        Color(0xFFFF3B30) // Red when asleep due to battery saver
    } else {
        when (indicatorStatus) {
            "pointer" -> Color(0xFF2196F3)     // Blue in air pointer mode
            "gesture" -> Color(0xFFFF9800)     // Orange when doing gestures
            "detected" -> Color(0xFF00E676)    // Green when hand detected
            else -> Color(0xFF64748B)          // Slate gray when searching
        }
    }

    Surface(
        modifier = modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(24.dp)),
        color = surfaceColor,
        border = BorderStroke(1.5.dp, accentColor),
        shadowElevation = 8.dp
    ) {
        if (!isExpanded) {
            // Collapsed indicator bubble
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clickable { onToggleExpand() },
                contentAlignment = Alignment.Center
            ) {
                // Outer pulsing ring if active and hand detected
                if (!isPaused && handDetected) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(1.15f)
                            .background(accentColor.copy(alpha = 0.15f), shape = CircleShape)
                    )
                }
                
                Icon(
                    imageVector = Icons.Default.Gesture,
                    contentDescription = "Gesture Tracking Active",
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
                
                // Mini privacy dot in top-right corner
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-8).dp, y = 8.dp)
                        .background(
                            if (isPaused) Color(0xFF94A3B8)
                            else if (isBatterySaverSleeping) Color(0xFFFF3B30)
                            else Color(0xFF22C55E),
                            shape = CircleShape
                        )
                )
            }
        } else {
            // Expanded full scanner pane
            Column(
                modifier = Modifier
                    .width(180.dp)
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accentColor.copy(alpha = pulseAlpha), shape = CircleShape)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Touchless OS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Row {
                        IconButton(
                            onClick = onToggleVisualizer,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = if (showVisualizer) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle heatmap",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleExpand,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Collapse overlay",
                                tint = Color.LightGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Motion Visualizer Grid (Cyber heat-map)
                if (isBatterySaverSleeping) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D0A0A).copy(alpha = 0.8f))
                            .border(1.dp, Color(0xFFFF3B30).copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color(0xFFFF3B30).copy(alpha = pulseAlpha), shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "ECO SLEEP MODE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF3B30),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Wave near proximity sensor",
                                fontSize = 8.sp,
                                color = Color.LightGray.copy(alpha = 0.8f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else if (showVisualizer && !isPaused) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
                        
                        DisposableEffect(previewViewRef) {
                            val view = previewViewRef
                            if (view != null) {
                                GestureService.registerPreview(view)
                            }
                            onDispose {
                                if (view != null) {
                                    GestureService.unregisterPreview(view)
                                }
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    previewViewRef = this
                                }
                            },
                            update = { },
                            modifier = Modifier.fillMaxSize()
                        )
                        val imgW by GestureService.imageWidth.collectAsState()
                        val imgH by GestureService.imageHeight.collectAsState()
                        
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Draw hand skeleton in floating HUD
                            handSkeleton?.let { skeleton ->
                                if (skeleton.size >= 42) {
                                    val bones = listOf(
                                        Pair(0, 1), Pair(0, 5), Pair(0, 9), Pair(0, 13), Pair(0, 17),
                                        Pair(1, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17),
                                        Pair(1, 2), Pair(2, 3), Pair(3, 4),
                                        Pair(5, 6), Pair(6, 7), Pair(7, 8),
                                        Pair(9, 10), Pair(10, 11), Pair(11, 12),
                                        Pair(13, 14), Pair(14, 15), Pair(15, 16),
                                        Pair(17, 18), Pair(18, 19), Pair(19, 20)
                                    )

                                    val glowColor = Color(0xFF00FF87)
                                    val boneColor = Color(0xFFE2E8F0)

                                    // Map normalized frame coordinates with FILL_CENTER scaling logic
                                    val scaleX = size.width / imgW
                                    val scaleY = size.height / imgH
                                    val scale = maxOf(scaleX, scaleY)
                                    val scaledW = imgW * scale
                                    val scaledH = imgH * scale
                                    val offsetX = (size.width - scaledW) / 2f
                                    val offsetY = (size.height - scaledH) / 2f

                                    val mapX = { nx: Float -> nx * scaledW + offsetX }
                                    val mapY = { ny: Float -> ny * scaledH + offsetY }

                                    bones.forEach { (jA, jB) ->
                                        val ax = mapX(skeleton[jA * 2])
                                        val ay = mapY(skeleton[jA * 2 + 1])
                                        val bx = mapX(skeleton[jB * 2])
                                        val by = mapY(skeleton[jB * 2 + 1])
                                        
                                        drawLine(
                                            color = boneColor.copy(alpha = 0.4f),
                                            start = androidx.compose.ui.geometry.Offset(ax, ay),
                                            end = androidx.compose.ui.geometry.Offset(bx, by),
                                            strokeWidth = 2.dp.toPx()
                                        )
                                        drawLine(
                                            color = glowColor.copy(alpha = 0.7f),
                                            start = androidx.compose.ui.geometry.Offset(ax, ay),
                                            end = androidx.compose.ui.geometry.Offset(bx, by),
                                            strokeWidth = 0.8.dp.toPx()
                                        )
                                    }

                                    for (j in 0 until 21) {
                                        val jx = mapX(skeleton[j * 2])
                                        val jy = mapY(skeleton[j * 2 + 1])

                                        if (j == 4 || j == 8 || j == 12 || j == 16 || j == 20) {
                                            drawCircle(
                                                color = glowColor,
                                                radius = 2.5.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(jx, jy)
                                            )
                                        } else {
                                            drawCircle(
                                                color = Color.White,
                                                radius = 1.2.dp.toPx(),
                                                center = androidx.compose.ui.geometry.Offset(jx, jy)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Scanning bar animation overlay
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .offset(y = 90.dp * scanProgress)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            accentColor.copy(alpha = 0.1f),
                                            accentColor.copy(alpha = 0.8f),
                                            accentColor.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Last gesture trigger stats
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(6.dp)) {
                        if (isBatterySaverSleeping) {
                            Text(
                                text = "Camera: OFF (Saving Power)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF3B30)
                            )
                            Text(
                                text = "Tracking: Suspended",
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        } else {
                            Text(
                                text = "Gesture: $lastGesture",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Action: $lastAction",
                                fontSize = 10.sp,
                                color = accentColor
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Mini toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(
                        onClick = onTogglePause,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.1f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onOpenApp,
                        modifier = Modifier
                            .size(32.dp)
                            .background(Color.White.copy(alpha = 0.1f), shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PointerCursorView(
    isCursorVisible: StateFlow<Boolean>,
    clickAnimationTrigger: StateFlow<Boolean>
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE) }
    
    // States
    val isVisible by isCursorVisible.collectAsState()
    val isClicked by clickAnimationTrigger.collectAsState()
    
    val alpha by animateFloatAsState(targetValue = if (isVisible) 1f else 0f, label = "cursor_alpha")
    val clickScale by animateFloatAsState(targetValue = if (isClicked) 1.4f else 1.0f, label = "click_scale")

    val pointerColorStr = prefs.getString("pointer_color", "#00FF87") ?: "#00FF87"
    val pointerSizeDp = prefs.getInt("pointer_size", 48)
    val pointerShape = prefs.getString("pointer_shape", "crosshair") ?: "crosshair"

    val color = Color(android.graphics.Color.parseColor(pointerColorStr))
    val size = pointerSizeDp.dp

    Box(
        modifier = Modifier
            .size(size)
            .scale(clickScale)
            .alpha(alpha)
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        when (pointerShape) {
            "dot" -> {
                Box(
                    modifier = Modifier
                        .size(size / 3)
                        .background(color, shape = CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                )
            }
            "ring" -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, color, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(color, shape = CircleShape)
                )
            }
            "arrow" -> {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.toPx(), size.toPx() * 0.5f)
                        lineTo(size.toPx() * 0.4f, size.toPx() * 0.6f)
                        close()
                    }
                    drawPath(path, color)
                    drawPath(path, Color.White, style = Stroke(width = 1.dp.toPx()))
                }
            }
            else -> {
                // Crosshair
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.5.dp, color, CircleShape)
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val len = size.toPx()
                    drawLine(color, androidx.compose.ui.geometry.Offset(0f, len / 2), androidx.compose.ui.geometry.Offset(len, len / 2), strokeWidth = 2f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(len / 2, 0f), androidx.compose.ui.geometry.Offset(len / 2, len), strokeWidth = 2f)
                }
            }
        }
    }
}
