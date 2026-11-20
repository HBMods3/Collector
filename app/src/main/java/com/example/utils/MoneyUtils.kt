package com.example.utils

import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs
import kotlin.math.roundToLong

object MoneyUtils {

    /**
     * Converts rupee string (e.g. "1250", "1250.50", "₹1,250") to integer paise.
     * Returns null if invalid or negative when allowNegative == false.
     */
    fun parseRupeesToPaise(input: String, allowNegative: Boolean = false): Long? {
        val cleaned = input.trim()
            .replace("₹", "")
            .replace(",", "")
            .replace(" ", "")
        if (cleaned.isEmpty()) return null
        return try {
            val bd = BigDecimal(cleaned)
            val paise = bd.multiply(BigDecimal(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
            if (!allowNegative && paise < 0L) null else paise
        } catch (_: Exception) {
            null
        }
    }

    fun rupeesDoubleToPaise(rupees: Double): Long {
        return BigDecimal.valueOf(rupees)
            .multiply(BigDecimal(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
    }

    fun paiseToPlainRupeeString(paise: Long): String {
        val whole = paise / 100
        val rem = abs(paise % 100)
        return if (rem == 0L) {
            whole.toString()
        } else {
            val sign = if (paise < 0 && whole == 0L) "-" else ""
            "$sign$whole.${rem.toString().padStart(2, '0')}"
        }
    }

    /**
     * Calculates percentage of a paise balance, rounded to nearest rupee (as specified:
     * Math.round(current_total_ba * (percentage / 100)) in rupees, or exact paise if < ₹1).
     */
    fun calculatePercentagePaymentPaise(pendingPaise: Long, percentage: Double): Long {
        if (pendingPaise <= 0L || percentage <= 0.0) return 0L
        val clampedPct = percentage.coerceIn(0.0, 100.0)
        if (clampedPct == 100.0) return pendingPaise
        val rawRupees = (pendingPaise / 100.0) * (clampedPct / 100.0)
        val roundedRupeesPaise = rawRupees.roundToLong() * 100L
        return roundedRupeesPaise.coerceIn(0L, pendingPaise)
    }

    /**
     * Formats integer paise in Indian Numbering System: ₹1,00,000 or ₹1,00,000.50.
     */
    fun formatInr(paise: Long, includeSymbol: Boolean = true): String {
        val negative = paise < 0L
        val absPaise = abs(paise)
        val whole = absPaise / 100L
        val dec = absPaise % 100L

        val formattedWhole = formatIndianInteger(whole)
        val decPart = if (dec > 0L) ".${dec.toString().padStart(2, '0')}" else ""
        val prefix = buildString {
            if (negative) append("-")
            if (includeSymbol) append("₹")
        }
        return "$prefix$formattedWhole$decPart"
    }

    fun formatIndianInteger(number: Long): String {
        val str = abs(number).toString()
        if (str.length <= 3) return str
        val lastThree = str.takeLast(3)
        val rest = str.dropLast(3)
        val sb = StringBuilder()
        var count = 0
        for (i in rest.length - 1 downTo 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i != 0) {
                sb.append(',')
                count = 0
            }
        }
        return "${sb.reverse()},$lastThree"
    }
}
