package studio.creater120.hapai.data

data class VServer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val link: String,
    val uuid: String,
    val address: String,
    val port: String,
    val name: String,
    val security: String = "",
    val encryption: String = "none",
    val protocolType: String = "tcp",
    val headerType: String = "",
    val flow: String = "",
    val fp: String = "",
    val sortOrder: Int = 0
) {
    companion object {
        fun fromLink(link: String): VServer? {
            return try {
                val configJson = happicore.Happicore.parseVLESS(link)
                val cfg = com.google.gson.Gson().fromJson(configJson, VServerConfig::class.java)
                VServer(
                    link = link,
                    uuid = cfg.uuid,
                    address = cfg.address,
                    port = cfg.port,
                    name = cfg.name.ifBlank { "$cfg.address:$cfg.port" },
                    security = cfg.security,
                    encryption = cfg.encryption,
                    protocolType = cfg.type,
                    headerType = cfg.headerType,
                    flow = cfg.flow,
                    fp = cfg.fp
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class VServerConfig(
    val uuid: String = "",
    val address: String = "",
    val port: String = "",
    val security: String = "",
    val encryption: String = "none",
    val type: String = "tcp",
    val headerType: String = "",
    val flow: String = "",
    val fp: String = "",
    val allowInsecure: String = "",
    val name: String = ""
)
