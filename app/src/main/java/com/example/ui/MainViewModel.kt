package com.example.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.GestureMapping
import com.example.data.GestureRepository
import com.example.service.GestureAccessibilityService
import com.example.service.GestureService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repository: GestureRepository) : ViewModel() {

    val allMappings: StateFlow<List<GestureMapping>> = repository.allMappings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    private val _isAccessibilityRunning = MutableStateFlow(false)
    val isAccessibilityRunning: StateFlow<Boolean> = _isAccessibilityRunning.asStateFlow()

    private val _hasCameraPermission = MutableStateFlow(false)
    val hasCameraPermission: StateFlow<Boolean> = _hasCameraPermission.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(false)
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    // Real-time telemetry streams from the service for the in-app visualizer
    val serviceLastGesture: StateFlow<String> = GestureService.lastGesture
    val serviceLastAction: StateFlow<String> = GestureService.lastAction
    val serviceMotionDensity: StateFlow<Float> = GestureService.motionDensity
    val serviceCentroid: StateFlow<Pair<Float, Float>?> = GestureService.centroid
    val serviceMotionGrid: StateFlow<FloatArray> = GestureService.motionGrid
    val serviceHandDetected: StateFlow<Boolean> = GestureService.handDetected
    val serviceHandSkeleton: StateFlow<FloatArray?> = GestureService.handSkeleton
    val serviceImageWidth: StateFlow<Int> = GestureService.imageWidth
    val serviceImageHeight: StateFlow<Int> = GestureService.imageHeight

    // Customizable Preferences (Saved to SharedPreferences for real-time Sync with background GestureService)
    private val _pointerEnabled = MutableStateFlow(true)
    val pointerEnabled: StateFlow<Boolean> = _pointerEnabled.asStateFlow()

    private val _pointerMode = MutableStateFlow("absolute") // "absolute" or "joystick"
    val pointerMode: StateFlow<String> = _pointerMode.asStateFlow()

    private val _pointerColor = MutableStateFlow("#00FF87")
    val pointerColor: StateFlow<String> = _pointerColor.asStateFlow()

    private val _pointerSize = MutableStateFlow(48)
    val pointerSize: StateFlow<Int> = _pointerSize.asStateFlow()

    private val _pointerShape = MutableStateFlow("crosshair") // "dot", "ring", "arrow", "crosshair"
    val pointerShape: StateFlow<String> = _pointerShape.asStateFlow()

    private val _sensitivityMode = MutableStateFlow("auto") // "auto" or "manual"
    val sensitivityMode: StateFlow<String> = _sensitivityMode.asStateFlow()

    private val _sensitivityValue = MutableStateFlow(0.5f)
    val sensitivityValue: StateFlow<Float> = _sensitivityValue.asStateFlow()

    private val _proximityModeEnabled = MutableStateFlow(false)
    val proximityModeEnabled: StateFlow<Boolean> = _proximityModeEnabled.asStateFlow()

    private val _batterySaverEnabled = MutableStateFlow(true)
    val batterySaverEnabled: StateFlow<Boolean> = _batterySaverEnabled.asStateFlow()

    private val _batterySaverTimeout = MutableStateFlow(30) // in seconds
    val batterySaverTimeout: StateFlow<Int> = _batterySaverTimeout.asStateFlow()

    val availableActions = listOf(
        ActionItem("NONE", "No Action", "Do nothing when this gesture occurs"),
        ActionItem("BACK", "Go Back", "Simulates the system Back navigation action (Accessibility required)"),
        ActionItem("HOME", "Go Home", "Launches the home screen"),
        ActionItem("RECENTS", "Recent Apps", "Opens the recent apps overview screen (Accessibility required)"),
        ActionItem("PLAY_PAUSE", "Play/Pause Media", "Toggles music or video playback in any active player"),
        ActionItem("NEXT_TRACK", "Next Track", "Skips to the next song/video in any media player"),
        ActionItem("PREVIOUS_TRACK", "Previous Track", "Goes back to the previous song/video in any media player"),
        ActionItem("VOLUME_UP", "Volume Up", "Increases the media sound volume"),
        ActionItem("VOLUME_DOWN", "Volume Down", "Decreases the media sound volume"),
        ActionItem("TOGGLE_FLASHLIGHT", "Toggle Flashlight", "Turns the phone rear LED flashlight on/off"),
        ActionItem("SCROLL_UP", "Scroll Up", "Simulates an upward swipe to scroll contents down (Accessibility required)"),
        ActionItem("SCROLL_DOWN", "Scroll Down", "Simulates a downward swipe to scroll contents up (Accessibility required)"),
        ActionItem("SCREENSHOT", "Take Screenshot", "Captures the current screen display immediately (Accessibility required)"),
        ActionItem("LOCK_SCREEN", "Lock Screen", "Puts the device to sleep instantly (Accessibility required)"),
        ActionItem("NOTIFICATIONS", "Notifications Panel", "Pulls down the system notification drawer (Accessibility required)"),
        ActionItem("QUICK_SETTINGS", "Quick Settings", "Opens the system quick settings panel (Accessibility required)")
    )

    fun loadPreferences(context: Context) {
        val prefs = context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE)
        _pointerEnabled.value = prefs.getBoolean("pointer_enabled", true)
        _pointerMode.value = prefs.getString("pointer_mode", "absolute") ?: "absolute"
        _pointerColor.value = prefs.getString("pointer_color", "#00FF87") ?: "#00FF87"
        _pointerSize.value = prefs.getInt("pointer_size", 48)
        _pointerShape.value = prefs.getString("pointer_shape", "crosshair") ?: "crosshair"
        _sensitivityMode.value = prefs.getString("sensitivity_mode", "auto") ?: "auto"
        _sensitivityValue.value = prefs.getFloat("sensitivity_value", 0.5f)
        _proximityModeEnabled.value = prefs.getBoolean("proximity_mode_enabled", false)
        _batterySaverEnabled.value = prefs.getBoolean("battery_saver_enabled", true)
        _batterySaverTimeout.value = prefs.getInt("battery_saver_timeout", 30)
    }

    fun updateBatterySaverEnabled(context: Context, enabled: Boolean) {
        _batterySaverEnabled.value = enabled
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putBoolean("battery_saver_enabled", enabled).apply()
    }

    fun updateBatterySaverTimeout(context: Context, timeout: Int) {
        _batterySaverTimeout.value = timeout
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putInt("battery_saver_timeout", timeout).apply()
    }

    fun updateProximityModeEnabled(context: Context, enabled: Boolean) {
        _proximityModeEnabled.value = enabled
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putBoolean("proximity_mode_enabled", enabled).apply()
    }

    fun updatePointerEnabled(context: Context, enabled: Boolean) {
        _pointerEnabled.value = enabled
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putBoolean("pointer_enabled", enabled).apply()
    }

    fun updatePointerMode(context: Context, mode: String) {
        _pointerMode.value = mode
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putString("pointer_mode", mode).apply()
    }

    fun updatePointerColor(context: Context, colorHex: String) {
        _pointerColor.value = colorHex
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putString("pointer_color", colorHex).apply()
    }

    fun updatePointerSize(context: Context, size: Int) {
        _pointerSize.value = size
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putInt("pointer_size", size).apply()
    }

    fun updatePointerShape(context: Context, shape: String) {
        _pointerShape.value = shape
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putString("pointer_shape", shape).apply()
    }

    fun updateSensitivityMode(context: Context, mode: String) {
        _sensitivityMode.value = mode
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putString("sensitivity_mode", mode).apply()
    }

    fun updateSensitivityValue(context: Context, value: Float) {
        _sensitivityValue.value = value
        context.getSharedPreferences("aura_prefs", Context.MODE_PRIVATE).edit().putFloat("sensitivity_value", value).apply()
    }

    fun checkAllPermissionsAndState(context: Context) {
        loadPreferences(context)
        _isServiceRunning.value = GestureService.isServiceRunning
        _isAccessibilityRunning.value = GestureAccessibilityService.isServiceRunning

        _hasCameraPermission.value = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        _hasOverlayPermission.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun toggleService(context: Context) {
        val isRunning = GestureService.isServiceRunning
        val serviceIntent = Intent(context, GestureService::class.java)

        if (isRunning) {
            context.stopService(serviceIntent)
            _isServiceRunning.value = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            _isServiceRunning.value = true
        }
    }

    fun updateMapping(gestureId: String, actionId: String) {
        val actionItem = availableActions.find { it.id == actionId } ?: return
        viewModelScope.launch {
            repository.updateMapping(gestureId, actionId, actionItem.name)
        }
    }

    fun initializeDefaults() {
        viewModelScope.launch {
            repository.populateDefaultsIfNeeded()
        }
    }
}

data class ActionItem(
    val id: String,
    val name: String,
    val description: String
)

class MainViewModelFactory(private val repository: GestureRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
