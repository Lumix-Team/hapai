package studio.creater120.hapai.services

import studio.creater120.hapai.data.AppStatus
import studio.creater120.hapai.data.Mode

var appStatus = AppStatus.Halted to Mode.VPN
    private set

fun setStatus(status: AppStatus, mode: Mode) {
    appStatus = status to mode
}
