package com.example.tvapp

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.tvapp.data.AppPreferences
import com.example.tvapp.ui.ThemeCatalog
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = AppPreferences(newBase.applicationContext).language
        super.attachBaseContext(localeContext(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Тема выбирается до super.onCreate: окно и весь setContentView наследуют её.
        setTheme(ThemeCatalog.styleFor(ThemeCatalog.savedTheme(this), prefersBlackStage()))
        super.onCreate(savedInstanceState)
    }

    /** Чёрная сцена (плеер): палитра темы сохраняется, но фон чёрный и текст светлый. */
    protected open fun prefersBlackStage(): Boolean = false

    @Suppress("AppBundleLocaleChanges")
    private fun localeContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
