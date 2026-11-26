package com.example.tvapp

import android.app.Application
import android.util.Log

class TVApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, appVersionName())
        CrashReporter.logPending(this)
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        Log.w("TVApplication", "versionName unavailable: ${e.message}")
        "unknown"
    }
}
