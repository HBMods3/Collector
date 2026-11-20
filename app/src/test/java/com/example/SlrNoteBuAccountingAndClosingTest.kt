package com.example

import com.example.data.repository.ShopDatabaseRepository
import com.example.data.repository.ShopJsonCodec
import com.example.data.repository.ShopPaths
import com.example.domain.AccountingCalculator
import com.example.domain.model.BaItemDetail
import com.example.domain.model.BaPaymentMethod
import com.example.domain.model.BackupType
import com.example.domain.model.ClosingStatus
import com.example.domain.model.LocalShopBinding
import com.example.domain.model.PaymentMode
import com.example.domain.model.SaleLineItem
import com.example.utils.MoneyUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SlrNoteBuAccountingAndClosingTest {

    private lateinit var storage: InMemoryGitHubStorage
    private lateinit var codec: ShopJsonCodec
    private lateinit var repo: ShopDatabaseRepository
    private lateinit var shopA: LocalShopBinding
    private lateinit var shopB: LocalShopBinding

    @Before
    fun setup() {
        storage = InMemoryGitHubStorage()
        codec = ShopJsonCodec()
        repo = ShopDatabaseRepository(storage, codec)
        shopA = LocalShopBinding(
            shopId = "SHOP-A",
            shopName = "Aizawl Mart",
            accountId = "acc-1",
            ownerGithubLogin = "ownerA",
            repoOwner = "ownerA",
            repoName = "slr-shop-a",
            timezoneId = "Asia/Kolkata",
            createdAtIso = "2026-11-01T08:00:00+05:30"
        )
        shopB = LocalShopBinding(
            shopId = "SHOP-B",
            shopName = "Lunglei Store",
            accountId = "acc-2",
            ownerGithubLogin = "ownerB",
            repoOwner = "ownerB",
            repoName = "slr-shop-b",
            timezoneId = "Asia/Kolkata",
            createdAtIso = "2026-11-01T08:00:00+05:30"
        )
    }

    @Test
    fun `indian rupee formatting and integer paise arithmetic are exact`() {
        assertEquals(10000000L, MoneyUtils.parseRupeesToPaise("100000"))
        assertEquals(12550L, MoneyUtils.parseRupeesToPaise("125.50"))
        assertEquals("₹1,00,000", MoneyUtils.formatInr(10000000L))
        assertEquals("₹1,00,000.50", MoneyUtils.formatInr(10000050L))
        // 25% of ₹5,000 = ₹1,250
        assertEquals(125000L, MoneyUtils.calculatePercentagePaymentPaise(500000L, 25.0))
    }

    @Test
    fun `multi-shop data isolation keeps Shop A and Shop B completely separate`() = runTest {
        repo.initializeNewShopDatabase("tokA", shopA, "ownerA", "Owner A", "Dawrpui", "9862000001")
        repo.initializeNewShopDatabase("tokB", shopB, "ownerB", "Owner B", "Chanmari", "9862000002")

        repo.addCustomer("tokA", shopA, "ownerA", "Liana", "9862111111", "Dawrpui", 50000L, "")
        val snapA = repo.loadShopSnapshot("tokA", shopA, "ownerA")
        val snapB = repo.loadShopSnapshot("tokB", shopB, "ownerB")

        assertEquals(1, snapA.customers.size)
        assertEquals(0, snapB.customers.size)
        assertEquals("Aizawl Mart", snapA.shopConfig.shopName)
        assertEquals("Lunglei Store", snapB.shopConfig.shopName)
    }

    @Test
    fun `ba exact payment percentage payment and reversal recalculate balances accurately`() = runTest {
        repo.initializeNewShopDatabase("tokA", shopA, "ownerA", "Owner A", "Dawrpui", "9862000001")
        repo.addCustomer("tokA", shopA, "ownerA", "Mawia", "9862222222", "Zarkawt", 0L, "")

        // Add ₹5,000 Ba credit (Rice 100kg @ ₹50)
        repo.addBaCredit(
            token = "tokA",
            binding = shopA,
            actorLogin = "ownerA",
            customerPhone = "9862222222",
            items = listOf(BaItemDetail("Rice", 5000L, 100, 500000L)),
            note = "Monthly ration",
            date = LocalDate.of(2026, 11, 10),
            time = LocalTime.of(10, 0)
        )

        // Exact amount payment: ₹500 -> Remaining ₹4,500
        var snap = repo.recordBaPayment(
            token = "tokA",
            binding = shopA,
            actorLogin = "ownerA",
            customerPhone = "9862222222",
            paymentAmountPaise = 50000L,
            paymentMethod = BaPaymentMethod.CASH,
            percentageUsed = null,
            note = "Partial cash",
            date = LocalDate.of(2026, 11, 12),
            time = LocalTime.of(11, 30)
        )
        var cust = snap.customers.first()
        assertEquals(450000L, cust.currentPendingBaPaise)
        assertEquals(500000L, cust.totalCreditPaise)
        assertEquals(50000L, cust.totalPaidPaise)

        // Optional percentage payment: 50% of ₹4,500 = ₹2,250 -> Remaining ₹2,250
        val pctAmt = MoneyUtils.calculatePercentagePaymentPaise(cust.currentPendingBaPaise, 50.0)
        assertEquals(225000L, pctAmt)
        snap = repo.recordBaPayment(
            token = "tokA",
            binding = shopA,
            actorLogin = "ownerA",
            customerPhone = "9862222222",
            paymentAmountPaise = pctAmt,
            paymentMethod = BaPaymentMethod.OTHER_OFFLINE,
            percentageUsed = 50.0,
            note = "50% payment",
            date = LocalDate.of(2026, 11, 15),
            time = LocalTime.of(15, 0)
        )
        cust = snap.customers.first()
        assertEquals(225000L, cust.currentPendingBaPaise)

        // Reverse the ₹500 payment -> balance recalculates to ₹2,750
        val firstPayment = snap.baTransactions.first { it.amountPaise == 50000L }
        snap = repo.correctOrReverseBaTransaction("tokA", shopA, "ownerA", firstPayment, null, "Wrong customer")
        cust = snap.customers.first()
        assertEquals(275000L, cust.currentPendingBaPaise)
        assertTrue(snap.recentAuditLogs.any { it.action == "REVERSE_TRANSACTION" })
    }

    @Test
    fun `mandatory november 30 to december 1 monthly closing archives verifies clears active sales and preserves babu`() = runTest {
        repo.initializeNewShopDatabase("tokA", shopA, "ownerA", "Owner A", "Dawrpui", "9862000001")

        // Register customer & add Ba in November
        repo.addCustomer("tokA", shopA, "ownerA", "Thanga", "9862333333", "Bethlehem", 100000L, "")

        // Add November 2026 sales across Cash, Other Offline, and Credit
        repo.addSale(
            "tokA", shopA, "ownerA",
            listOf(SaleLineItem("Rice", 50000L, 2, 100000L)),
            PaymentMode.CASH, "Nov cash sale",
            LocalDate.of(2026, 11, 20), LocalTime.of(9, 15), null
        )
        repo.addSale(
            "tokA", shopA, "ownerA",
            listOf(SaleLineItem("Oil", 20000L, 1, 20000L)),
            PaymentMode.OTHER_OFFLINE, "Nov offline sale",
            LocalDate.of(2026, 11, 25), LocalTime.of(14, 0), null
        )
        repo.addSale(
            "tokA", shopA, "ownerA",
            listOf(SaleLineItem("Sugar", 10000L, 3, 30000L)),
            PaymentMode.CREDIT_BA, "Nov credit sale",
            LocalDate.of(2026, 11, 30), LocalTime.of(18, 45), null
        )

        // Simulate December 1, 2026 closing of November 2026
        val dec1Instant = Instant.parse("2026-12-01T00:05:00+05:30")
        val closeResult = repo.closingService.closeMonthAndArchive(
            token = "tokA",
            binding = shopA,
            actorLogin = "auto-scheduler",
            targetYear = 2026,
            targetMonth = 11,
            nowInstant = dec1Instant
        )
        assertTrue(closeResult.success)
        assertFalse(closeResult.alreadyClosed)
        assertEquals(ClosingStatus.COMPLETED, closeResult.record?.closingStatus)
        assertTrue(closeResult.record?.verificationStatus == true)

        // Verify November archive exists with exact totals: ₹1,500 total (₹1,000 cash, ₹200 other, ₹300 credit)
        val novArchive = repo.loadMonthlyArchive("tokA", shopA, 2026, 11)
        assertNotNull(novArchive)
        assertEquals(150000L, novArchive!!.totalSalesPaise)
        assertEquals(100000L, novArchive.cashSalesPaise)
        assertEquals(20000L, novArchive.otherOfflineSalesPaise)
        assertEquals(30000L, novArchive.creditSalesPaise)
        assertEquals(3, novArchive.transactionCount)
        assertEquals(6, novArchive.totalProductsSold)

        // Verify November active Daily Sales are cleared, December starts empty
        val novActive = storage.getFile("tokA", "ownerA", "slr-shop-a", ShopPaths.activeDailySalesFile(2026, 11))
            ?.content?.let { codec.dailySalesFromJson(it) }
        val decActive = storage.getFile("tokA", "ownerA", "slr-shop-a", ShopPaths.activeDailySalesFile(2026, 12))
            ?.content?.let { codec.dailySalesFromJson(it) }
        assertEquals(0, novActive!!.sales.size)
        assertEquals(0, decActive!!.sales.size)

        // Verify November monthly report remains accurate from archive
        val snap = repo.loadShopSnapshot("tokA", shopA, "ownerA")
        val novReport = AccountingCalculator.computeMonthlyReport(
            2026, 11,
            snap.allActiveSalesFiles.flatMap { it.sales },
            novArchive,
            snap.baTransactions,
            snap.customers
        )
        assertEquals(150000L, novReport.totalSalesPaise)

        // Verify Ba Bu customer & balance remained completely untouched
        assertEquals(1, snap.customers.size)
        assertEquals(100000L, snap.customers.first().currentPendingBaPaise)

        // Verify duplicate closing is prevented
        val duplicateAttempt = repo.closingService.closeMonthAndArchive("tokA", shopA, "ownerA", 2026, 11, dec1Instant)
        assertTrue(duplicateAttempt.success)
        assertTrue(duplicateAttempt.alreadyClosed)
    }

    @Test
    fun `monthly closing failure does NOT clear active sales`() = runTest {
        repo.initializeNewShopDatabase("tokA", shopA, "ownerA", "Owner A", "Dawrpui", "9862000001")
        repo.addSale(
            "tokA", shopA, "ownerA",
            listOf(SaleLineItem("Rice", 50000L, 1, 50000L)),
            PaymentMode.CASH, "Keep safe",
            LocalDate.of(2026, 11, 28), LocalTime.of(12, 0), null
        )

        storage.failPutPathPrefix = ShopPaths.MONTHLY_ARCHIVE_DIR
        val failedResult = repo.closingService.closeMonthAndArchive("tokA", shopA, "ownerA", 2026, 11)
        assertFalse(failedResult.success)
        assertEquals(ClosingStatus.FAILED, failedResult.record?.closingStatus)

        // Active sales MUST remain untouched!
        storage.failPutPathPrefix = null
        val novActive = storage.getFile("tokA", "ownerA", "slr-shop-a", ShopPaths.activeDailySalesFile(2026, 11))
            ?.content?.let { codec.dailySalesFromJson(it) }
        assertEquals(1, novActive!!.sales.size)
    }

    @Test
    fun `manual backup and restore creates pre-restore safety backup and preserves integrity`() = runTest {
        repo.initializeNewShopDatabase("tokA", shopA, "ownerA", "Owner A", "Dawrpui", "9862000001")
        repo.addCustomer("tokA", shopA, "ownerA", "Rema", "9862444444", "Mission Veng", 75000L, "")

        val bkp = repo.backupService.createBackup("tokA", shopA, "ownerA", BackupType.MANUAL)
        assertTrue(bkp.success)
        assertEquals(1, bkp.metadata!!.customerCount)

        // Modify customer afterwards, then restore from backup
        repo.addBaCredit(
            "tokA", shopA, "ownerA", "9862444444",
            listOf(BaItemDetail("Tea", 10000L, 1, 10000L)),
            "", LocalDate.of(2026, 11, 19), LocalTime.of(10, 0)
        )
        val restoreRes = repo.backupService.restoreFromBackup("tokA", shopA, "ownerA", bkp.metadata.filePath)
        assertTrue(restoreRes.isSuccess)

        val history = repo.backupService.listBackupHistory("tokA", shopA)
        assertTrue(history.any { it.backupType == BackupType.PRE_RESTORE })
        val restoredSnap = repo.loadShopSnapshot("tokA", shopA, "ownerA")
        assertEquals(75000L, restoredSnap.customers.first().currentPendingBaPaise)
    }
}
