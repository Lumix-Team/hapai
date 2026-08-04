package studio.creater120.hapai.activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.color.DynamicColors
import studio.creater120.hapai.utility.SettingsUtils
import studio.creater120.hapai.utility.getPreferences
import studio.creater120.hapai.utility.getStringNotNull

abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getPreferences()

        val lang = prefs.getStringNotNull("language", "system")
        SettingsUtils.setLang(lang)

        val theme = prefs.getStringNotNull("app_theme", "system")
        SettingsUtils.setTheme(theme)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(this)
        }

        super.onCreate(savedInstanceState)
    }

}