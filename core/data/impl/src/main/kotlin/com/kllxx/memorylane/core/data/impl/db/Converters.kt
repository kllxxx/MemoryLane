package com.kllxx.memorylane.core.data.impl.db

import androidx.room.TypeConverter
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long): LocalDate = LocalDate.ofEpochDay(value)

    @TypeConverter
    fun toEpochDay(date: LocalDate): Long = date.toEpochDay()
}
