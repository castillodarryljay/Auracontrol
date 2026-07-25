package com.example

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.launch
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.camera.view.PreviewView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GestureMapping
import com.example.data.GestureRepository
import com.example.service.GestureService
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen()
            }
        }
    }
}

enum class NavigationTab {
    DASHBOARD,
    PLAYGROUND,
    CUSTOMIZE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    // Initialize Database & Repository inside Compose to keep things completely self-contained and clean
    val db = remember { AppDatabase.getDatabase(context.applicationContext) }
    val repository = remember { GestureRepository(db.gestureMappingDao()) }
    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository))

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.checkAllPermissionsAndState(context)
    }

    var currentTab by remember { mutableStateOf(NavigationTab.DASHBOARD) }

    // Check permission states when view becomes visible or active
    LaunchedEffect(Unit) {
        viewModel.initializeDefaults()
        while (true) {
            viewModel.checkAllPermissionsAndState(context)
            delay(1500) // Poll permissions & service status periodically
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color(0xFF0A0C10), // Immersive deep slate black background
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF0A0C10))
                    .statusBarsPadding()
            ) {
                // Immersive Status Bar / Privacy Indicator
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_indicator")
                        val indicatorAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = if (isServiceRunning) Color(0xFF22C55E).copy(alpha = indicatorAlpha) else Color(0xFF94A3B8).copy(alpha = 0.5f),
                                    shape = CircleShape
                                )
                        )
                        Text(
                            text = if (isServiceRunning) "SENSOR ACTIVE" else "SENSOR STANDBY",
                            color = if (isServiceRunning) Color(0xFF22C55E).copy(alpha = 0.8f) else Color(0xFF94A3B8).copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Custom Drawn 4-bar Cellular Icon
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                            modifier = Modifier.height(10.dp)
                        ) {
                            Box(modifier = Modifier.width(2.dp).height(3.dp).background(Color(0xFF94A3B8)))
                            Box(modifier = Modifier.width(2.dp).height(5.dp).background(Color(0xFF94A3B8)))
                            Box(modifier = Modifier.width(2.dp).height(7.dp).background(Color(0xFF94A3B8)))
                            Box(modifier = Modifier.width(2.dp).height(10.dp).background(Color(0xFF94A3B8)))
                        }

                        // Custom Drawn Healthy Battery Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(10.dp)
                                    .border(1.dp, Color(0xFF94A3B8), RoundedCornerShape(2.dp))
                                    .padding(1.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.85f)
                                        .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(4.dp)
                                    .background(Color(0xFF94A3B8), RoundedCornerShape(1.dp))
                            )
                        }
                    }
                }

                // Immersive Brand Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Aura",
                                fontWeight = FontWeight.Light,
                                fontSize = 30.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = Color.White,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                text = "Control",
                                fontWeight = FontWeight.Bold,
                                fontSize = 30.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = Color(0xFFA8C7FA),
                                letterSpacing = (-0.5).sp
                            )
                        }
                        Text(
                            text = "Touchless Navigation Engine",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    // Top-Bar Toggle Switch (matching mockup button)
                    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
                    val hasCameraPermission by viewModel.hasCameraPermission.collectAsStateWithLifecycle()
                    
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(32.dp)
                            .background(
                                color = if (isServiceRunning) Color(0xFFA8C7FA) else Color(0xFF444746),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                } else {
                                    viewModel.toggleService(context)
                                }
                            }
                            .padding(4.dp),
                        contentAlignment = if (isServiceRunning) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    color = if (isServiceRunning) Color(0xFF003355) else Color(0xFFC2E7FF),
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF111318), // Immersive bottom navigation bg
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == NavigationTab.DASHBOARD,
                    onClick = { currentTab = NavigationTab.DASHBOARD },
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = "Sensor") },
                    label = { Text("Sensor") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFC2E7FF),
                        selectedTextColor = Color(0xFFC2E7FF),
                        indicatorColor = Color(0xFF004A77),
                        unselectedIconColor = Color.LightGray.copy(alpha = 0.5f),
                        unselectedTextColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_dashboard_tab")
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.CUSTOMIZE,
                    onClick = { currentTab = NavigationTab.CUSTOMIZE },
                    icon = { Icon(Icons.Default.Edit, contentDescription = "Gestures") },
                    label = { Text("Gestures") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFC2E7FF),
                        selectedTextColor = Color(0xFFC2E7FF),
                        indicatorColor = Color(0xFF004A77),
                        unselectedIconColor = Color.LightGray.copy(alpha = 0.5f),
                        unselectedTextColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_customize_tab")
                )
                NavigationBarItem(
                    selected = currentTab == NavigationTab.PLAYGROUND,
                    onClick = { currentTab = NavigationTab.PLAYGROUND },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Global") },
                    label = { Text("Global") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFC2E7FF),
                        selectedTextColor = Color(0xFFC2E7FF),
                        indicatorColor = Color(0xFF004A77),
                        unselectedIconColor = Color.LightGray.copy(alpha = 0.5f),
                        unselectedTextColor = Color.LightGray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("nav_playground_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                NavigationTab.DASHBOARD -> DashboardScreen(viewModel, cameraPermissionLauncher)
                NavigationTab.PLAYGROUND -> PlaygroundScreen(viewModel)
                NavigationTab.CUSTOMIZE -> CustomizeScreen(viewModel)
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    cameraPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val context = LocalContext.current
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val isAccessibilityRunning by viewModel.isAccessibilityRunning.collectAsStateWithLifecycle()
    val hasCameraPermission by viewModel.hasCameraPermission.collectAsStateWithLifecycle()
    val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Glowing Hero Master Activation Switch
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("main_activation_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)), // Immersive surface bg
                border = BorderStroke(
                    1.dp,
                    if (isServiceRunning) Color(0xFFA8C7FA).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)
                ),
                shape = RoundedCornerShape(32.dp) // Large beautiful corners
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val scaleTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseScale by scaleTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.04f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = EaseInOutSine),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    // Big Activation Button
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .scale(if (isServiceRunning) pulseScale else 1f)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = if (isServiceRunning) {
                                        listOf(Color(0xFFA8C7FA).copy(alpha = 0.3f), Color.Transparent)
                                      } else {
                                        listOf(Color.White.copy(alpha = 0.02f), Color.Transparent)
                                      }
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            onClick = {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                                } else {
                                    viewModel.toggleService(context)
                                }
                            },
                            modifier = Modifier
                                .size(84.dp)
                                .testTag("toggle_service_button"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServiceRunning) Color(0xFFA8C7FA) else Color(0xFF1C2024)
                            ),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isServiceRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Toggle Service",
                                tint = if (isServiceRunning) Color(0xFF003355) else Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isServiceRunning) "TOUCHLESS ENGINE ACTIVE" else "TOUCHLESS ENGINE STANDBY",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isServiceRunning) Color(0xFFA8C7FA) else Color(0xFF94A3B8),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isServiceRunning) "Tracking air gestures in the background." else "Enable service to gesture navigate system-wide.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Setup and Permissions Section
        item {
            Text(
                text = "SYSTEM ACCESS CONFIG",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }

        // Camera Permission Row
        item {
            PermissionCard(
                title = "Camera Access",
                description = "Required to capture and analyze hands movement offline.",
                isGranted = hasCameraPermission,
                onGrantClick = {
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }
            )
        }

        // Draw Over Other Apps Permission Row
        item {
            PermissionCard(
                title = "Floating Privacy Indicator",
                description = "Displays overlay to visually confirm camera status and gesture triggers.",
                isGranted = hasOverlayPermission,
                onGrantClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                }
            )
        }

        // Accessibility Service Permission Row
        item {
            PermissionCard(
                title = "AuraControl Accessibility Service",
                description = "Required to inject system navigation commands like Back, Home, and Scrolling.",
                isGranted = isAccessibilityRunning,
                onGrantClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                }
            )
        }

        // Instructions Quick Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Tips",
                            tint = Color(0xFFA8C7FA)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Air Gesturing Guide",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. Prop your phone upright in front of you.\n" +
                               "2. Stand/sit about 1 to 2 feet away.\n" +
                               "3. Make swift, intentional swipe movements with your hand in front of the front camera.\n" +
                               "4. Check the \"Global\" tab to calibrate your environment!",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onGrantClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C2024)), // Match Tailwind bg-[#1C2024]
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)), // Match border border-white/5
        shape = RoundedCornerShape(28.dp) // Match rounded-[28px]
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B), // Slate subtext
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            if (isGranted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Granted",
                    tint = Color(0xFF22C55E), // Vivid green
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Button(
                    onClick = onGrantClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFA8C7FA)),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "GRANT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF003355)
                    )
                }
            }
        }
    }
}

