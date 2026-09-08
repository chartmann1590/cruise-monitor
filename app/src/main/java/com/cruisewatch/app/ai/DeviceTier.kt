package com.cruisewatch.app.ai

import android.app.ActivityManager
import android.content.Context

enum class DeviceTier { LOW, HIGH }

/** Recommends a model size based on total device RAM — LiteRT LLMs need to fit comfortably alongside the OS. */
object DeviceCapability {
    fun recommend(context: Context): DeviceTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalGb = info.totalMem / (1024.0 * 1024.0 * 1024.0)
        return if (totalGb >= 6.0) DeviceTier.HIGH else DeviceTier.LOW
    }

    fun totalRamGb(context: Context): Double {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024.0 * 1024.0 * 1024.0)
    }
}
