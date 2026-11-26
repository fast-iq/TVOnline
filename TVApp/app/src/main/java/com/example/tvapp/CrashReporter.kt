package com.example.tvapp

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val DIR_NAME = "crash_logs"
    private const val PREFIX = "crash-"
    private const val SUFFIX = ".txt"
    private const val MAX_REPORTS = 5

    fun install(context: Context, versionName: String) {
        val dir = crashDir(context)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeReport(dir, thread, throwable, versionName, System.currentTimeMillis())
            } catch (e: Throwable) {
                Log.e(TAG, "unable to write crash report: ${e.message}")
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logPending(context: Context) {
        val reports = pendingReports(context)
        if (reports.isEmpty()) return
        val newest = reports.first()
        Log.w(TAG, "${reports.size} unanswered crash report(s), newest: ${newest.absolutePath}")
        try {
            newest.readLines().take(15).forEach { Log.w(TAG, it) }
        } catch (e: Exception) {
            Log.w(TAG, "cannot read ${newest.name}: ${e.message}")
        }
    }

    fun pendingReports(context: Context): List<File> {
        val dir = crashDir(context)
        val files = dir.listFiles()?.filter { it.name.startsWith(PREFIX) && it.name.endsWith(SUFFIX) }.orEmpty()
        val stale = prune(files.map { it.name }, MAX_REPORTS)
        for (name in stale) {
            try {
                File(dir, name).delete()
            } catch (e: Exception) {
                Log.w(TAG, "cannot prune $name: ${e.message}")
            }
        }
        return files.sortedByDescending { it.name }.take(MAX_REPORTS)
    }

    private fun writeReport(dir: File, thread: Thread, throwable: Throwable, versionName: String, nowMs: Long) {
        if (!dir.exists() && !dir.mkdirs()) return
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(nowMs))
        val file = File(dir, "$PREFIX$stamp-${thread.name.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}.txt")
        file.writeText(formatReport(thread, throwable, versionName, nowMs))
        val names = dir.listFiles()?.map { it.name }.orEmpty()
        for (stale in prune(names, MAX_REPORTS)) {
            File(dir, stale).delete()
        }
    }

    fun formatReport(
        thread: Thread,
        throwable: Throwable,
        versionName: String,
        nowMs: Long,
        model: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
        osVersion: String = android.os.Build.VERSION.RELEASE,
        sdkInt: Int = android.os.Build.VERSION.SDK_INT
    ): String {
        val trace = StringWriter()
        throwable.printStackTrace(PrintWriter(trace))
        return buildString {
            appendLine("time_ms=$nowMs")
            appendLine("app_version=$versionName")
            appendLine("thread=${thread.name}")
            appendLine("model=$model")
            appendLine("android=$osVersion (api $sdkInt)")
            appendLine()
            append(trace.toString())
        }
    }

    internal fun crashDir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Имена сверх лимита (самые старые) — то, что нужно удалить. Имена содержат фиксированную дату-время. */
    internal fun prune(fileNames: List<String>, max: Int): List<String> {
        if (fileNames.size <= max) return emptyList()
        return fileNames.sorted().take(fileNames.size - max)
    }
}
