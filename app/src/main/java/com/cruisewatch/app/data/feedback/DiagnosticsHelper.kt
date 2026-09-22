package com.cruisewatch.app.data.feedback

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.cruisewatch.app.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Generic app/device diagnostics. Never collects accounts, tokens, or files. */
object DiagnosticsHelper {

    fun collect(context: Context, appName: String = "CruiseWatch"): String {
        val pm = context.packageManager
        val packageName = context.packageName
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull()
        @Suppress("DEPRECATION")
        val versionCode = pkgInfo?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toString() else it.versionCode.toString()
        } ?: "unknown"
        val versionName = pkgInfo?.versionName ?: BuildConfig.VERSION_NAME

        val locale = Locale.getDefault().toString()
        val timeZone = TimeZone.getDefault().id
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())

        val (freeStorage, totalStorage) = storageInfo()
        val (freeMemory, totalMemory) = memoryInfo(context)

        return buildString {
            appendLine("## Diagnostics")
            appendLine()
            appendLine("- App: $appName")
            appendLine("- Package: $packageName")
            appendLine("- Version: $versionName ($versionCode)")
            appendLine("- Device: ${Build.BRAND} ${Build.MODEL}")
            appendLine("- Manufacturer: ${Build.MANUFACTURER}")
            appendLine("- Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("- Locale: $locale")
            appendLine("- Time Zone: $timeZone")
            appendLine("- Storage Free/Total: $freeStorage / $totalStorage")
            appendLine("- Memory Free/Total: $freeMemory / $totalMemory")
            appendLine("- Reported At: $timestamp")
        }.trimEnd()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "unknown"
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        return if (gb >= 1) "%.1f GB".format(gb) else "%d MB".format(bytes / (1024 * 1024))
    }

    private fun storageInfo(): Pair<String, String> {
        return runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            val blockSize = stat.blockSizeLong
            val free = stat.availableBlocksLong * blockSize
            val total = stat.blockCountLong * blockSize
            formatBytes(free) to formatBytes(total)
        }.getOrElse { "unknown" to "unknown" }
    }

    private fun memoryInfo(context: Context): Pair<String, String> {
        return runCatching {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            formatBytes(info.availMem) to formatBytes(info.totalMem)
        }.getOrElse { "unknown" to "unknown" }
    }
}
