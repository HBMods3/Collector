package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.FinancialReportSummary
import com.example.domain.model.ItemSummaryEntry
import com.example.security.TokenCryptoManager
import com.example.utils.ExportUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches SLR Note Bu`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SLR Note Bu", appName)
    }

    @Test
    fun `token crypto manager encrypts and decrypts cleanly`() {
        val encrypted = TokenCryptoManager.encrypt("gho_sample_token_12345")
        val decrypted = TokenCryptoManager.decrypt(encrypted)
        assertEquals("gho_sample_token_12345", decrypted)
    }

    @Test
    fun `export utils generates pdf csv and zip files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = FinancialReportSummary(
            title = "Monthly Financial Report",
            subtitle = "November 2026",
            totalSalesPaise = 15000000L,
            cashSalesPaise = 10000000L,
            otherOfflineSalesPaise = 2000000L,
            creditSalesPaise = 3000000L,
            baAddedPaise = 3000000L,
            baCollectedPaise = 1200000L,
            remainingPendingBaPaise = 1800000L,
            numberOfSales = 24,
            numberOfActiveCustomers = 5,
            numberOfPayments = 3,
            bestSellingItems = listOf(ItemSummaryEntry("Rice", 50, 2500000L))
        )
        val pdf = ExportUtils.writeReportPdf(context, "test_report.pdf", "Zoram Store", report)
        val csv = ExportUtils.writeTextExport(context, "test_report.csv", ExportUtils.buildReportCsv(report))
        val zip = ExportUtils.writeZipExport(context, "test_db.zip", mapOf("SLR-Note-Bu/config/shop.json" to "{}"))

        assertTrue(pdf.exists() && pdf.length() > 100)
        assertTrue(csv.exists() && csv.readText().contains("Rice,50,25000"))
        assertTrue(zip.exists() && zip.length() > 20)
    }
}
