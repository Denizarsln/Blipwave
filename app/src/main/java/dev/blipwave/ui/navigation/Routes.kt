package dev.blipwave.ui.navigation

import android.net.Uri

object Routes {
    const val SCAN = "scan"
    const val CONNECTIONS = "connections"
    const val SETTINGS = "settings"

    const val ARG_ADDRESS = "address"
    const val ARG_NAME = "name"

    const val DEVICE = "device/{$ARG_ADDRESS}?$ARG_NAME={$ARG_NAME}"
    const val CHAT = "chat/{$ARG_ADDRESS}"

    fun device(address: String, name: String?): String =
        "device/${Uri.encode(address)}?$ARG_NAME=${Uri.encode(name.orEmpty())}"

    fun chat(address: String): String = "chat/${Uri.encode(address)}"
}
