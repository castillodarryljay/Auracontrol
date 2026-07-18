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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class GestureService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

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
    
    private var wasPinching = false
    private var lastPinchX = 0f
    private var lastPinchY = 0f
    private var lastScrollTime = 0L


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
        val isPinching = MutableStateFlow(false)
        val pinchCoordinates = MutableStateFlow<Pair<Float, Float>?>(null)
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
                handSkeleton: FloatArray?
            ) {
                if (isTrackingPaused) return
                
                GestureService.motionGrid.value = motionGrid
                GestureService.motionDensity.value = motionDensity
                GestureService.centroid.value = if (handDetected && centroidX != null && centroidY != null) Pair(centroidX, centroidY) else null
                GestureService.handDetected.value = handDetected
                GestureService.handSkeleton.value = if (handDetected) handSkeleton else null
            }

            override fun onGestureDetected(gestureId: String) {
                if (isTrackingPaused) return
                
                val mapping = gestureMappings[gestureId]
                val actionId = mapping?.actionId ?: "NONE"
                val actionName = mapping?.actionName ?: "No Action"

                GestureService.lastGesture.value = mapping?.gestureName ?: gestureId
                GestureService.lastAction.value = actionName

                Log.d("GestureService", "Detected $gestureId -> triggering $actionId")
                
                serviceScope.launch(Dispatchers.Main) {
                    executeAction(actionId)
                }
            }

            override fun onPinchStateChanged(isPinch: Boolean, pinchX: Float, pinchY: Float) {
                if (isTrackingPaused) return
                
                GestureService.isPinching.value = isPinch
                GestureService.pinchCoordinates.value = if (isPinch) Pair(pinchX, pinchY) else null

                if (isPinch) {
                    val accService = GestureAccessibilityService.instance
                    if (accService != null) {
                        if (!wasPinching) {
                            lastPinchX = pinchX
                            lastPinchY = pinchY
                            wasPinching = true
                            lastScrollTime = System.currentTimeMillis()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastScrollTime > 160L) {
                                val dx = pinchX - lastPinchX
                                val dy = pinchY - lastPinchY

                                if (kotlin.math.abs(dx) > 0.015f || kotlin.math.abs(dy) > 0.015f) {
                                    val success = accService.dispatchDragGesture(dx, dy)
                                    if (success) {
                                        lastPinchX = pinchX
                                        lastPinchY = pinchY
                                        lastScrollTime = now
                                    }
                                }
                            }
                        }
                    }
                } else {
                    wasPinching = false
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
            "BACK", "HOME", "RECENTS", "SCROLL_UP", "SCROLL_DOWN" -> {
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
                        isPinchingFlow = GestureService.isPinching,
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
    isPinchingFlow: StateFlow<Boolean>,
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
    val isPinching by isPinchingFlow.collectAsState()

    val surfaceColor = Color(0xFF1E1E24).copy(alpha = 0.92f)
    val accentColor = if (isPaused) {
        Color(0xFFFFB4AB)
    } else if (isPinching) {
        Color(0xFFFF9100) // Glowing Gold/Orange when pinching/scrolling
    } else if (handDetected) {
        Color(0xFF00E676) // Glowing green when hand detected
    } else {
        Color(0xFF64748B) // Slate gray when searching
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
                        .background(if (isPaused) Color(0xFF94A3B8) else Color(0xFF22C55E), shape = CircleShape)
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
                    Text(
                        text = "Touchless OS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
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
                if (showVisualizer && !isPaused) {
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

                                    bones.forEach { (jA, jB) ->
                                        val ax = skeleton[jA * 2] * size.width
                                        val ay = skeleton[jA * 2 + 1] * size.height
                                        val bx = skeleton[jB * 2] * size.width
                                        val by = skeleton[jB * 2 + 1] * size.height
                                        
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

                                    if (isPinching) {
                                        val x4 = skeleton[4 * 2] * size.width
                                        val y4 = skeleton[4 * 2 + 1] * size.height
                                        val x8 = skeleton[8 * 2] * size.width
                                        val y8 = skeleton[8 * 2 + 1] * size.height
                                        // Draw a glowing orange line between index and thumb to show pinch connection
                                        drawLine(
                                            color = Color(0xFFFF9100),
                                            start = androidx.compose.ui.geometry.Offset(x4, y4),
                                            end = androidx.compose.ui.geometry.Offset(x8, y8),
                                            strokeWidth = 3.dp.toPx()
                                        )
                                        // Draw a pulsing circle at the midpoint
                                        drawCircle(
                                            color = Color(0xFFFF9100),
                                            radius = 4.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset((x4 + x8) / 2f, (y4 + y8) / 2f)
                                        )
                                    }

                                    for (j in 0 until 21) {
                                        val jx = skeleton[j * 2] * size.width
                                        val jy = skeleton[j * 2 + 1] * size.height

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
