package com.yourname.nib.core.time

import java.util.Calendar

data class NibDate(val year: Int, val month: Int, val day: Int) {
    val epochDay: Long get() = daysFromCivil(year, month, day)

    val dayOfWeek: Int get() = (((epochDay + 4) % 7 + 7) % 7).toInt()

    fun normalized(): NibDate = fromEpochDay(epochDay)

    fun plusDays(days: Long): NibDate = fromEpochDay(epochDay + days)

    fun dateKey(): String = "%04d-%02d-%02d".format(year, month, day)

    companion object {
        fun today(): NibDate {
            val cal = Calendar.getInstance()
            return NibDate(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
            )
        }

        fun fromEpochDay(value: Long): NibDate {
            val z = value + 719468
            val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
            val doe = z - era * 146097
            val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
            var y = yoe + era * 400
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = doy - (153 * mp + 2) / 5 + 1
            val m = mp + if (mp < 10) 3 else -9
            if (m <= 2) y++
            return NibDate(y.toInt(), m.toInt(), d.toInt())
        }
    }
}

private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year.toLong()
    if (month <= 2) y--
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val mp = (month + if (month > 2) -3 else 9).toLong()
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}
