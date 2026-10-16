package studio.creater120.hapai.vpn

enum class VpnStatus {
    Stopped,
    Starting,
    Started,
    Stopping;

    val nativeName: String get() = name
}
