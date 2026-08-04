package studio.creater120.hapai.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import studio.creater120.hapai.R
import studio.creater120.hapai.data.AppStatus
import studio.creater120.hapai.databinding.ActivityMainBinding
import studio.creater120.hapai.fragments.ByeDpiFragment
import studio.creater120.hapai.fragments.HapaiVpnFragment
import studio.creater120.hapai.services.ServiceManager
import studio.creater120.hapai.services.appStatus
import studio.creater120.hapai.utility.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.system.exitProcess
import androidx.core.content.edit

class MainActivity : BaseActivity() {
    private lateinit var binding: ActivityMainBinding

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName
        private const val BATTERY_OPTIMIZATION_REQUESTED = "battery_optimization_requested"

        private fun collectLogs(): String? =
            try {
                Runtime.getRuntime()
                    .exec("logcat *:D -d")
                    .inputStream.bufferedReader()
                    .use { it.readText() }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to collect logs", e)
                null
            }
    }

    private val logsRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { log ->
            lifecycleScope.launch(Dispatchers.IO) {
                val logs = collectLogs()
                if (logs == null) {
                    Toast.makeText(this@MainActivity, R.string.logs_failed, Toast.LENGTH_SHORT).show()
                } else {
                    val uri = log.data?.data ?: run {
                        android.util.Log.e(TAG, "No data in result")
                        return@launch
                    }
                    contentResolver.openOutputStream(uri)?.use {
                        try { it.write(logs.toByteArray()) } catch (e: IOException) { android.util.Log.e(TAG, "Failed to save logs", e) }
                    } ?: android.util.Log.e(TAG, "Failed to open output stream")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavigation!!.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_vpn -> {
                    switchFragment(HapaiVpnFragment())
                    supportActionBar?.title = "Happi VPN"
                    true
                }
                R.id.nav_byedpi -> {
                    switchFragment(ByeDpiFragment())
                    supportActionBar?.title = "Happi DPI"
                    true
                }
                else -> false
            }
        }

        if (savedInstanceState == null) {
            binding.bottomNavigation!!.selectedItemId = R.id.nav_vpn
            supportActionBar?.title = "Happi VPN"
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            requestBatteryOptimization()
        }

        if (getPreferences().getBoolean("auto_connect", false) && appStatus.first != AppStatus.Running) {
            ServiceManager.start(this, getPreferences().mode())
        }

        ShortcutUtils.update(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) requestBatteryOptimization()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val (status, _) = appStatus
        return when (item.itemId) {
            R.id.action_save_logs -> {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TITLE, "byedpi.log")
                }
                logsRegister.launch(intent)
                true
            }
            R.id.action_close_app -> {
                if (status == AppStatus.Running) ServiceManager.stop(this)
                finishAffinity()
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(0)
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun requestBatteryOptimization() {
        val preferences = getPreferences()
        val alreadyRequested = preferences.getBoolean(BATTERY_OPTIMIZATION_REQUESTED, false)
        if (!alreadyRequested && !BatteryUtils.isOptimizationDisabled(this)) {
            BatteryUtils.requestBatteryOptimization(this)
            preferences.edit { putBoolean(BATTERY_OPTIMIZATION_REQUESTED, true) }
        }
    }
}