@Composable
fun PlaygroundScreen(viewModel: MainViewModel) {
    val isServiceRunning by viewModel.isServiceRunning.collectAsStateWithLifecycle()
    val density by viewModel.serviceMotionDensity.collectAsStateWithLifecycle()
    val centroid by viewModel.serviceCentroid.collectAsStateWithLifecycle()
    val motionGrid by viewModel.serviceMotionGrid.collectAsStateWithLifecycle()
    val lastGesture by viewModel.serviceLastGesture.collectAsStateWithLifecycle()
    val lastAction by viewModel.serviceLastAction.collectAsStateWithLifecycle()
    val handDetected by viewModel.serviceHandDetected.collectAsStateWithLifecycle()
    val handSkeleton by viewModel.serviceHandSkeleton.collectAsStateWithLifecycle()
    val imageWidth by viewModel.serviceImageWidth.collectAsStateWithLifecycle()
    val imageHeight by viewModel.serviceImageHeight.collectAsStateWithLifecycle()
    val mappings by viewModel.allMappings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedDatasetId by remember { mutableStateOf<String?>("open_palm") }
    var isCalibrating by remember { mutableStateOf(false) }

    // Keep track of local flash flare when gesture detected
    var showGestureFlash by remember { mutableStateOf(false) }
    var flashedGestureName by remember { mutableStateOf("") }

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        }
    }
    val labelBgPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#E60F172A") // Slate 900
            style = android.graphics.Paint.Style.FILL
            isAntiAlias = true
        }
    }
    val labelBorderPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#A8C7FA") // Tech blue border
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }
    }
    val leaderLinePaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00FF87") // Neon green leader line
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }
    }

    LaunchedEffect(lastGesture) {
        if (lastGesture != "None" && lastGesture.isNotEmpty()) {
            flashedGestureName = lastGesture
            showGestureFlash = true
            delay(1200)
            showGestureFlash = false
        }
    }

    // Scanning animation transition
    val infiniteTransition = rememberInfiniteTransition(label = "scanner")
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan_line"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "REALTIME OS TRACKER TELEMETRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.5.sp
            )
        }

        // Live Camera Stream Telemetry (Standard View)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .border(
                        1.dp,
                        if (isServiceRunning) Color(0xFFA8C7FA).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(32.dp)
                    )
                    .background(Color(0xFF0F0F12))
            ) {
                if (!isServiceRunning) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VisibilityOff,
                            contentDescription = "Off",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Camera Telemetry Standby",
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aura's hand tracking is offline to conserve battery. Tap the 'AURA' master switch on Sensor dashboard to start real-time tracking.",
                            color = Color(0xFF64748B),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
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
                        handSkeleton?.let { skeleton ->
                            if (skeleton.size >= 42) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val imgW = imageWidth.toFloat()
                                val imgH = imageHeight.toFloat()

                                val scaleX = canvasWidth / imgW
                                val scaleY = canvasHeight / imgH
                                val scale = maxOf(scaleX, scaleY)

                                val scaledWidth = imgW * scale
                                val scaledHeight = imgH * scale
                                val offsetX = (canvasWidth - scaledWidth) / 2f
                                val offsetY = (canvasHeight - scaledHeight) / 2f

                                fun getProjectedX(nx: Float): Float {
                                    return nx * scaledWidth + offsetX
                                }

                                fun getProjectedY(ny: Float): Float {
                                    return ny * scaledHeight + offsetY
                                }

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
                                val boneColor = Color(0xFFA8C7FA)
                                val jointColor = Color(0xFFC2E7FF)

                                bones.forEach { (jA, jB) ->
                                    val ax = getProjectedX(skeleton[jA * 2])
                                    val ay = getProjectedY(skeleton[jA * 2 + 1])
                                    val bx = getProjectedX(skeleton[jB * 2])
                                    val by = getProjectedY(skeleton[jB * 2 + 1])
                                    
                                    drawLine(
                                        color = boneColor.copy(alpha = 0.5f),
                                        start = androidx.compose.ui.geometry.Offset(ax, ay),
                                        end = androidx.compose.ui.geometry.Offset(bx, by),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawLine(
                                        color = glowColor.copy(alpha = 0.8f),
                                        start = androidx.compose.ui.geometry.Offset(ax, ay),
                                        end = androidx.compose.ui.geometry.Offset(bx, by),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                for (j in 0 until 21) {
                                    val jx = getProjectedX(skeleton[j * 2])
                                    val jy = getProjectedY(skeleton[j * 2 + 1])

                                    if (j in listOf(4, 8, 12, 16, 20)) {
                                        drawCircle(
                                            color = glowColor.copy(alpha = 0.3f),
                                            radius = 8.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                        drawCircle(
                                            color = glowColor,
                                            radius = 4.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                    } else {
                                        drawCircle(
                                            color = jointColor,
                                            radius = 3.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                    }
                                }

                                val thumbTipX = getProjectedX(skeleton[4 * 2])
                                val thumbTipY = getProjectedY(skeleton[4 * 2 + 1])
                                val pointerTipX = getProjectedX(skeleton[8 * 2])
                                val pointerTipY = getProjectedY(skeleton[8 * 2 + 1])

                                val thumbLabelX = thumbTipX - 70f
                                val thumbLabelY = thumbTipY - 70f

                                drawContext.canvas.nativeCanvas.drawLine(
                                    thumbTipX, thumbTipY, thumbLabelX, thumbLabelY, leaderLinePaint
                                )
                                val thumbText = "THUMB"
                                val rect = android.graphics.Rect()
                                textPaint.getTextBounds(thumbText, 0, thumbText.length, rect)
                                val padW = 22f
                                val padH = 16f
                                val bgRect = android.graphics.RectF(
                                    thumbLabelX - rect.width() / 2f - padW,
                                    thumbLabelY - rect.height() / 2f - padH - 4f,
                                    thumbLabelX + rect.width() / 2f + padW,
                                    thumbLabelY + rect.height() / 2f + padH - 4f
                                )
                                drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 12f, 12f, labelBgPaint)
                                drawContext.canvas.nativeCanvas.drawRoundRect(bgRect, 12f, 12f, labelBorderPaint)
                                drawContext.canvas.nativeCanvas.drawText(
                                    thumbText, thumbLabelX, thumbLabelY + rect.height() / 2f - 4f, textPaint
                                )

                                val pointerLabelX = pointerTipX + 70f
                                val pointerLabelY = pointerTipY - 70f

                                drawContext.canvas.nativeCanvas.drawLine(
                                    pointerTipX, pointerTipY, pointerLabelX, pointerLabelY, leaderLinePaint
                                )
                                val pointerText = "POINTER"
                                textPaint.getTextBounds(pointerText, 0, pointerText.length, rect)
                                val bgRectPointer = android.graphics.RectF(
                                    pointerLabelX - rect.width() / 2f - padW,
                                    pointerLabelY - rect.height() / 2f - padH - 4f,
                                    pointerLabelX + rect.width() / 2f + padW,
                                    pointerLabelY + rect.height() / 2f + padH - 4f
                                )
                                drawContext.canvas.nativeCanvas.drawRoundRect(bgRectPointer, 12f, 12f, labelBgPaint)
                                drawContext.canvas.nativeCanvas.drawRoundRect(bgRectPointer, 12f, 12f, labelBorderPaint)
                                drawContext.canvas.nativeCanvas.drawText(
                                    pointerText, pointerLabelX, pointerLabelY + rect.height() / 2f - 4f, textPaint
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "INDEX: CALIBRATING CH[0]",
                            color = Color(0xFFA8C7FA).copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.TopStart)
                        )

                        Text(
                            text = "MOTION DENSITY: ${(density * 100).toInt()}%",
                            color = Color(0xFFC2E7FF).copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )

                        Row(
                            modifier = Modifier.align(Alignment.BottomStart),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        color = if (handDetected) Color(0xFF00FF87) else Color(0xFF64748B),
                                        shape = CircleShape
                                    )
                            )
                            Text(
                                text = "HAND DETECTED: ${if (handDetected) "YES" else "NO"}",
                                color = if (handDetected) Color(0xFF00FF87) else Color(0xFFA8C7FA).copy(alpha = 0.6f),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        androidx.compose.animation.AnimatedVisibility(
                            visible = showGestureFlash,
                            enter = fadeIn() + scaleIn(initialScale = 0.8f),
                            exit = fadeOut() + scaleOut(targetScale = 1.2f),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFA8C7FA)),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.shadow(16.dp, RoundedCornerShape(20.dp))
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "TRIGGERED",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF003355),
                                        letterSpacing = 1.5.sp
                                    )
                                    Text(
                                        text = flashedGestureName,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF003355)
                                    )
                                    Text(
                                        text = "Action: $lastAction",
                                        fontSize = 11.sp,
                                        color = Color(0xFF003355).copy(alpha = 0.8f),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Telemetry Stats Block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LIVE GESTURE STREAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isServiceRunning) lastGesture else "Inactive",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color.White else Color(0xFF64748B)
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "COMMAND ISSUED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isServiceRunning) lastAction else "Inactive",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color(0xFFA8C7FA) else Color(0xFF64748B)
                        )
                    }
                }
            }
        }

        // --- PROXIMITY MODE SETTING ---
        item {
            val proximityModeEnabled by viewModel.proximityModeEnabled.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Proximity Settings",
                            tint = Color(0xFF00FF87)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "PROXIMITY SENSOR MODE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Proximity Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Replaces camera with the phone's proximity sensor for hands-free gestures. Saves battery and works in darkness.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = proximityModeEnabled,
                            onCheckedChange = { viewModel.updateProximityModeEnabled(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF003355),
                                checkedTrackColor = Color(0xFF00FF87)
                            )
                        )
                    }
                }
            }
        }

        // --- INTELLIGENT BATTERY SAVER SETTING ---
        item {
            val batterySaverEnabled by viewModel.batterySaverEnabled.collectAsStateWithLifecycle()
            val batterySaverTimeout by viewModel.batterySaverTimeout.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.BatteryChargingFull,
                            contentDescription = "Battery Saver Settings",
                            tint = Color(0xFF00FF87)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "INTELLIGENT BATTERY SAVER",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto-Sleep Mode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Sustains battery by auto-turning off camera tracking after inactivity. Wave close to proximity sensor to wake up immediately.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = batterySaverEnabled,
                            onCheckedChange = { viewModel.updateBatterySaverEnabled(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF003355),
                                checkedTrackColor = Color(0xFF00FF87)
                            ),
                            modifier = Modifier.testTag("battery_saver_toggle")
                        )
                    }

                    if (batterySaverEnabled) {
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        val timeoutText = when {
                            batterySaverTimeout < 60 -> "${batterySaverTimeout}s"
                            batterySaverTimeout % 60 == 0 -> "${batterySaverTimeout / 60}m"
                            else -> "${batterySaverTimeout / 60}m ${batterySaverTimeout % 60}s"
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Inactivity Timeout",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = timeoutText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF00FF87)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Slider(
                            value = batterySaverTimeout.toFloat(),
                            onValueChange = { viewModel.updateBatterySaverTimeout(context, it.toInt()) },
                            valueRange = 10f..180f,
                            steps = 16, // steps of 10s: 10, 20, 30, 40... 180
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00FF87),
                                activeTrackColor = Color(0xFF00FF87),
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.testTag("battery_saver_timeout_slider")
                        )
                    }
                }
            }
        }

        // --- AIR-POINTER OVERLAY & SENSITIVITY CONFIGURATION ---
        item {
            val pointerEnabled by viewModel.pointerEnabled.collectAsStateWithLifecycle()
            val pointerMode by viewModel.pointerMode.collectAsStateWithLifecycle()
            val pointerColorHex by viewModel.pointerColor.collectAsStateWithLifecycle()
            val pointerSize by viewModel.pointerSize.collectAsStateWithLifecycle()
            val pointerShape by viewModel.pointerShape.collectAsStateWithLifecycle()
            val sensitivityMode by viewModel.sensitivityMode.collectAsStateWithLifecycle()
            val sensitivityVal by viewModel.sensitivityValue.collectAsStateWithLifecycle()

            val hasOverlayPermission by viewModel.hasOverlayPermission.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SettingsAccessibility,
                            contentDescription = "Air-Pointer Settings",
                            tint = Color(0xFFA8C7FA)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "AIR-POINTER CONFIGURATION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pointer Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Air-Pointer",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Shows a small pointer overlay on screen when you raise your pointer finger",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = pointerEnabled,
                            onCheckedChange = { viewModel.updatePointerEnabled(context, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF003355),
                                checkedTrackColor = Color(0xFFA8C7FA)
                            )
                        )
                    }

                    if (pointerEnabled) {
                        if (!hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF3B2424)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color(0xFFFFB4AB))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Please grant Display Overlay permission in System Settings to allow the pointer overlay to appear.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFB4AB)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // Pointer Mode Selection (Joystick vs Pointer)
                        Text(
                            text = "Cursor Movement Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                Pair("absolute", "Absolute Pointer"),
                                Pair("joystick", "Joystick Cursor")
                            ).forEach { (mode, label) ->
                                val isSelected = pointerMode == mode
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.updatePointerMode(context, mode) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFA8C7FA).copy(alpha = 0.15f) else Color(0xFF131518)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = if (isSelected) Color(0xFFA8C7FA) else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pointer Shape selection
                        Text(
                            text = "Pointer Shape",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("crosshair", "dot", "ring", "arrow").forEach { shape ->
                                val isSelected = pointerShape == shape
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.updatePointerShape(context, shape) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFA8C7FA).copy(alpha = 0.15f) else Color(0xFF131518)
                                    ),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = shape.uppercase(),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = if (isSelected) Color(0xFFA8C7FA) else Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Pointer Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Pointer Size",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${pointerSize}dp",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA8C7FA)
                            )
                        }
                        Slider(
                            value = pointerSize.toFloat(),
                            onValueChange = { viewModel.updatePointerSize(context, it.toInt()) },
                            valueRange = 24f..72f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFA8C7FA),
                                activeTrackColor = Color(0xFFA8C7FA)
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Pointer Color Grid
                        Text(
                            text = "Pointer Accent Color",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            listOf("#00FF87", "#38BDF8", "#EC4899", "#EAB308", "#A855F7").forEach { colorHex ->
                                val isSelected = pointerColorHex.equals(colorHex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(Color(android.graphics.Color.parseColor(colorHex)), shape = CircleShape)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.updatePointerColor(context, colorHex) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- SENSITIVITY CALIBRATION ---
        item {
            val sensitivityMode by viewModel.sensitivityMode.collectAsStateWithLifecycle()
            val sensitivityVal by viewModel.sensitivityValue.collectAsStateWithLifecycle()

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Sensitivity Calibration",
                            tint = Color(0xFFA8C7FA)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "SENSITIVITY CONTROLS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mode Selection: Auto vs Manual
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        listOf(
                            Pair("auto", "Automatic (Auto-Range)"),
                            Pair("manual", "Manual Threshold")
                        ).forEach { (mode, label) ->
                            val isSelected = sensitivityMode == mode
                            Card(
                                modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.updateSensitivityMode(context, mode) },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFA8C7FA).copy(alpha = 0.15f) else Color(0xFF131518)
                                ),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFFA8C7FA) else Color.White,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (sensitivityMode == "auto") {
                        Text(
                            text = "Aura is currently in Auto-Range mode. It will automatically adjust tracking sensitivity and distance scaling on-the-fly dynamically based on your hand distance and ambient sensor frames.",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 16.sp
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Manual Tracking Sensitivity",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "${(sensitivityVal * 100).toInt()}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA8C7FA)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sensitivityVal,
                            onValueChange = { viewModel.updateSensitivityValue(context, it) },
                            valueRange = 0.1f..0.9f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFA8C7FA),
                                activeTrackColor = Color(0xFFA8C7FA)
                            )
                        )
                        Text(
                            text = "Low sensitivity requires higher-confidence landmarks (prevents mis-triggers). High sensitivity allows trackings under low light or far distance.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // --- OFFLINE AI HANDS DATASET CALIBRATION SECTION (NEW) ---
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "OFFLINE HANDS DATASET (PROCESSOR MATCH)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA8C7FA),
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "A built-in dataset of offline hand models. Tap on any item to run the AI landmark tracking algorithm entirely offline using your device processor.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B),
                    lineHeight = 16.sp
                )
            }
        }

        // Horizontal Row of Datasets
        item {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                val handDatasets = listOf(
                    Pair("open_palm", "Open Palm"),
                    Pair("closed_fist", "Closed Fist"),
                    Pair("thumbs_up", "Thumbs Up"),
                    Pair("pointing_up", "Pointing Index"),
                    Pair("victory_peace", "Victory Sign")
                )

                items(handDatasets) { (id, name) ->
                    val isSelected = selectedDatasetId == id
                    val cardBg = if (isSelected) Color(0xFFA8C7FA).copy(alpha = 0.15f) else Color(0xFF1A1C1E)
                    val borderCol = if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)

                    Card(
                        modifier = Modifier
                            .width(130.dp)
                            .clickable { selectedDatasetId = id },
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        border = BorderStroke(1.dp, borderCol),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Tiny Skeleton Canvas Preview
                            val landmarks = when (id) {
                                "open_palm" -> floatArrayOf(
                                    0.5f, 0.85f,
                                    0.35f, 0.8f, 0.28f, 0.74f, 0.22f, 0.68f, 0.16f, 0.62f,
                                    0.38f, 0.55f, 0.37f, 0.42f, 0.36f, 0.32f, 0.35f, 0.22f,
                                    0.48f, 0.52f, 0.48f, 0.38f, 0.48f, 0.28f, 0.48f, 0.18f,
                                    0.58f, 0.55f, 0.59f, 0.42f, 0.6f, 0.32f, 0.61f, 0.22f,
                                    0.66f, 0.6f, 0.68f, 0.5f, 0.7f, 0.42f, 0.72f, 0.34f
                                )
                                "closed_fist" -> floatArrayOf(
                                    0.5f, 0.85f,
                                    0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                                    0.42f, 0.65f, 0.42f, 0.72f, 0.44f, 0.75f, 0.44f, 0.78f,
                                    0.5f, 0.63f, 0.5f, 0.71f, 0.51f, 0.74f, 0.51f, 0.77f,
                                    0.58f, 0.65f, 0.58f, 0.72f, 0.57f, 0.75f, 0.57f, 0.78f,
                                    0.65f, 0.68f, 0.65f, 0.74f, 0.63f, 0.77f, 0.63f, 0.8f
                                )
                                "thumbs_up" -> floatArrayOf(
                                    0.5f, 0.85f,
                                    0.45f, 0.75f, 0.42f, 0.62f, 0.43f, 0.5f, 0.44f, 0.38f,
                                    0.52f, 0.75f, 0.55f, 0.78f, 0.53f, 0.81f, 0.5f, 0.83f,
                                    0.56f, 0.76f, 0.59f, 0.79f, 0.57f, 0.82f, 0.54f, 0.84f,
                                    0.6f, 0.78f, 0.63f, 0.81f, 0.61f, 0.84f, 0.58f, 0.86f,
                                    0.64f, 0.8f, 0.67f, 0.83f, 0.65f, 0.86f, 0.62f, 0.88f
                                )
                                "pointing_up" -> floatArrayOf(
                                    0.5f, 0.85f,
                                    0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                                    0.48f, 0.55f, 0.48f, 0.42f, 0.48f, 0.32f, 0.48f, 0.2f,
                                    0.53f, 0.65f, 0.54f, 0.72f, 0.55f, 0.75f, 0.55f, 0.78f,
                                    0.59f, 0.67f, 0.6f, 0.74f, 0.59f, 0.77f, 0.59f, 0.8f,
                                    0.65f, 0.7f, 0.66f, 0.76f, 0.64f, 0.79f, 0.64f, 0.82f
                                )
                                else -> floatArrayOf(
                                    0.5f, 0.85f,
                                    0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                                    0.43f, 0.55f, 0.41f, 0.42f, 0.39f, 0.32f, 0.37f, 0.22f,
                                    0.53f, 0.55f, 0.55f, 0.42f, 0.57f, 0.32f, 0.59f, 0.22f,
                                    0.59f, 0.67f, 0.6f, 0.74f, 0.59f, 0.77f, 0.59f, 0.8f,
                                    0.65f, 0.7f, 0.66f, 0.76f, 0.64f, 0.79f, 0.64f, 0.82f
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    val bones = listOf(
                                        Pair(0, 1), Pair(0, 5), Pair(0, 9), Pair(0, 13), Pair(0, 17),
                                        Pair(1, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17),
                                        Pair(1, 2), Pair(2, 3), Pair(3, 4),
                                        Pair(5, 6), Pair(6, 7), Pair(7, 8),
                                        Pair(9, 10), Pair(10, 11), Pair(11, 12),
                                        Pair(13, 14), Pair(14, 15), Pair(15, 16),
                                        Pair(17, 18), Pair(18, 19), Pair(19, 20)
                                    )
                                    bones.forEach { (jA, jB) ->
                                        val ax = landmarks[jA * 2] * size.width
                                        val ay = landmarks[jA * 2 + 1] * size.height
                                        val bx = landmarks[jB * 2] * size.width
                                        val by = landmarks[jB * 2 + 1] * size.height
                                        drawLine(
                                            color = if (isSelected) Color(0xFFA8C7FA) else Color(0xFF64748B),
                                            start = androidx.compose.ui.geometry.Offset(ax, ay),
                                            end = androidx.compose.ui.geometry.Offset(bx, by),
                                            strokeWidth = 1.5.dp.toPx()
                                        )
                                    }
                                    for (j in 0 until 21) {
                                        val jx = landmarks[j * 2] * size.width
                                        val jy = landmarks[j * 2 + 1] * size.height
                                        drawCircle(
                                            color = if (j in listOf(4, 8, 12, 16, 20)) Color(0xFF00FF87) else Color.White,
                                            radius = 1.5.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color(0xFFA8C7FA) else Color.White,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // AI Sandbox Offline Calibration Card
        item {
            selectedDatasetId?.let { id ->
                val name = when (id) {
                    "open_palm" -> "Open Palm Model"
                    "closed_fist" -> "Closed Fist Model"
                    "thumbs_up" -> "Thumbs Up Model"
                    "pointing_up" -> "Pointing Index Model"
                    else -> "Victory Sign Model"
                }
                val description = when (id) {
                    "open_palm" -> "All 5 fingers fully extended upwards. Primary hand posture representation for checking detector tracking fidelity and system calibration."
                    "closed_fist" -> "All fingers curled tightly into the center of the palm. Utilized by offline AI algorithms to detect fist/grab commands."
                    "thumbs_up" -> "The thumb is fully extended vertically while the other four fingers remain curled. Triggers swift media or custom system functions."
                    "pointing_up" -> "The index finger points straight up with other fingers clenched, forming a linear cursor. Perfect for fine offline coordinate matching."
                    else -> "Index and middle fingers fully extended to form an open 'V'. Standard posture for Wave and swipe actions matched offline."
                }
                val matchedGesture = when (id) {
                    "open_palm" -> "HOVER"
                    "closed_fist" -> "GRAB"
                    "thumbs_up" -> "SWIPE_UP"
                    "pointing_up" -> "SWIPE_LEFT"
                    else -> "WAVE"
                }
                val mappedActionName = mappings.find { it.gestureId == matchedGesture }?.actionName ?: "NONE (No Action)"
                val mappedActionId = mappings.find { it.gestureId == matchedGesture }?.actionId ?: "NONE"

                val landmarks = when (id) {
                    "open_palm" -> floatArrayOf(
                        0.5f, 0.85f,
                        0.35f, 0.8f, 0.28f, 0.74f, 0.22f, 0.68f, 0.16f, 0.62f,
                        0.38f, 0.55f, 0.37f, 0.42f, 0.36f, 0.32f, 0.35f, 0.22f,
                        0.48f, 0.52f, 0.48f, 0.38f, 0.48f, 0.28f, 0.48f, 0.18f,
                        0.58f, 0.55f, 0.59f, 0.42f, 0.6f, 0.32f, 0.61f, 0.22f,
                        0.66f, 0.6f, 0.68f, 0.5f, 0.7f, 0.42f, 0.72f, 0.34f
                    )
                    "closed_fist" -> floatArrayOf(
                        0.5f, 0.85f,
                        0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                        0.42f, 0.65f, 0.42f, 0.72f, 0.44f, 0.75f, 0.44f, 0.78f,
                        0.5f, 0.63f, 0.5f, 0.71f, 0.51f, 0.74f, 0.51f, 0.77f,
                        0.58f, 0.65f, 0.58f, 0.72f, 0.57f, 0.75f, 0.57f, 0.78f,
                        0.65f, 0.68f, 0.65f, 0.74f, 0.63f, 0.77f, 0.63f, 0.8f
                    )
                    "thumbs_up" -> floatArrayOf(
                        0.5f, 0.85f,
                        0.45f, 0.75f, 0.42f, 0.62f, 0.43f, 0.5f, 0.44f, 0.38f,
                        0.52f, 0.75f, 0.55f, 0.78f, 0.53f, 0.81f, 0.5f, 0.83f,
                        0.56f, 0.76f, 0.59f, 0.79f, 0.57f, 0.82f, 0.54f, 0.84f,
                        0.6f, 0.78f, 0.63f, 0.81f, 0.61f, 0.84f, 0.58f, 0.86f,
                        0.64f, 0.8f, 0.67f, 0.83f, 0.65f, 0.86f, 0.62f, 0.88f
                    )
                    "pointing_up" -> floatArrayOf(
                        0.5f, 0.85f,
                        0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                        0.48f, 0.55f, 0.48f, 0.42f, 0.48f, 0.32f, 0.48f, 0.2f,
                        0.53f, 0.65f, 0.54f, 0.72f, 0.55f, 0.75f, 0.55f, 0.78f,
                        0.59f, 0.67f, 0.6f, 0.74f, 0.59f, 0.77f, 0.59f, 0.8f,
                        0.65f, 0.7f, 0.66f, 0.76f, 0.64f, 0.79f, 0.64f, 0.82f
                    )
                    else -> floatArrayOf(
                        0.5f, 0.85f,
                        0.38f, 0.8f, 0.33f, 0.75f, 0.36f, 0.7f, 0.4f, 0.68f,
                        0.43f, 0.55f, 0.41f, 0.42f, 0.39f, 0.32f, 0.37f, 0.22f,
                        0.53f, 0.55f, 0.55f, 0.42f, 0.57f, 0.32f, 0.59f, 0.22f,
                        0.59f, 0.67f, 0.6f, 0.74f, 0.59f, 0.77f, 0.59f, 0.8f,
                        0.65f, 0.7f, 0.66f, 0.76f, 0.64f, 0.79f, 0.64f, 0.82f
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131518)),
                    border = BorderStroke(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.DeveloperMode,
                                    contentDescription = "AI Memory",
                                    tint = Color(0xFF00FF87)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = name,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 15.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF00FF87).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "100% OFFLINE (CPU)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00FF87)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Large Coordinate Sandbox Grid
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A0C10))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw futuristic radar sweep
                                if (isCalibrating) {
                                    val sweepY = scanLineY * size.height
                                    drawLine(
                                        color = Color(0xFF00FF87).copy(alpha = 0.6f),
                                        start = androidx.compose.ui.geometry.Offset(0f, sweepY),
                                        end = androidx.compose.ui.geometry.Offset(size.width, sweepY),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(Color(0xFF00FF87).copy(alpha = 0.15f), Color.Transparent),
                                            startY = sweepY - 50.dp.toPx(),
                                            endY = sweepY
                                        ),
                                        size = androidx.compose.ui.geometry.Size(size.width, 50.dp.toPx()),
                                        topLeft = androidx.compose.ui.geometry.Offset(0f, sweepY - 50.dp.toPx())
                                    )
                                }

                                // Draw coordinate grids
                                val gridColor = Color(0xFF1E293B).copy(alpha = 0.3f)
                                for (i in 1..4) {
                                    val x = (i / 5f) * size.width
                                    val y = (i / 5f) * size.height
                                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Offset(x, size.height), 1f)
                                    drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), 1f)
                                }

                                // Draw hand bones
                                val bones = listOf(
                                    Pair(0, 1), Pair(0, 5), Pair(0, 9), Pair(0, 13), Pair(0, 17),
                                    Pair(1, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17),
                                    Pair(1, 2), Pair(2, 3), Pair(3, 4),
                                    Pair(5, 6), Pair(6, 7), Pair(7, 8),
                                    Pair(9, 10), Pair(10, 11), Pair(11, 12),
                                    Pair(13, 14), Pair(14, 15), Pair(15, 16),
                                    Pair(17, 18), Pair(18, 19), Pair(19, 20)
                                )

                                val skeletonColor = if (isCalibrating) Color(0xFF00FF87) else Color(0xFFA8C7FA)
                                bones.forEach { (jA, jB) ->
                                    val ax = landmarks[jA * 2] * size.width
                                    val ay = landmarks[jA * 2 + 1] * size.height
                                    val bx = landmarks[jB * 2] * size.width
                                    val by = landmarks[jB * 2 + 1] * size.height
                                    
                                    drawLine(
                                        color = skeletonColor.copy(alpha = 0.4f),
                                        start = androidx.compose.ui.geometry.Offset(ax, ay),
                                        end = androidx.compose.ui.geometry.Offset(bx, by),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawLine(
                                        color = skeletonColor,
                                        start = androidx.compose.ui.geometry.Offset(ax, ay),
                                        end = androidx.compose.ui.geometry.Offset(bx, by),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }

                                // Draw joint nodes
                                for (j in 0 until 21) {
                                    val jx = landmarks[j * 2] * size.width
                                    val jy = landmarks[j * 2 + 1] * size.height

                                    if (j in listOf(4, 8, 12, 16, 20)) {
                                        drawCircle(
                                            color = Color(0xFF00FF87).copy(alpha = 0.3f),
                                            radius = 6.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                        drawCircle(
                                            color = Color(0xFF00FF87),
                                            radius = 3.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                    } else {
                                        drawCircle(
                                            color = Color.White,
                                            radius = 2.dp.toPx(),
                                            center = androidx.compose.ui.geometry.Offset(jx, jy)
                                        )
                                    }
                                }
                            }

                            // Overlay metadata info
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = "GESTURE MATCH: $matchedGesture",
                                    color = Color(0xFF00FF87),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SYSTEM ACTION: $mappedActionName",
                                    color = Color(0xFFA8C7FA),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Trigger actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        isCalibrating = true
                                        delay(1500)
                                        isCalibrating = false
                                        Toast.makeText(
                                            context,
                                            "Offline CPU Calibration successful for $name!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isCalibrating) Color(0xFF0F172A) else Color(0xFFA8C7FA)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isCalibrating
                            ) {
                                Icon(
                                    imageVector = if (isCalibrating) Icons.Default.Sync else Icons.Default.Check,
                                    contentDescription = "Calibrate",
                                    tint = if (isCalibrating) Color.White else Color(0xFF003355),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCalibrating) "CALIBRATING..." else "ALIGN OFFLINE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCalibrating) Color.White else Color(0xFF003355)
                                )
                            }

                            Button(
                                onClick = {
                                    // Trigger the mapped action offline directly!
                                    val serviceIntent = Intent(context, GestureService::class.java).apply {
                                        putExtra("SIMULATE_ACTION", mappedActionId)
                                    }
                                    
                                    Toast.makeText(
                                        context,
                                        "Offline AI triggers simulated gesture: $matchedGesture -> $mappedActionName",
                                        Toast.LENGTH_LONG
                                    ).show()

                                    if (mappedActionId == "TOGGLE_FLASHLIGHT") {
                                        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                                        try {
                                            val rearCameraId = cameraManager.cameraIdList.firstOrNull()
                                            if (rearCameraId != null) {
                                                cameraManager.setTorchMode(rearCameraId, true)
                                                scope.launch {
                                                    delay(1000)
                                                    cameraManager.setTorchMode(rearCameraId, false)
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Log.e("MainActivity", "Flashlight trigger error", e)
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                                shape = RoundedCornerShape(12.dp),
                                enabled = !isCalibrating
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Simulate",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SIMULATE TRIGGER",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomizeScreen(viewModel: MainViewModel) {
    val mappings by viewModel.allMappings.collectAsStateWithLifecycle()
    var selectedMappingForEdit by remember { mutableStateOf<GestureMapping?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "GESTURE TRIGGER CUSTOMIZATION",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF64748B),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap on any hand gesture card to remap it to a different system action instantly.",
                fontSize = 12.sp,
                color = Color(0xFF64748B)
            )
        }

        val categories = remember {
            listOf(
                Pair("PROXIMITY GESTURES (NO CAMERA)", listOf("PROXIMITY_WAVE", "PROXIMITY_HOVER")),
                Pair("OPEN HAND GESTURES", listOf("OPEN_PALM", "WAVE", "SWIPE_LEFT", "SWIPE_RIGHT", "SWIPE_UP", "SWIPE_DOWN")),
                Pair("CLOSED HAND GESTURES", listOf("FIST", "PEACE_SIGN", "ROCK_ON", "THUMBS_UP", "THUMBS_DOWN")),
                Pair("FINGER RAISE GESTURES", listOf("INDEX_RAISED", "MIDDLE_RAISED", "PINKY_RAISED", "INDEX_MIDDLE_RAISED")),
                Pair("PINCH GESTURES", listOf("INDEX_PINCH", "MIDDLE_PINCH", "RING_PINCH", "PINKY_PINCH")),
                Pair("GESTURE COMBINATIONS", listOf("COMBO_FIST_OPEN", "COMBO_PINCH_SWIPE"))
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            categories.forEach { (catName, gestureIds) ->
                val catMappings = mappings.filter { it.gestureId in gestureIds }
                if (catMappings.isNotEmpty()) {
                    item {
                        Text(
                            text = catName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8C7FA),
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(catMappings, key = { it.gestureId }) { mapping ->
                        GestureMappingCard(
                            mapping = mapping,
                            onClick = { selectedMappingForEdit = mapping }
                        )
                    }
                }
            }
        }
    }

    // Modal Selection Dialog for remapping
    selectedMappingForEdit?.let { mapping ->
        AlertDialog(
            onDismissRequest = { selectedMappingForEdit = null },
            title = {
                Text(
                    text = "Map Action: ${mapping.gestureName}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            containerColor = Color(0xFF1A1C1E), // Immersive surface bg
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    Text(
                        text = "Choose what happens when this gesture is detected:",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(viewModel.availableActions) { action ->
                            val isSelected = mapping.actionId == action.id
                            val cardBg = if (isSelected) Color(0xFFA8C7FA).copy(alpha = 0.15f) else Color(0xFF1C2024)
                            val borderCol = if (isSelected) Color(0xFFA8C7FA) else Color.White.copy(alpha = 0.05f)

                            Card(
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, borderCol),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.updateMapping(mapping.gestureId, action.id)
                                        selectedMappingForEdit = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = getIconForAction(action.id),
                                        contentDescription = action.name,
                                        tint = if (isSelected) Color(0xFFA8C7FA) else Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = action.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (isSelected) Color(0xFFA8C7FA) else Color.White
                                        )
                                        Text(
                                            text = action.description,
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMappingForEdit = null }) {
                    Text("CANCEL", color = Color(0xFFA8C7FA))
                }
            }
        )
    }
}

@Composable
fun GestureMappingCard(
    mapping: GestureMapping,
    onClick: () -> Unit
) {
    val actionColor = if (mapping.actionId == "NONE") Color(0xFF64748B) else Color(0xFFA8C7FA)
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("gesture_card_${mapping.gestureId}"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C1E)),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Color(0xFF1C2024), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getIconForGesture(mapping.gestureId),
                        contentDescription = mapping.gestureName,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = mapping.gestureName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Trigger command",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = mapping.actionName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = actionColor
                )
                Text(
                    text = if (mapping.actionId == "NONE") "Disabled" else "Active",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

fun getIconForGesture(gestureId: String): ImageVector {
    return when (gestureId) {
        "SWIPE_LEFT" -> Icons.Default.ArrowBack
        "SWIPE_RIGHT" -> Icons.Default.ArrowForward
        "SWIPE_UP" -> Icons.Default.ArrowUpward
        "SWIPE_DOWN" -> Icons.Default.ArrowDownward
        "WAVE", "PROXIMITY_WAVE" -> Icons.Default.Refresh
        "HOVER", "PROXIMITY_HOVER", "OPEN_PALM" -> Icons.Default.PanTool
        "FIST" -> Icons.Default.BackHand
        "PEACE_SIGN" -> Icons.Default.FrontHand
        "ROCK_ON" -> Icons.Default.SignLanguage
        "THUMBS_UP" -> Icons.Default.ThumbUp
        "THUMBS_DOWN" -> Icons.Default.ThumbDown
        "INDEX_RAISED" -> Icons.Default.BackHand
        "MIDDLE_RAISED" -> Icons.Default.BackHand
        "PINKY_RAISED" -> Icons.Default.BackHand
        "INDEX_PINCH", "MIDDLE_PINCH", "RING_PINCH", "PINKY_PINCH" -> Icons.Default.Pinch
        "COMBO_FIST_OPEN", "COMBO_PINCH_SWIPE" -> Icons.Default.JoinRight
        else -> Icons.Default.Gesture
    }
}

fun getIconForAction(actionId: String): ImageVector {
    return when (actionId) {
        "NONE" -> Icons.Default.Block
        "BACK" -> Icons.Default.ArrowBack
        "HOME" -> Icons.Default.Home
        "RECENTS" -> Icons.Default.Menu
        "PLAY_PAUSE" -> Icons.Default.PlayArrow
        "NEXT_TRACK" -> Icons.Default.SkipNext
        "PREVIOUS_TRACK" -> Icons.Default.SkipPrevious
        "VOLUME_UP" -> Icons.Default.VolumeUp
        "VOLUME_DOWN" -> Icons.Default.VolumeDown
        "TOGGLE_FLASHLIGHT" -> Icons.Default.FlashlightOn
        "SCROLL_UP" -> Icons.Default.KeyboardDoubleArrowUp
        "SCROLL_DOWN" -> Icons.Default.KeyboardDoubleArrowDown
        "SCREENSHOT" -> Icons.Default.CameraAlt
        "LOCK_SCREEN" -> Icons.Default.Lock
        "NOTIFICATIONS" -> Icons.Default.Notifications
        "QUICK_SETTINGS" -> Icons.Default.Settings
        else -> Icons.Default.PlayArrow
    }
}
