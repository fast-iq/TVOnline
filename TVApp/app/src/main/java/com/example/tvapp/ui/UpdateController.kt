package com.example.tvapp.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.tvapp.R
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.data.UpdateChecker
import com.example.tvapp.data.UpdateInfo
import com.example.tvapp.data.UpdateInstaller
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Тихая проверка обновлений (не чаще раза в сутки) + диалог «скачать».
 * Вызывается из [MainActivity] на старте; сетевой запрос — в IO, диалог — только
 * на живой активности.
 */
internal class UpdateController(
    private val activity: Activity,
    private val scope: CoroutineScope,
    private val preferences: AppPreferences
) {

    fun checkIfNeeded() {
        if (System.currentTimeMillis() - preferences.lastUpdateCheckMs < UPDATE_CHECK_INTERVAL_MS) return
        scope.launch {
            try {
                val info = withContext(Dispatchers.IO) { UpdateChecker.check(activity) }
                preferences.lastUpdateCheckMs = System.currentTimeMillis()
                if (info != null && !activity.isFinishing) showUpdateDialog(info)
            } catch (e: Exception) {
                Log.w("UpdateController", "update check failed: ${e.message}")
            }
        }
    }

    private fun showUpdateDialog(info: UpdateInfo) {
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.update_available, info.version))
            .setMessage(info.notes.ifBlank { activity.getString(R.string.update_no_notes) })
            .setPositiveButton(R.string.update_download) { _, _ ->
                try {
                    val apk = info.apkUrl
                    if (!apk.isNullOrBlank()) {
                        UpdateInstaller.start(activity, scope, apk, info.url)
                    } else if (info.url.isNotBlank()) {
                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    }
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(activity, R.string.update_failed, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }

    companion object {
        private const val UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}
