package com.audacia.orion

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.audacia.orion.service.OrionService
import com.audacia.orion.service.OrionSession
import com.audacia.orion.ui.HudScreen
import com.audacia.orion.ui.HudViewModel
import com.audacia.orion.ui.theme.ORIONTheme

class MainActivity : ComponentActivity() {

    private val hudViewModel: HudViewModel by viewModels()

    // Estado reactivo para la optimización de batería
    private val isBatteryOptimizationIgnoredState = mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        OrionService.start(this)
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateBatteryOptimizationState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        updateBatteryOptimizationState()
        requestNotificationPermissionAndStartService()

        setContent {
            ORIONTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    HudScreen(
                        viewModel = hudViewModel,
                        onRequestBatteryOptimization = { requestIgnoreBatteryOptimization() },
                        isBatteryOptimizationIgnored = isBatteryOptimizationIgnoredState.value,
                        onOpenAppSettings = { openAppSettings() },
                        onCloseApp = { closeApp() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptimizationState()
    }

    private fun updateBatteryOptimizationState() {
        isBatteryOptimizationIgnoredState.value = checkIsBatteryOptimizationIgnored()
    }

    private fun requestNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                OrionService.start(this)
            }
        } else {
            OrionService.start(this)
        }
    }

    private fun checkIsBatteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
            return powerManager.isIgnoringBatteryOptimizations(packageName)
        }
        return true
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                batteryOptimizationLauncher.launch(intent)
            } catch (e: Exception) {
                try {
                    val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    batteryOptimizationLauncher.launch(fallbackIntent)
                } catch (ignored: Exception) {}
            }
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (ignored: Exception) {}
    }

    private fun closeApp() {
        OrionService.stop(this)
        OrionSession.stop()
        OrionSession.close()
        finishAffinity()
    }
}
