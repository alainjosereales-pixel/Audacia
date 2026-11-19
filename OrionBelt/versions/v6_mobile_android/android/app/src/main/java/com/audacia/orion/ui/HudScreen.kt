package com.audacia.orion.ui

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audacia.orion.R
import com.audacia.orion.feedback.Alert
import com.audacia.orion.feedback.FeedbackSettings
import com.audacia.orion.resilience.ThermalThrottleLevel
import com.audacia.orion.resilience.WatchdogState
import com.audacia.orion.vision.Action
import com.audacia.orion.vision.BenchmarkMetrics
import com.audacia.orion.vision.ConnectionStatus
import com.audacia.orion.vision.InferenceBackend
import com.audacia.orion.vision.VisionState
import com.audacia.orion.vision.Zone
import com.audacia.orion.vision.ZoneResult

@Composable
fun HudScreen(
    viewModel: HudViewModel,
    modifier: Modifier = Modifier,
    onRequestBatteryOptimization: () -> Unit = {},
    isBatteryOptimizationIgnored: Boolean = true,
    onOpenAppSettings: () -> Unit = {},
    onCloseApp: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val scrollState = rememberScrollState()
    var showBatteryTip by remember { mutableStateOf(true) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        DetectionSettingsDialog(
            nearThreshold = state.nearThreshold,
            initialIp = state.endpoint?.ip ?: viewModel.getLastKnownIp().orEmpty(),
            isBenchmarkRunning = state.isBenchmarkRunning,
            benchmarkProgress = state.benchmarkProgress,
            benchmarkPhase = state.benchmarkPhase,
            benchmarkMetrics = state.benchmarkMetrics,
            onThresholdChange = { viewModel.updateNearThreshold(it) },
            onConnectManualIp = { ip ->
                showSettingsDialog = false
                if (ip.isNotBlank()) {
                    viewModel.connectDirect(ip)
                }
            },
            onRunBenchmark = { viewModel.runBenchmark() },
            onDismiss = { showSettingsDialog = false }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(14.dp)
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. BARRA SUPERIOR: ESTADO, CONTROL TÉRMICO Y BACKEND ---
        TopHudHeader(
            state = state,
            onBackendToggle = {
                val next = if (state.activeBackend == InferenceBackend.GPU) InferenceBackend.CPU else InferenceBackend.GPU
                viewModel.switchBackend(next)
            },
            onOpenDetectionSettings = { showSettingsDialog = true },
            onRequestBatteryOptimization = onRequestBatteryOptimization,
            isBatteryOptimizationIgnored = isBatteryOptimizationIgnored,
            onOpenAppSettings = onOpenAppSettings,
            onCloseApp = onCloseApp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Sugerencia para excluir optimización de batería en Modo Bolsillo
        if (!isBatteryOptimizationIgnored && showBatteryTip) {
            PocketModeBatteryTipCard(
                onConfigure = onRequestBatteryOptimization,
                onDismiss = { showBatteryTip = false }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // --- 2. BANNER DE NAVEGACIÓN Y ALERTA (FORWARD / STOP / WATCHDOG) ---
        ActionAlertBanner(
            zoneResult = state.zoneResult,
            alert = state.activeAlert,
            watchdogState = state.watchdogState
        )

        Spacer(modifier = Modifier.height(10.dp))

        // --- 3. TELEMETRÍA (FPS, INFERENCIA, LATENCIA) ---
        TelemetryRow(state = state)

        Spacer(modifier = Modifier.height(12.dp))

        // --- 4. VISUALES: STREAM DE CÁMARA Y MAPA MAGMA ---
        VisualStreamsSection(
            cameraBitmap = state.cameraBitmap,
            depthBitmap = state.depthBitmap
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- 5. BARRAS ESPACIALES POR ZONAS (IZQ, CENTRO, DER) ---
        SpatialZonesCard(
            zoneResult = state.zoneResult,
            nearThreshold = state.nearThreshold,
            onOpenThresholdSettings = { showSettingsDialog = true }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- 6. RETROALIMENTACIÓN SENSORIAL (TIRA COMPACTA PROFESIONAL) ---
        SensoryFeedbackCard(
            settings = state.feedbackSettings,
            onUpdateSettings = { viewModel.updateFeedbackSettings(it) }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // --- 7. CONTROLES DE ENLACE ---
        ControlButtonsSection(
            status = state.connectionStatus,
            onToggleAutoSearch = {
                if (state.connectionStatus == ConnectionStatus.SEARCHING ||
                    state.connectionStatus == ConnectionStatus.CONNECTING ||
                    state.connectionStatus == ConnectionStatus.STREAMING
                ) {
                    viewModel.stop()
                } else {
                    viewModel.startAutoDiscovery()
                }
            },
            onRefreshSearch = { viewModel.startAutoDiscovery() },
            onDisconnect = { viewModel.stop() }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Estado textual del sistema
        Text(
            text = state.statusMessage,
            color = Color(0xFF64748B),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TopHudHeader(
    state: VisionState,
    onBackendToggle: () -> Unit,
    onOpenDetectionSettings: () -> Unit = {},
    onRequestBatteryOptimization: () -> Unit = {},
    isBatteryOptimizationIgnored: Boolean = true,
    onOpenAppSettings: () -> Unit = {},
    onCloseApp: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val statusDotColor = when (state.connectionStatus) {
                ConnectionStatus.STREAMING -> Color(0xFF10B981) // Verde
                ConnectionStatus.SEARCHING -> Color(0xFFF59E0B) // Ámbar
                ConnectionStatus.CONNECTING -> Color(0xFF6366F1) // Índigo
                ConnectionStatus.ERROR -> Color(0xFFEF4444) // Rojo
                ConnectionStatus.IDLE -> Color(0xFF64748B) // Gris
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusDotColor, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ORION v6",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                val statusLabel = when (state.connectionStatus) {
                    ConnectionStatus.STREAMING -> "STREAM EN VIVO"
                    ConnectionStatus.SEARCHING -> "BUSCANDO CÁMARA..."
                    ConnectionStatus.CONNECTING -> "CONECTANDO..."
                    ConnectionStatus.ERROR -> "ERROR DE ENLACE"
                    ConnectionStatus.IDLE -> "EN ESPERA"
                }
                Text(
                    text = statusLabel,
                    color = statusDotColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Indicador de Throttling Térmico si se activa
            if (state.thermalLevel != ThermalThrottleLevel.NOMINAL) {
                val (thermalColor, thermalText) = when (state.thermalLevel) {
                    ThermalThrottleLevel.MODERATE -> Pair(Color(0xFFF59E0B), "12 FPS")
                    ThermalThrottleLevel.SEVERE -> Pair(Color(0xFFEF4444), "8 FPS")
                    else -> Pair(Color(0xFF10B981), "NOMINAL")
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(thermalColor.copy(alpha = 0.15f))
                        .border(1.dp, thermalColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "TERM: $thermalText",
                        color = thermalColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Chip selector de Backend (GPU / CPU)
            val isGpu = state.activeBackend == InferenceBackend.GPU
            val backendColor = if (isGpu) Color(0xFF10B981) else Color(0xFFF59E0B)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(backendColor.copy(alpha = 0.15f))
                    .border(1.dp, backendColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable { onBackendToggle() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(backendColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isGpu) "GPU ACCEL" else "CPU (4H)",
                        color = backendColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Menú de 3 puntos (Permisos de segundo plano / Cerrar app)
            var showOverflowMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(
                    onClick = { showOverflowMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = "Opciones",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    modifier = Modifier
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "Ajustes de Detección e IP",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Umbral: ${(state.nearThreshold * 100).toInt()}% • IP / Benchmark",
                                    fontSize = 10.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        },
                        onClick = {
                            showOverflowMenu = false
                            onOpenDetectionSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    text = "Segundo Plano (Batería)",
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBatteryOptimizationIgnored) "Permitido (Sin restricciones)" else "Restringido (Optimizado)",
                                    fontSize = 10.sp,
                                    color = if (isBatteryOptimizationIgnored) Color(0xFF10B981) else Color(0xFFF59E0B)
                                )
                            }
                        },
                        onClick = {
                            showOverflowMenu = false
                            onRequestBatteryOptimization()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Permisos del Sistema", fontSize = 12.sp, color = Color.White)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onOpenAppSettings()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Cerrar Aplicación", fontSize = 12.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                        },
                        onClick = {
                            showOverflowMenu = false
                            onCloseApp()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ActionAlertBanner(
    zoneResult: ZoneResult?,
    alert: Alert?,
    watchdogState: WatchdogState = WatchdogState.OK
) {
    val isLost = watchdogState == WatchdogState.LOST
    val isStop = zoneResult?.action == Action.STOP && !isLost
    val bgColor by animateColorAsState(
        targetValue = when {
            isLost -> Color(0xFF991B1B)
            isStop -> Color(0xFFDC2626)
            else -> Color(0xFF047857)
        },
        animationSpec = tween(durationMillis = 200),
        label = "alert_bg_anim"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(
                        when {
                            isLost -> R.drawable.ic_disconnect
                            isStop -> R.drawable.ic_alert_stop
                            else -> R.drawable.ic_forward
                        }
                    ),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = when {
                            isLost -> "SEÑAL PERDIDA"
                            isStop -> "STOP DETECTADO"
                            else -> "AVANZAR"
                        },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = when {
                            isLost -> "Sin frames en > 3.5s • Reconectando..."
                            isStop -> {
                                if (alert != null) {
                                    "Nivel ${alert.level} (${alert.intervalMs} ms) • Zona ${alert.zone.name}"
                                } else {
                                    val zoneName = zoneResult?.nearestZone?.name ?: "OBSTÁCULO FRONTAL"
                                    "Zona crítica: $zoneName"
                                }
                            }
                            else -> "Trayectoria libre de obstáculos"
                        },
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp
                    )
                }
            }

            if (isStop && !isLost) {
                val pct = ((zoneResult?.intensity ?: 0f) * 100).toInt()
                Text(
                    text = "$pct%",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun TelemetryRow(state: VisionState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "VELOCIDAD",
            value = String.format("%.1f FPS", state.fps),
            color = Color(0xFF38BDF8)
        )
        MetricCard(
            modifier = Modifier.weight(1f),
            label = "INFERENCIA",
            value = "${state.inferenceMs} ms",
            color = Color(0xFFA78BFA)
        )
        MetricCard(
            modifier = Modifier.weight(1.2f),
            label = "TOTAL LATENCIA",
            value = "${state.totalLatencyMs} ms",
            color = Color(0xFF34D399)
        )
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, color = Color(0xFF64748B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun VisualStreamsSection(
    cameraBitmap: Bitmap?,
    depthBitmap: Bitmap?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Vista de Cámara
        StreamCard(
            modifier = Modifier.weight(1f),
            title = "CÁMARA (XIAO)",
            bitmap = cameraBitmap
        )

        // Vista de Mapa de Profundidad (Magma)
        StreamCard(
            modifier = Modifier.weight(1f),
            title = "PROFUNDIDAD (MAGMA)",
            bitmap = depthBitmap
        )
    }
}

@Composable
fun StreamCard(
    modifier: Modifier = Modifier,
    title: String,
    bitmap: Bitmap?
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = Color(0xFF94A3B8),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF090D16)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null && !bitmap.isRecycled) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = "Sin señal",
                        color = Color(0xFF475569),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun SpatialZonesCard(
    zoneResult: ZoneResult?,
    nearThreshold: Float = 0.60f,
    onOpenThresholdSettings: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SEGMENTACIÓN ESPACIAL POR ZONAS",
                    color = Color(0xFF94A3B8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "UMBRAL: ${(nearThreshold * 100).toInt()}%",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onOpenThresholdSettings() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            val left = zoneResult?.left ?: 0.0f
            val center = zoneResult?.center ?: 0.0f
            val right = zoneResult?.right ?: 0.0f

            ZoneProgressBar(
                name = "IZQUIERDA",
                value = left,
                nearThreshold = nearThreshold,
                isNearest = zoneResult?.nearestZone == Zone.LEFT
            )
            Spacer(modifier = Modifier.height(5.dp))
            ZoneProgressBar(
                name = "CENTRO",
                value = center,
                nearThreshold = nearThreshold,
                isNearest = zoneResult?.nearestZone == Zone.CENTER
            )
            Spacer(modifier = Modifier.height(5.dp))
            ZoneProgressBar(
                name = "DERECHA",
                value = right,
                nearThreshold = nearThreshold,
                isNearest = zoneResult?.nearestZone == Zone.RIGHT
            )
        }
    }
}

@Composable
fun ZoneProgressBar(
    name: String,
    value: Float,
    nearThreshold: Float = 0.60f,
    isNearest: Boolean
) {
    val barColor = when {
        isNearest -> Color(0xFFEF4444)
        value > nearThreshold -> Color(0xFFF59E0B)
        else -> Color(0xFF38BDF8)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            color = if (isNearest) Color(0xFFEF4444) else Color(0xFF94A3B8),
            fontSize = 10.sp,
            fontWeight = if (isNearest) FontWeight.Bold else FontWeight.Normal,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(80.dp)
        )

        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = Color(0xFF1E293B)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "${(value * 100).toInt()}%",
            color = Color.White,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(32.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun SensoryFeedbackCard(
    settings: FeedbackSettings,
    onUpdateSettings: (FeedbackSettings) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
        border = BorderStroke(1.dp, Color(0xFF1E293B))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RETROALIMENTACIÓN SENSORIAL",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                val activeCount = listOf(
                    settings.hapticEnabled,
                    settings.audioEnabled,
                    settings.voiceEnabled
                ).count { it }
                Text(
                    text = "$activeCount/3 ACTIVOS",
                    color = Color(0xFF38BDF8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CompactFeedbackToggle(
                    modifier = Modifier.weight(1f),
                    drawableRes = R.drawable.ic_vibrate,
                    label = "Háptica",
                    enabled = settings.hapticEnabled,
                    onToggle = { onUpdateSettings(settings.copy(hapticEnabled = !settings.hapticEnabled)) }
                )
                CompactFeedbackToggle(
                    modifier = Modifier.weight(1f),
                    drawableRes = R.drawable.ic_audio,
                    label = "Audio",
                    enabled = settings.audioEnabled,
                    onToggle = { onUpdateSettings(settings.copy(audioEnabled = !settings.audioEnabled)) }
                )
                CompactFeedbackToggle(
                    modifier = Modifier.weight(1f),
                    drawableRes = R.drawable.ic_voice,
                    label = "Voz",
                    enabled = settings.voiceEnabled,
                    onToggle = { onUpdateSettings(settings.copy(voiceEnabled = !settings.voiceEnabled)) }
                )
            }
        }
    }
}

@Composable
fun CompactFeedbackToggle(
    modifier: Modifier = Modifier,
    drawableRes: Int,
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val activeColor = Color(0xFF10B981)
    val inactiveBorder = Color(0xFF1E293B)
    val bgColor = if (enabled) activeColor.copy(alpha = 0.12f) else Color(0xFF0F172A)
    val borderColor = if (enabled) activeColor.copy(alpha = 0.6f) else inactiveBorder
    val iconColor = if (enabled) activeColor else Color(0xFF64748B)
    val textColor = if (enabled) Color.White else Color(0xFF64748B)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(drawableRes),
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(15.dp)
                )
                if (enabled) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .background(activeColor, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = if (enabled) FontWeight.Bold else FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = if (enabled) "ON" else "OFF",
                color = if (enabled) activeColor else Color(0xFF475569),
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun ControlButtonsSection(
    status: ConnectionStatus,
    onToggleAutoSearch: () -> Unit,
    onRefreshSearch: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isAutoActive = status == ConnectionStatus.SEARCHING ||
            status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.STREAMING

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 1. Botón de Búsqueda Automática (Activo por defecto)
            val autoBgColor = if (isAutoActive) Color(0xFF0284C7) else Color(0xFF1E293B)
            val autoBorderColor = if (isAutoActive) Color(0xFF38BDF8) else Color(0xFF334155)
            Button(
                onClick = onToggleAutoSearch,
                modifier = Modifier
                    .weight(1.15f)
                    .height(42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = autoBgColor),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, autoBorderColor)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_radar),
                    contentDescription = null,
                    tint = if (isAutoActive) Color.White else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isAutoActive) "Búsqueda: ON" else "Búsqueda: OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }

            // 2. Botón Reintentar Autoconexión (Refresh)
            OutlinedButton(
                onClick = onRefreshSearch,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF131C31))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_refresh),
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Reintentar",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
            }
        }

        // 3. Botón Desconectar (visible cuando está conectado/streaming o conectando)
        if (status == ConnectionStatus.STREAMING || status == ConnectionStatus.CONNECTING) {
            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color(0xFF475569))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_disconnect),
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Desconectar",
                    fontSize = 11.sp,
                    color = Color(0xFFF87171),
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun PocketModeBatteryTipCard(
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C31)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Modo Bolsillo Continuo",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Para evitar que Android duerma la detección con la pantalla apagada, puedes desactivar el ahorro de batería.",
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Text("Omitir", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
                Button(
                    onClick = onConfigure,
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("Ajustar", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DetectionSettingsDialog(
    nearThreshold: Float,
    initialIp: String,
    isBenchmarkRunning: Boolean,
    benchmarkProgress: Float,
    benchmarkPhase: String,
    benchmarkMetrics: BenchmarkMetrics?,
    onThresholdChange: (Float) -> Unit,
    onConnectManualIp: (String) -> Unit,
    onRunBenchmark: () -> Unit,
    onDismiss: () -> Unit
) {
    var ipText by remember(initialIp) { mutableStateOf(initialIp) }

    AlertDialog(
        onDismissRequest = {
            if (!isBenchmarkRunning) onDismiss()
        },
        containerColor = Color(0xFF0F172A),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFCBD5E1),
        title = {
            Text(
                text = "AJUSTES DE DETECCIÓN E IP",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF38BDF8)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Control de umbral de cercanía
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Umbral de Cercanía: ${(nearThreshold * 100).toInt()}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        TextButton(
                            onClick = { onThresholdChange(0.60f) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = "Restablecer (60%)",
                                fontSize = 10.sp,
                                color = Color(0xFF38BDF8),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        text = "Menor % avisa desde más lejos; mayor % solo al estar muy cerca (rango 40% - 90%).",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8),
                        lineHeight = 13.sp
                    )
                    Slider(
                        value = nearThreshold,
                        onValueChange = onThresholdChange,
                        valueRange = 0.40f..0.90f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF0284C7),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )
                }

                // 2. Conexión manual por IP (opcional / respaldo)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Conexión Manual XIAO (Opcional)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = { ipText = it.trim() },
                        placeholder = {
                            Text(
                                text = "Ej: 192.168.43.120",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { onConnectManualIp(ipText) },
                        enabled = ipText.isNotBlank() && !isBenchmarkRunning,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Conectar a IP Manual",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // 3. Benchmark Interactivo de Hardware (GPU vs CPU)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Benchmark de Rendimiento IA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White
                        )
                        if (benchmarkMetrics != null && !isBenchmarkRunning) {
                            Text(
                                text = "Hora: ${benchmarkMetrics.completedAtTime}",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onRunBenchmark,
                        enabled = !isBenchmarkRunning,
                        modifier = Modifier.fillMaxWidth().height(36.dp),
                        border = BorderStroke(
                            1.dp,
                            if (isBenchmarkRunning) Color(0xFF38BDF8) else Color(0xFFA855F7)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = when {
                                isBenchmarkRunning -> "Evaluando Hardware... ${(benchmarkProgress * 100).toInt()}%"
                                benchmarkMetrics != null -> "Volver a Ejecutar Benchmark"
                                else -> "Iniciar Benchmark GPU vs CPU"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isBenchmarkRunning) Color(0xFF38BDF8) else Color(0xFFD8B4FE),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Barra de progreso en vivo durante el benchmark
                    if (isBenchmarkRunning) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = benchmarkPhase.ifBlank { "Preparando prueba..." },
                                        fontSize = 9.sp,
                                        color = Color(0xFF38BDF8),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "${(benchmarkProgress * 100).toInt()}%",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { benchmarkProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF38BDF8),
                                    trackColor = Color(0xFF0F172A)
                                )
                            }
                        }
                    }

                    // Tarjeta comparativa de resultados al finalizar
                    if (benchmarkMetrics != null && !isBenchmarkRunning) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val gpuBetter = benchmarkMetrics.gpuAvailable &&
                                        benchmarkMetrics.gpuMedianMs < benchmarkMetrics.cpuMedianMs
                                val recommendation = if (gpuBetter) {
                                    val speedup = benchmarkMetrics.cpuMedianMs / benchmarkMetrics.gpuMedianMs
                                    "ÓPTIMO: GPU (${String.format("%.2f", speedup)}x más rápida que CPU)"
                                } else {
                                    "ÓPTIMO: CPU 4 Hilos"
                                }
                                Text(
                                    text = recommendation,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF4ADE80),
                                    fontFamily = FontFamily.Monospace
                                )

                                // Fila GPU
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "GPU (FP16):",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF38BDF8),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = if (benchmarkMetrics.gpuAvailable) {
                                            "${String.format("%.1f", benchmarkMetrics.gpuMedianMs)} ms (p95: ${String.format("%.0f", benchmarkMetrics.gpuP95Ms)} ms | ~${String.format("%.1f", benchmarkMetrics.gpuFps)} FPS)"
                                        } else {
                                            "No disponible"
                                        },
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                // Fila CPU
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "CPU (4H):",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFF59E0B),
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        text = "${String.format("%.1f", benchmarkMetrics.cpuMedianMs)} ms (p95: ${String.format("%.0f", benchmarkMetrics.cpuP95Ms)} ms | ~${String.format("%.1f", benchmarkMetrics.cpuFps)} FPS)",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBenchmarkRunning
            ) {
                Text(
                    text = "Cerrar",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isBenchmarkRunning) Color(0xFF475569) else Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    )
}
