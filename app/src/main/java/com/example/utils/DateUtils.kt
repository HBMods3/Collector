package com.example.utils

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Month
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class TimestampParts(
    val isoTimestamp: String,
    val day: Int,
    val month: Int,
    val year: Int,
    val dateDisplay: String,
    val timeDisplay: String,
    val dateKey: String,
    val monthKey: String
)

object DateUtils {
    private val displayDateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
    private val displayTimeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val dateKeyFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH)

    fun safeZoneId(timezoneId: String?): ZoneId {
        return try {
            if (timezoneId.isNullOrBlank()) ZoneId.of("Asia/Kolkata")
            else ZoneId.of(timezoneId)
        } catch (_: Exception) {
            ZoneId.of("Asia/Kolkata")
        }
    }

    fun nowParts(timezoneId: String, nowInstant: Instant = Instant.now()): TimestampParts {
        val zone = safeZoneId(timezoneId)
        val zdt = ZonedDateTime.ofInstant(nowInstant, zone)
        return fromZonedDateTime(zdt)
    }

    fun partsForLocalDateTime(
        date: LocalDate,
        time: LocalTime,
        timezoneId: String
    ): TimestampParts {
        val zone = safeZoneId(timezoneId)
        val zdt = ZonedDateTime.of(date, time, zone)
        return fromZonedDateTime(zdt)
    }

    fun fromZonedDateTime(zdt: ZonedDateTime): TimestampParts {
        return TimestampParts(
            isoTimestamp = zdt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            day = zdt.dayOfMonth,
            month = zdt.monthValue,
            year = zdt.year,
            dateDisplay = zdt.format(displayDateFmt),
            timeDisplay = zdt.format(displayTimeFmt),
            dateKey = zdt.format(dateKeyFmt),
            monthKey = "%04d-%02d".format(zdt.year, zdt.monthValue)
        )
    }

    fun monthName(month: Int): String {
        return Month.of(month.coerceIn(1, 12)).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
    }

    fun monthArchiveFileName(month: Int): String {
        return "%02d-%s.json".format(month, monthName(month))
    }

    fun monthKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

    fun dateKey(year: Int, month: Int, day: Int): String = "%04d-%02d-%02d".format(year, month, day)

    fun parseLocalDate(dateKey: String): LocalDate? {
        return try {
            LocalDate.parse(dateKey, dateKeyFmt)
        } catch (_: Exception) {
            null
        }
    }

    fun formatLocalDate(date: LocalDate): String = date.format(displayDateFmt)

    fun formatLocalTime(time: LocalTime): String = time.format(displayTimeFmt)

    fun todayDate(timezoneId: String): LocalDate = LocalDate.now(safeZoneId(timezoneId))

    fun nowTime(timezoneId: String): LocalTime = LocalTime.now(safeZoneId(timezoneId))

    fun currentDateTime(timezoneId: String): LocalDateTime = LocalDateTime.now(safeZoneId(timezoneId))
}
