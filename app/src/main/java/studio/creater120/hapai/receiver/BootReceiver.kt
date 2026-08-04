package studio.creater120.hapai.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.SystemClock
import studio.creater120.hapai.data.Mode
import studio.creater120.hapai.services.ServiceManager
import studio.creater120.hapai.utility.getPreferences
import studio.creater120.hapai.utility.mode

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_REBOOT ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {

            // for A15, todo: use wasForceStopped
            if (SystemClock.elapsedRealtime() > 5 * 60 * 1000) {
                return
            }

            val preferences = context.getPreferences()
            val autorunEnabled = preferences.getBoolean("autostart", false)

            if(autorunEnabled) {
                when (preferences.mode()) {
                    Mode.VPN -> {
                        if (VpnService.prepare(context) == null) {
                            ServiceManager.start(context, Mode.VPN)
                        }
                    }

                    Mode.Proxy -> ServiceManager.start(context, Mode.Proxy)
                }
            }
        }
    }
}