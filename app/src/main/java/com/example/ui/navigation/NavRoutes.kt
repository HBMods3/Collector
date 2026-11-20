package com.example.ui.navigation

object NavRoutes {
    const val HOME = "home"
    const val DAILY_SALE = "daily_sale"
    const val BABU = "babu"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val CUSTOMER_DETAIL = "customer_detail/{phone}"
    fun customerDetail(phone: String): String = "customer_detail/$phone"

    const val MONTHLY_ARCHIVE = "monthly_archive"
    const val BACKUPS = "backups"
    const val EXPLORER = "explorer"
    const val USER_MANAGEMENT = "user_management"
    const val AUDIT_LOGS = "audit_logs"
}
