package dev.liox.ambermusic.diagnostics

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat

/** One-shot snapshots; never poll or wake the player just to draw a settings page. */
data class DeviceHealthSnapshot(
    val model: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdk: Int,
    val hyperOsVersion: String?,
    val notificationPermission: Boolean,
    val batteryUnrestricted: Boolean?,
    val ownProcessPssMb: Int?,
    val cpuPercent: Double? = null,
    val missedFramesPercent: Double? = null
)

object RedmiDiagnostics {
    fun snapshot(context: Context): DeviceHealthSnapshot {
        val app = context.applicationContext
        val battery = app.getSystemService(PowerManager::class.java)
        val am = app.getSystemService(ActivityManager::class.java)
        val pss = runCatching {
            am?.getProcessMemoryInfo(intArrayOf(android.os.Process.myPid()))
                ?.firstOrNull()?.totalPss?.div(1024)
        }.getOrNull()
        val notifications = NotificationManagerCompat.from(app).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 ||
                app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
        return DeviceHealthSnapshot(
            model = Build.MODEL.orEmpty(), manufacturer = Build.MANUFACTURER.orEmpty(),
            androidVersion = Build.VERSION.RELEASE.orEmpty(), sdk = Build.VERSION.SDK_INT,
            hyperOsVersion = null, // Avoid hidden Xiaomi APIs; read exact version via ADB report.
            notificationPermission = notifications,
            batteryUnrestricted = battery?.isIgnoringBatteryOptimizations(app.packageName),
            ownProcessPssMb = pss
        )
    }
}
