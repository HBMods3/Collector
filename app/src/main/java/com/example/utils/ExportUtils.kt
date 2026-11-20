package com.example.utils

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.domain.model.FinancialReportSummary
import com.example.domain.model.MonthlyArchiveFile
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ExportUtils {

    private fun exportsDir(context: Context): File {
        return File(context.cacheDir, "exports").apply { mkdirs() }
    }

    fun writeTextExport(
        context: Context,
        fileName: String,
        content: String
    ): File {
        val file = File(exportsDir(context), fileName)
        file.writeText(content, StandardCharsets.UTF_8)
        return file
    }

    fun writeZipExport(
        context: Context,
        zipFileName: String,
        entries: Map<String, String>
    ): File {
        val file = File(exportsDir(context), zipFileName)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            entries.forEach { (entryPath, body) ->
                val entry = ZipEntry(entryPath.trimStart('/'))
                zos.putNextEntry(entry)
                zos.write(body.toByteArray(StandardCharsets.UTF_8))
                zos.closeEntry()
            }
        }
        return file
    }

    fun buildReportCsv(report: FinancialReportSummary): String {
        val sb = StringBuilder()
        sb.appendLine("Report,${escapeCsv(report.title)}")
        sb.appendLine("Period,${escapeCsv(report.subtitle)}")
        sb.appendLine()
        sb.appendLine("Metric,Amount (INR)")
        sb.appendLine("Total Sales,${MoneyUtils.paiseToPlainRupeeString(report.totalSalesPaise)}")
        sb.appendLine("Cash Sales Received,${MoneyUtils.paiseToPlainRupeeString(report.cashSalesPaise)}")
        sb.appendLine("Other Offline Payments Received,${MoneyUtils.paiseToPlainRupeeString(report.otherOfflineSalesPaise)}")
        sb.appendLine("Credit Sales / Ba Added,${MoneyUtils.paiseToPlainRupeeString(report.creditSalesPaise)}")
        sb.appendLine("Total Ba Added (Ledger),${MoneyUtils.paiseToPlainRupeeString(report.baAddedPaise)}")
        sb.appendLine("Ba Payments Collected,${MoneyUtils.paiseToPlainRupeeString(report.baCollectedPaise)}")
        sb.appendLine("Remaining Pending Customer Ba,${MoneyUtils.paiseToPlainRupeeString(report.remainingPendingBaPaise)}")
        sb.appendLine("Number of Sales,${report.numberOfSales}")
        sb.appendLine("Number of Ba Payments,${report.numberOfPayments}")
        sb.appendLine()
        sb.appendLine("Best-Selling Items,Quantity Sold,Total Amount (INR)")
        report.bestSellingItems.forEach { item ->
            sb.appendLine("${escapeCsv(item.itemName)},${item.quantitySold},${MoneyUtils.paiseToPlainRupeeString(item.totalAmountPaise)}")
        }
        if (report.monthlyComparison.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Month,Total Sales,Cash,Other Offline,Credit,Ba Added,Ba Collected,Transactions")
            report.monthlyComparison.forEach { m ->
                sb.appendLine(
                    "${escapeCsv(m.monthName)},${MoneyUtils.paiseToPlainRupeeString(m.totalSalesPaise)}," +
                        "${MoneyUtils.paiseToPlainRupeeString(m.cashSalesPaise)}," +
                        "${MoneyUtils.paiseToPlainRupeeString(m.otherOfflineSalesPaise)}," +
                        "${MoneyUtils.paiseToPlainRupeeString(m.creditSalesPaise)}," +
                        "${MoneyUtils.paiseToPlainRupeeString(m.baAddedPaise)}," +
                        "${MoneyUtils.paiseToPlainRupeeString(m.baCollectedPaise)},${m.transactionCount}"
                )
            }
        }
        return sb.toString()
    }

    fun buildArchiveCsv(archive: MonthlyArchiveFile): String {
        val sb = StringBuilder()
        sb.appendLine("SLR Note Bu Monthly Sales Archive,${archive.monthName} ${archive.year}")
        sb.appendLine("Total Sales,${MoneyUtils.paiseToPlainRupeeString(archive.totalSalesPaise)}")
        sb.appendLine("Cash Sales,${MoneyUtils.paiseToPlainRupeeString(archive.cashSalesPaise)}")
        sb.appendLine("Other Offline Payments,${MoneyUtils.paiseToPlainRupeeString(archive.otherOfflineSalesPaise)}")
        sb.appendLine("Credit Sales,${MoneyUtils.paiseToPlainRupeeString(archive.creditSalesPaise)}")
        sb.appendLine("Total Products Sold,${archive.totalProductsSold}")
        sb.appendLine()
        sb.appendLine("Item Summary")
        sb.appendLine("Item Name,Quantity Sold,Total Amount (INR)")
        archive.itemSummary.forEach {
            sb.appendLine("${escapeCsv(it.itemName)},${it.quantitySold},${MoneyUtils.paiseToPlainRupeeString(it.totalAmountPaise)}")
        }
        sb.appendLine()
        sb.appendLine("Archived Sales")
        sb.appendLine("Date,Time,Sale ID,Payment Mode,Total (INR),Items,Note")
        archive.sales.forEach { s ->
            val itemsStr = s.items.joinToString("; ") { "${it.itemName} x${it.quantity} (${MoneyUtils.formatInr(it.itemTotalPaise)})" }
            sb.appendLine(
                "${escapeCsv(s.dateDisplay)},${escapeCsv(s.timeDisplay)},${escapeCsv(s.saleId)}," +
                    "${escapeCsv(s.paymentMode.label)},${MoneyUtils.paiseToPlainRupeeString(s.grandTotalPaise)}," +
                    "${escapeCsv(itemsStr)},${escapeCsv(s.note)}"
            )
        }
        return sb.toString()
    }

    fun writeReportPdf(
        context: Context,
        fileName: String,
        shopName: String,
        report: FinancialReportSummary
    ): File {
        val outFile = File(exportsDir(context), fileName)
        try {
            val doc = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = doc.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0B3B2E")
                textSize = 20f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint().apply {
                color = Color.parseColor("#44554F")
                textSize = 12f
            }
            val sectionPaint = Paint().apply {
                color = Color.parseColor("#0F5C47")
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val rowPaint = Paint().apply {
                color = Color.parseColor("#141D1A")
                textSize = 11.5f
            }
            val linePaint = Paint().apply {
                color = Color.parseColor("#D5DDD9")
                strokeWidth = 1f
            }

            var y = 48f
            canvas.drawText("$shopName — SLR Note Bu", 40f, y, titlePaint)
            y += 22f
            canvas.drawText("${report.title} (${report.subtitle})", 40f, y, subtitlePaint)
            y += 14f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            canvas.drawText("Financial Breakdown", 40f, y, sectionPaint)
            y += 20f
            val rows = listOf(
                "Total Sales" to MoneyUtils.formatInr(report.totalSalesPaise),
                "Cash Sales Received" to MoneyUtils.formatInr(report.cashSalesPaise),
                "Other Offline Payments Received" to MoneyUtils.formatInr(report.otherOfflineSalesPaise),
                "Credit Sales / Ba Added" to MoneyUtils.formatInr(report.creditSalesPaise),
                "Total Ledger Ba Added" to MoneyUtils.formatInr(report.baAddedPaise),
                "Ba Payments Collected" to MoneyUtils.formatInr(report.baCollectedPaise),
                "Remaining Pending Customer Ba" to MoneyUtils.formatInr(report.remainingPendingBaPaise),
                "Number of Sales" to report.numberOfSales.toString(),
                "Number of Ba Payments" to report.numberOfPayments.toString()
            )
            rows.forEach { (k, v) ->
                canvas.drawText(k, 40f, y, rowPaint)
                canvas.drawText(v, 380f, y, rowPaint)
                y += 18f
            }

            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 22f
            canvas.drawText("Best-Selling Items", 40f, y, sectionPaint)
            y += 20f
            report.bestSellingItems.take(25).forEach { item ->
                if (y > 790f) {
                    doc.finishPage(page)
                    page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, doc.pages.size + 1).create())
                    canvas = page.canvas
                    y = 48f
                }
                canvas.drawText("${item.itemName} (Qty: ${item.quantitySold})", 40f, y, rowPaint)
                canvas.drawText(MoneyUtils.formatInr(item.totalAmountPaise), 420f, y, rowPaint)
                y += 18f
            }

            doc.finishPage(page)
            FileOutputStream(outFile).use { fos -> doc.writeTo(fos) }
            doc.close()
        } catch (_: Throwable) {
            writeFallbackSimplePdf(outFile, shopName, report)
        }
        return outFile
    }

    private fun writeFallbackSimplePdf(outFile: File, shopName: String, report: FinancialReportSummary) {
        val lines = buildList {
            add("$shopName - SLR Note Bu")
            add("${report.title} (${report.subtitle})")
            add("Total Sales: Rs ${MoneyUtils.paiseToPlainRupeeString(report.totalSalesPaise)}")
            add("Cash Sales: Rs ${MoneyUtils.paiseToPlainRupeeString(report.cashSalesPaise)}")
            add("Other Offline: Rs ${MoneyUtils.paiseToPlainRupeeString(report.otherOfflineSalesPaise)}")
            add("Credit / Ba Added: Rs ${MoneyUtils.paiseToPlainRupeeString(report.creditSalesPaise)}")
            add("Ba Collected: Rs ${MoneyUtils.paiseToPlainRupeeString(report.baCollectedPaise)}")
            add("Remaining Pending Ba: Rs ${MoneyUtils.paiseToPlainRupeeString(report.remainingPendingBaPaise)}")
        }
        val streamContent = buildString {
            append("BT /F1 12 Tf 40 780 Td 16 TL ")
            lines.forEach { line ->
                val safe = line.replace("(", "[").replace(")", "]")
                append("($safe) Tj T* ")
            }
            append("ET")
        }
        val pdf = """%PDF-1.4
1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj
2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj
3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj
4 0 obj << /Length ${streamContent.length} >> stream
$streamContent
endstream endobj
5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj
trailer << /Root 1 0 R >>
%%EOF"""
        outFile.writeText(pdf, StandardCharsets.US_ASCII)
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }
}
