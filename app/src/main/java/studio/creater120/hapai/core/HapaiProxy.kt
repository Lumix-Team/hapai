package studio.creater120.hapai.core

class HapaiProxy {
    companion object {
        init {
            System.loadLibrary("byedpi")
        }
    }

    fun startProxy(preferences: HapaiProxyPreferences): Int {
        val args = prepareArgs(preferences)
        return jniStartProxy(args)
    }

    fun stopProxy(): Int {
        return jniStopProxy()
    }

    private fun prepareArgs(preferences: HapaiProxyPreferences): Array<String> =
        when (preferences) {
            is HapaiProxyCmdPreferences -> preferences.args
            is HapaiProxyUIPreferences -> preferences.uiargs
        }

    private external fun jniStartProxy(args: Array<String>): Int
    private external fun jniStopProxy(): Int
    external fun jniForceClose(): Int
}