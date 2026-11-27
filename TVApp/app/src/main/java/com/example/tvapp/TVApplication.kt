package com.example.tvapp

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.request.RequestOptions
import com.example.tvapp.ui.ThemeCatalog
import com.example.tvapp.ui.ThemeIcons

class TVApplication : Application() {

    companion object {
        // Лимиты кэша Glide: по умолчанию диск 250 МБ — расточительно для ТВ-приставок,
        // а логотипы мелкие и декодируются в RGB_565 (вдвое меньше памяти на битмап).
        private const val MEMORY_CACHE_BYTES = 20L * 1024 * 1024
        private const val DISK_CACHE_BYTES = 100L * 1024 * 1024

        /** Задержка перед переключением алиасов после ухода в фон (мс). */
        private const val ICON_SWAP_DELAY_MS = 1_200L
    }

    private var startedActivities = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val iconSwapRunnable = Runnable { applyLauncherIconNow() }

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this, appVersionName())
        CrashReporter.logPending(this)
        NotificationHelper.ensureChannel(this)
        syncLauncherIcon()
        configureGlide()
        registerActivityLifecycleCallbacks(backgroundWatcher)
    }

    /**
     * Иконка лаунчера должна соответствовать сохранённой теме — в т.ч. после
     * установки/обновления, когда PackageManager сбрасывает состояния компонентов
     * на манифестные. Здесь только ВКЛЮЧАЕМ нужный алиас: отключение текущего
     * лаунчер-алиаса на лету система наказывает убийством процесса (даже с
     * DONT_KILL_APP), а мы как раз на нём возможно и запустились. Отключение
     * остальных — в [onAppBackgrounded], когда убийство безвредно.
     */
    private fun syncLauncherIcon() {
        try {
            ThemeIcons.enableForLaunch(this, ThemeCatalog.savedTheme(this))
        } catch (e: Exception) {
            Log.w("TVApplication", "launcher icon sync failed: ${e.message}")
        }
    }

    /** Все активности остановлены (пользователь ушёл/домой) — можно менять алиасы. */
    internal fun onAppBackgrounded() {
        mainHandler.removeCallbacks(iconSwapRunnable)
        mainHandler.postDelayed(iconSwapRunnable, ICON_SWAP_DELAY_MS)
    }

    /** Вернулись на передний — отменяем отложенное переключение (алиас снова «живой»). */
    internal fun onAppForegrounded() {
        mainHandler.removeCallbacks(iconSwapRunnable)
    }

    internal fun applyLauncherIconNow() {
        try {
            ThemeIcons.apply(this, ThemeCatalog.savedTheme(this))
        } catch (e: Exception) {
            Log.w("TVApplication", "launcher icon swap failed: ${e.message}")
        }
    }

    private val backgroundWatcher = object : ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivities++
            onAppForegrounded()
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivities--
            if (startedActivities <= 0) {
                startedActivities = 0
                onAppBackgrounded()
            }
        }

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
        override fun onActivityResumed(activity: Activity) = Unit
        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit
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
