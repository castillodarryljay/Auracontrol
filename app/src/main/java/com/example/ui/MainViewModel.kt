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
        ActionItem("SCROLL_DOWN", "Scroll Down", "Simulates a downward swipe to scroll contents up (Accessibility required)")
    )

    fun checkAllPermissionsAndState(context: Context) {
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
