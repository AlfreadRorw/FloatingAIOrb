package com.alfread.alfvoicecontrol.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun actionTypeToString(value: ActionType): String = value.name

    @TypeConverter
    fun stringToActionType(value: String): ActionType =
        ActionType.entries.firstOrNull { it.name == value } ?: ActionType.OPEN_APP
}
