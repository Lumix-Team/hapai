package studio.creater120.hapai.data

import android.content.Context
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ServerManager {
    private const val PREF_SERVERS = "imported_servers"
    private const val PREF_ACTIVE_SERVER = "active_server_id"
    private val gson = Gson()

    fun getServers(context: Context): List<VServer> {
        val json = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_SERVERS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<VServer>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addServer(context: Context, server: VServer) {
        val list = getServers(context).toMutableList()
        list.add(server.copy(sortOrder = list.size))
        save(context, list)
    }

    fun removeServer(context: Context, serverId: String) {
        val list = getServers(context).toMutableList()
        list.removeAll { it.id == serverId }
        save(context, list)
    }

    fun getActiveServer(context: Context): VServer? {
        val activeId = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_ACTIVE_SERVER, null) ?: return null
        return getServers(context).find { it.id == activeId }
    }

    fun setActiveServer(context: Context, server: VServer?) {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putString(PREF_ACTIVE_SERVER, server?.id)
        }
    }

    fun importFromClipboard(context: Context, text: String): Boolean {
        val server = VServer.fromLink(text.trim()) ?: return false
        val existing = getServers(context)
        if (existing.any { it.link == server.link }) return true
        addServer(context, server)
        return true
    }

    private fun save(context: Context, servers: List<VServer>) {
        PreferenceManager.getDefaultSharedPreferences(context).edit(commit = true) {
            putString(PREF_SERVERS, gson.toJson(servers))
        }
    }
}
