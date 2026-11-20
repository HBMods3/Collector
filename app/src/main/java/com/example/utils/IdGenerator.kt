package com.example.utils

import java.security.SecureRandom
import java.util.UUID

object IdGenerator {
    private val random = SecureRandom()
    private val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    private fun shortSuffix(len: Int = 6): String {
        val sb = StringBuilder(len)
        repeat(len) {
            sb.append(alphabet[random.nextInt(alphabet.length)])
        }
        return sb.toString()
    }

    fun saleId(): String = "SLR-SALE-${System.currentTimeMillis()}-${shortSuffix()}"
    fun baCreditId(): String = "SLR-BA-${System.currentTimeMillis()}-${shortSuffix()}"
    fun baPaymentId(): String = "SLR-PAY-${System.currentTimeMillis()}-${shortSuffix()}"
    fun auditId(): String = "SLR-AUD-${System.currentTimeMillis()}-${shortSuffix()}"
    fun backupId(): String = "SLR-BKP-${System.currentTimeMillis()}-${shortSuffix()}"
    fun correctionId(): String = "SLR-COR-${System.currentTimeMillis()}-${shortSuffix()}"
    fun shopId(): String = "SHOP-${UUID.randomUUID().toString().take(8).uppercase()}"
    fun requestId(): String = UUID.randomUUID().toString()
}
