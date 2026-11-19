package com.audacia.orion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.audacia.orion.MainActivity
import com.audacia.orion.R
import com.audacia.orion.vision.ConnectionStatus
import com.audacia.orion.vision.VisionPipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Servicio en primer plano (ForegroundService) para Modo Bolsillo de ORION v6.
 *
 * Mantiene la ejecución continua del pipeline de visión, la inferencia sobre IA y
 * la emisión de retroalimentación sensorial (vibración háptica, audio estéreo y buzzer)
 * durante 30+ minutos con la pantalla del celular completamente apagada.
 *
 * Cuenta con:
 * - Tipo de servicio: FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE.
 * - WakeLock parcial de CPU (PARTIAL_WAKE_LOCK) para evitar suspensión por el SO.
 * - Notificación persistente con acción directa de "Detener".
 */
class OrionService : Service() {

    inner class LocalBinder : Binder() {
        val service: OrionService get() = this@OrionService
        val pipeline: VisionPipeline get() = this@OrionService.pipeline
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var wakeLock: PowerManager.WakeLock? = null
    lateinit var pipeline: VisionPipeline
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        pipeline = OrionSession.getPipeline(applicationContext)

        acquireLocks()
        createNotificationChannel()
        startForegroundServiceWithNotification("Iniciando asistencia visual...")

        // Actualizar la notificación persistente únicamente al cambiar el estado de conexión
        // para eliminar llamadas periódicas IPC a NotificationManager en el hilo principal.
        var lastStatus: ConnectionStatus? = null

        pipeline.state.onEach { state ->
            if (state.connectionStatus != lastStatus) {
                lastStatus = state.connectionStatus

                val message = when (state.connectionStatus) {
                    ConnectionStatus.STREAMING -> "Stream activo (${state.endpoint?.ip}) • Navegación en curso"
                    ConnectionStatus.SEARCHING -> "Buscando cámara XIAO..."
                    ConnectionStatus.CONNECTING -> "Conectando con cámara..."
                    ConnectionStatus.ERROR -> "Interrupción: ${state.statusMessage}"
                    ConnectionStatus.IDLE -> "En espera"
                }
                updateNotification(message)
            }
        }.launchIn(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            pipeline.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun acquireLocks() {
        // Bloqueo parcial de CPU (PARTIAL_WAKE_LOCK) para mantener viva la inferencia y audio con pantalla apagada.
        // NOTA: NO se adquiere WifiLock en modo Hotspot/AP, ya que en Android 10+ (MediaTek/HiOS)
        // WIFI_MODE_FULL_LOW_LATENCY altera el ahorro de energía del chip y produce caídas de FPS a 1 FPS.
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "orion:vision_pocket_lock"
        )?.apply {
            setReferenceCounted(false)
            acquire(12 * 60 * 60 * 1000L) // 12 horas máx
        }
    }

    private fun releaseLocks() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ORION Asistencia Visual",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene la navegación activa en modo bolsillo con la pantalla apagada"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val activityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OrionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ORION Activo (Modo Bolsillo)")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_radar)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityPendingIntent)
            .addAction(R.drawable.ic_disconnect, "Detener", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun startForegroundServiceWithNotification(text: String) {
        val notification = buildNotification(text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        pipeline.stop()
        serviceScope.cancel()
        releaseLocks()
    }

    companion object {
        const val CHANNEL_ID = "orion_pocket_service"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.audacia.orion.action.STOP"

        @Volatile
        var instance: OrionService? = null
            private set

        fun start(context: Context) {
            val intent = Intent(context, OrionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OrionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
