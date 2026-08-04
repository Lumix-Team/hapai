package studio.creater120.hapai.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import studio.creater120.hapai.data.AppStatus
import studio.creater120.hapai.data.Mode
import studio.creater120.hapai.data.START_ACTION
import studio.creater120.hapai.data.STOP_ACTION
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ServiceManager {
    private val TAG: String = ServiceManager::class.java.simpleName
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start(context: Context, mode: Mode) {
        when (mode) {
            Mode.VPN -> {
                Log.i(TAG, "Starting VPN")
                val intent = Intent(context, HapaiVpnService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                Log.i(TAG, "Starting proxy")
                val intent = Intent(context, HapaiProxyService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun stop(context: Context) {
        val (_, mode) = appStatus
        when (mode) {
            Mode.VPN -> {
                Log.i(TAG, "Stopping VPN")
                val intent = Intent(context, HapaiVpnService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                Log.i(TAG, "Stopping proxy")
                val intent = Intent(context, HapaiProxyService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun restart(context: Context, mode: Mode) {
        if (appStatus.first == AppStatus.Running) {
            stop(context)
            scope.launch {
                val startTime = System.currentTimeMillis()
                while (System.currentTimeMillis() - startTime < 3000L) {
                    if (appStatus.first == AppStatus.Halted) break
                    delay(100)
                }
                start(context, mode)
            }
        }
    }
}
