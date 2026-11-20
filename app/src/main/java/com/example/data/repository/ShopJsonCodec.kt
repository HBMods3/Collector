package com.example.data.repository

import com.example.domain.model.AuditLogFile
import com.example.domain.model.BaTransactionsFile
import com.example.domain.model.BackupFilePayload
import com.example.domain.model.BackupSettings
import com.example.domain.model.CategoriesFile
import com.example.domain.model.ClosingHistoryFile
import com.example.domain.model.CustomerProfile
import com.example.domain.model.DailySalesFile
import com.example.domain.model.MonthlyArchiveFile
import com.example.domain.model.ShopConfig
import com.example.domain.model.ShopSettings
import com.example.domain.model.ShopUsersFile
import com.example.domain.model.YearlyReportFile
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class ShopJsonCodec(val moshi: Moshi = defaultMoshi()) {

    companion object {
        fun defaultMoshi(): Moshi = Moshi.Builder()
            .addLast(KotlinJsonAdapterFactory())
            .build()
    }

    inline fun <reified T> adapter(): JsonAdapter<T> =
        moshi.adapter(T::class.java).indent("  ")

    inline fun <reified T> toJson(value: T): String = adapter<T>().toJson(value)

    inline fun <reified T> fromJson(json: String): T? = try {
        adapter<T>().fromJson(json)
    } catch (_: Exception) {
        null
    }

    fun shopConfigToJson(v: ShopConfig) = toJson(v)
    fun shopConfigFromJson(s: String) = fromJson<ShopConfig>(s)

    fun settingsToJson(v: ShopSettings) = toJson(v)
    fun settingsFromJson(s: String) = fromJson<ShopSettings>(s)

    fun usersToJson(v: ShopUsersFile) = toJson(v)
    fun usersFromJson(s: String) = fromJson<ShopUsersFile>(s)

    fun categoriesToJson(v: CategoriesFile) = toJson(v)
    fun categoriesFromJson(s: String) = fromJson<CategoriesFile>(s)

    fun backupSettingsToJson(v: BackupSettings) = toJson(v)
    fun backupSettingsFromJson(s: String) = fromJson<BackupSettings>(s)

    fun closingHistoryToJson(v: ClosingHistoryFile) = toJson(v)
    fun closingHistoryFromJson(s: String) = fromJson<ClosingHistoryFile>(s)

    fun dailySalesToJson(v: DailySalesFile) = toJson(v)
    fun dailySalesFromJson(s: String) = fromJson<DailySalesFile>(s)

    fun customerToJson(v: CustomerProfile) = toJson(v)
    fun customerFromJson(s: String) = fromJson<CustomerProfile>(s)

    fun baTransactionsToJson(v: BaTransactionsFile) = toJson(v)
    fun baTransactionsFromJson(s: String) = fromJson<BaTransactionsFile>(s)

    fun monthlyArchiveToJson(v: MonthlyArchiveFile) = toJson(v)
    fun monthlyArchiveFromJson(s: String) = fromJson<MonthlyArchiveFile>(s)

    fun yearlyReportToJson(v: YearlyReportFile) = toJson(v)
    fun yearlyReportFromJson(s: String) = fromJson<YearlyReportFile>(s)

    fun backupPayloadToJson(v: BackupFilePayload) = toJson(v)
    fun backupPayloadFromJson(s: String) = fromJson<BackupFilePayload>(s)

    fun auditLogToJson(v: AuditLogFile) = toJson(v)
    fun auditLogFromJson(s: String) = fromJson<AuditLogFile>(s)
}
