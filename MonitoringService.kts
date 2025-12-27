package com.uptimeguardian.core.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.uptimeguardian.R
import com.uptimeguardian.core.data.repository.MonitorRepository
import com.uptimeguardian.core.domain.model.Monitor
import com.uptimeguardian.core.work.MonitorWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class MonitoringService : Service() {
    
    @Inject
    lateinit var monitorRepository: MonitorRepository
    
    @Inject
    lateinit var workManager: WorkManager
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    
    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
        startMonitoring()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "UptimeGuardian::MonitoringWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L /*10 minutes*/)
        }
    }
    
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.monitoring_active))
            .setSmallIcon(R.drawable.ic_service)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun startMonitoring() {
        serviceScope.launch {
            monitorRepository.getActiveMonitors().collect { monitors ->
                scheduleMonitorWorkers(monitors)
            }
        }
    }
    
    private fun scheduleMonitorWorkers(monitors: List<Monitor>) {
        monitors.forEach { monitor ->
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false)
                .setRequiresStorageNotLow(false)
                .build()
            
            val request = PeriodicWorkRequestBuilder<MonitorWorker>(
                monitor.interval.toLong(), TimeUnit.SECONDS
            )
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        "monitor_id" to monitor.id,
                        "monitor_url" to monitor.url,
                        "monitor_type" to monitor.type.name,
                        "timeout" to monitor.timeout
                    )
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30,
                    TimeUnit.SECONDS
                )
                .addTag("monitor_${monitor.id}")
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "monitor_${monitor.id}",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
        serviceScope.cancel()
    }
    
    companion object {
        private const val CHANNEL_ID = "monitoring_service"
        private const val NOTIFICATION_ID = 1001
        
        fun startService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, MonitoringService::class.java)
            context.stopService(intent)
        }
    }
}