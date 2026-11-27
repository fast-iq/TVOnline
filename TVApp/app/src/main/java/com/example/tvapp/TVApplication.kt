package com.example.tvapp

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.request.RequestOptions

class TVApplication : Application() {

    companion object {
        // Лимиты кэша Glide: по умолчанию диск 250 МБ — расточительно для ТВ-приставок,
        // а логотипы мелкие и декодируются в RGB_565 (вдвое меньше памяти на битмап).
        private const val MEMORY_CACHE_BYTES = 20L * 1024 * 1024
        private const val DISK_CACHE_BYTES = 100L * 1024 * 1024
    }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, appVersionName())
        CrashReporter.logPending(this)
        NotificationHelper.ensureChannel(this)
        configureGlide()
    }

    /**
     * Ранняя (до первого Glide.with) настройка Glide: явные размеры кэшей и RGB_565
     * по умолчанию. Glide.init ленивая — тяжёлая инициализация откладывается до
     * первого запроса, так что main-поток это не тормозит.
     * Glide помечает init/isInitialized как @VisibleForTests (канонический путь —
     * AppGlideModule + процессор аннотаций), но при встроенном Kotlin AGP 9 процессора
     * нет — это единственный публичный способ конфигурации.
     */
    @SuppressLint("VisibleForTests")
    private fun configureGlide() {
        if (Glide.isInitialized()) return
        try {
            Glide.init(
                this,
                GlideBuilder()
                    .setMemoryCache(LruResourceCache(MEMORY_CACHE_BYTES))
                    .setDiskCache(InternalCacheDiskCacheFactory(this, DISK_CACHE_BYTES))
                    .setDefaultRequestOptions(RequestOptions().format(DecodeFormat.PREFER_RGB_565))
            )
        } catch (e: Exception) {
            Log.w("TVApplication", "Glide config failed: ${e.message}")
        }
    }

    private fun appVersionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    } catch (e: Exception) {
        Log.w("TVApplication", "versionName unavailable: ${e.message}")
        "unknown"
    }
}
