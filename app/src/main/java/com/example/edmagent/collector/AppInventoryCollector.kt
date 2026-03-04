package com.example.edmagent.collector

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.example.edmagent.data.AppInfoDto

object AppInventoryCollector {

    fun collect(context: Context): List<AppInfoDto> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_META_DATA
        } else {
            PackageManager.GET_META_DATA
        }

        return try {
            pm.getInstalledPackages(flags).map { pkg ->
                AppInfoDto(
                    appName = pkg.applicationInfo?.loadLabel(pm).toString(),
                    packageName = pkg.packageName,
                    versionName = pkg.versionName ?: "unknown",
                    versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        pkg.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        pkg.versionCode.toLong()
                    },
                    installSource = getInstallSource(pm, pkg.packageName),
                    isSystemApp = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getInstallSource(pm: PackageManager, packageName: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pm.getInstallSourceInfo(packageName).installingPackageName ?: "unknown"
            } else {
                @Suppress("DEPRECATION")
                pm.getInstallerPackageName(packageName) ?: "unknown"
            }
        } catch (e: Exception) {
            "unknown"
        }
    }
}