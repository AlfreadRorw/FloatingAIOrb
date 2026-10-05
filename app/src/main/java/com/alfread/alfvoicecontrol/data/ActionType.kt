package com.alfread.alfvoicecontrol.data

/**
 * The kind of action a [VoiceCommand] performs once its trigger phrase is matched.
 * New action types can be added here as the command system grows - the executor
 * in [com.alfread.alfvoicecontrol.commands.CommandExecutor] switches on this enum.
 */
enum class ActionType {
    SCREEN_ON,
    SCREEN_OFF,
    OPEN_APP
}
