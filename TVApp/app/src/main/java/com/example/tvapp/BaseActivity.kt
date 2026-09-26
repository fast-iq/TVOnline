package com.example.tvapp

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import com.example.tvapp.data.AppPreferences
import java.util.Locale

abstract class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val lang = AppPreferences(newBase.applicationContext).language
        super.attachBaseContext(localeContext(newBase, lang))
    }

    @Suppress("AppBundleLocaleChanges")
    private fun localeContext(context: Context, languageCode: String): Context {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }
}
