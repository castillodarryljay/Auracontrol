package com.example

import android.content.Context
import android.content.Intent
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "REALTIME OS TRACKER TELEMETRY",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF64748B),
            letterSpacing = 1.5.sp
        )

        if (!isServiceRunning) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(32.dp))
                    .background(Color(0xFF1A1C1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = "Off",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Telemetry Offline",
                        fontWeight = FontWeight.Bold,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Activate Aura touchless service to see real-time hand-difference scanner stream.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            // Live Motion Grid Visualizer Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .border(1.dp, Color(0xFFA8C7FA).copy(alpha = 0.2f), RoundedCornerShape(32.dp))
                    .background(Color(0xFF0F0F12))
            ) {
                if (isServiceRunning) {
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
                }

                // Background futuristic coordinate grid drawing
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw futuristic hand tracking skeleton
                    handSkeleton?.let { skeleton ->
                        if (skeleton.size >= 42) {
                            val bones = listOf(
                                // Wrist to MCPs
                                Pair(0, 1), Pair(0, 5), Pair(0, 9), Pair(0, 13), Pair(0, 17),
                                // MCP to MCP (Palm closure)
                                Pair(1, 5), Pair(5, 9), Pair(9, 13), Pair(13, 17),
                                // Thumb
                                Pair(1, 2), Pair(2, 3), Pair(3, 4),
                                // Index
                                Pair(5, 6), Pair(6, 7), Pair(7, 8),
                                // Middle
                                Pair(9, 10), Pair(10, 11), Pair(11, 12),
                                // Ring
                                Pair(13, 14), Pair(14, 15), Pair(15, 16),
                                // Pinky
                                Pair(17, 18), Pair(18, 19), Pair(19, 20)
                            )

                            val glowColor = Color(0xFF00FF87)
                            val boneColor = Color(0xFFA8C7FA)
                            val jointColor = Color(0xFFC2E7FF)

                            // 1. Draw bones (lines connecting joints)
                            bones.forEach { (jA, jB) ->
                                val ax = skeleton[jA * 2] * size.width
                                val ay = skeleton[jA * 2 + 1] * size.height
                                val bx = skeleton[jB * 2] * size.width
                                val by = skeleton[jB * 2 + 1] * size.height
                                
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

                            // 2. Draw joints (dots)
                            for (j in 0 until 21) {
                                val jx = skeleton[j * 2] * size.width
                                val jy = skeleton[j * 2 + 1] * size.height

                                // Is it a fingertip? (4, 8, 12, 16, 20)
                                if (j == 4 || j == 8 || j == 12 || j == 16 || j == 20) {
                                    // Pulsing tip glow
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
                                    // Regular joint dot
                                    drawCircle(
                                        color = jointColor,
                                        radius = 3.dp.toPx(),
                                        center = androidx.compose.ui.geometry.Offset(jx, jy)
                                    )
                                }
                            }

                            // Draw specific finger HUD labels for Thumb and Pointer/Index
                            val thumbTipX = skeleton[4 * 2] * size.width
                            val thumbTipY = skeleton[4 * 2 + 1] * size.height

                            val pointerTipX = skeleton[8 * 2] * size.width
                            val pointerTipY = skeleton[8 * 2 + 1] * size.height

                            // Draw Thumb HUD label (Leader line + pill text)
                            val thumbLabelX = thumbTipX - 70f
                            val thumbLabelY = thumbTipY - 70f

                            drawContext.canvas.nativeCanvas.drawLine(
                                thumbTipX, thumbTipY,
                                thumbLabelX, thumbLabelY,
                                leaderLinePaint
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
                                thumbText,
                                thumbLabelX,
                                thumbLabelY + rect.height() / 2f - 4f,
                                textPaint
                            )

                            // Draw Pointer/Index HUD label (Leader line + pill text)
                            val pointerLabelX = pointerTipX + 70f
                            val pointerLabelY = pointerTipY - 70f

                            drawContext.canvas.nativeCanvas.drawLine(
                                pointerTipX, pointerTipY,
                                pointerLabelX, pointerLabelY,
                                leaderLinePaint
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
                                pointerText,
                                pointerLabelX,
                                pointerLabelY + rect.height() / 2f - 4f,
                                textPaint
                            )
                        }
                    }
                }

                // Cybernetic scanning HUD elements overlays
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

                    // Centered Gesture Detection HUD Alert
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

            // Lower Status Stats Block
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
                            text = "LAST GESTURE RECORDED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = lastGesture,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
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
                            text = lastAction,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFA8C7FA)
                        )
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

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mappings, key = { it.gestureId }) { mapping ->
                GestureMappingCard(
                    mapping = mapping,
                    onClick = { selectedMappingForEdit = mapping }
                )
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
        "WAVE" -> Icons.Default.Refresh
        "HOVER" -> Icons.Default.RadioButtonChecked
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
        else -> Icons.Default.PlayArrow
    }
}
