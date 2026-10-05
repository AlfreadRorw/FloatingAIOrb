package com.alfread.alfvoicecontrol.ui

object AlfRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val COMMANDS_LIST = "commands_list"
    const val ADD_COMMAND = "add_command?commandId={commandId}"
    const val SETTINGS = "settings"
    const val DEVICE_ADMIN = "device_admin"
    const val PIN_SETUP = "pin_setup"
    const val PIN_UNLOCK = "pin_unlock"

    fun addCommand(commandId: String? = null) =
        "add_command" + if (commandId != null) "?commandId=$commandId" else ""
}
