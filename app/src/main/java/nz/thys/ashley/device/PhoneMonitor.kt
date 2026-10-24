package nz.thys.ashley.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import android.os.StatFs

/** One reading of the phone's state, shown in the strip under the context meter. */
data class PhoneHealth(
    /** Battery temperature in °C - the best skin-temperature proxy Android gives apps. */
    val batteryTempC: Float?,
    /** PowerManager.THERMAL_STATUS_* (0 none ... 6 shutdown). */
    val thermalStatus: Int,
    /** 0 = cool, 1.0 = the phone will start throttling hard. NaN/null if the phone doesn't report it. */
    val thermalHeadroom: Float?,
    val availRamBytes: Long,
    val totalRamBytes: Long,
    val lowMemory: Boolean,
    /** RAM used by Ashley itself (PSS). GPU memory for the model may not be included. */
    val appRamBytes: Long,
    val batteryPct: Int?,
    val charging: Boolean,
    val freeStorageBytes: Long,
) {
    val thermalLabel: String
        get() = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Cool"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Hot"
            PowerManager.THERMAL_STATUS_SEVERE -> "Very hot"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            else -> "Emergency"
        }
}

/** Limits from agent.json ("phone comes first"). */
data class PhoneLimits(
    val maxTempC: Float,
    /** Stop / refuse at this PowerManager thermal status (3 = severe). */
    val stopThermalStatus: Int,
    /** Free RAM that must remain after loading a model. */
    val reserveRamBytes: Long,
)

class PhoneMonitor(private val context: Context) {

    private val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun read(): PhoneHealth {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val rawTemp = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val headroom = runCatching { pm.getThermalHeadroom(10) }.getOrNull()?.takeIf { !it.isNaN() }
        val storage = runCatching { StatFs(context.getExternalFilesDir(null)?.path ?: context.filesDir.path).availableBytes }.getOrDefault(0L)
        return PhoneHealth(
            batteryTempC = if (rawTemp == Int.MIN_VALUE) null else rawTemp / 10f,
            thermalStatus = pm.currentThermalStatus,
            thermalHeadroom = headroom,
            availRamBytes = mi.availMem,
            totalRamBytes = mi.totalMem,
            lowMemory = mi.lowMemory,
            appRamBytes = Debug.getPss() * 1024L,
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else null,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            freeStorageBytes = storage,
        )
    }

    companion object {
        /** Why an on-device model must not run right now, or null if it may. */
        fun tooHot(h: PhoneHealth, limits: PhoneLimits): String? = when {
            h.thermalStatus >= limits.stopThermalStatus ->
                "The phone is ${h.thermalLabel.lowercase()} (Android thermal level ${h.thermalStatus}). Let it cool down, or switch to the PC model."
            h.batteryTempC != null && h.batteryTempC >= limits.maxTempC ->
                String.format("The phone is at %.1f °C (limit %.0f °C). Let it cool down, or switch to the PC model.", h.batteryTempC, limits.maxTempC)
            else -> null
        }

        /** Why a model of [modelBytes] must not be loaded, or null if there is room. */
        fun notEnoughRam(h: PhoneHealth, modelBytes: Long, limits: PhoneLimits): String? {
            if (modelBytes <= 0) return null
            // Weights (memory-mapped, so roughly the file size) plus the reserve the phone keeps for itself.
            val need = modelBytes + limits.reserveRamBytes
            return if (h.availRamBytes < need) String.format(
                "Not enough free memory: the model needs about %.1f GB and %.1f GB is free. Close other apps, or use a smaller model or the PC model.",
                need / 1e9, h.availRamBytes / 1e9,
            ) else null
        }
    }
}
