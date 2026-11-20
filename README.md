# SLR Note Bu

**GitHub-Powered Multi-Shop Sales, Ba Bu Credit Ledger, Financial Accounting, Monthly Archive & Automatic Backup Application (Android / Kotlin / Jetpack Compose)**

---

## 1. GitHub OAuth Setup Instructions

SLR Note Bu uses **GitHub OAuth Device Flow** (`repo` and `read:user` scopes) so shopkeepers can authenticate directly from Android without exposing client secrets or generating manual Personal Access Tokens.

1. Visit **GitHub Developer Settings**: `https://github.com/settings/developers` → **OAuth Apps** → **New OAuth App**.
2. Fill in:
   - **Application Name**: `SLR Note Bu`
   - **Homepage URL**: `https://github.com`
   - **Authorization Callback URL**: `slrnotebu://oauth/callback`
3. Check **Enable Device Flow** in the OAuth App settings and save.
4. Copy the **Client ID** (`Ov23li...`).
5. Configure `GITHUB_CLIENT_ID` in the **Secrets panel in AI Studio** (or enter the Client ID directly inside the app's GitHub Sign-In setup panel).

---

## 2. Environment Variable Example (`.env.example`)

```properties
GITHUB_CLIENT_ID=YOUR_GITHUB_OAUTH_CLIENT_ID
```

Tokens obtained from GitHub OAuth are encrypted at rest on-device using **Android Keystore (`AES/GCM/NoPadding`)** inside `TokenCryptoManager` and `SessionStore`.

---

## 3. Repository Database Structure

Each shop uses its own isolated private GitHub repository initialized with:

```
SLR-Note-Bu/
├── config/
│   ├── shop.json
│   ├── settings.json
│   ├── users.json
│   ├── categories.json
│   ├── backup-settings.json
│   └── closing-history.json
├── babu/
│   ├── customers/
│   │   └── 91XXXXXXXXXX.json
│   └── transactions/
│       └── 2026/11/transactions.json
├── daily-sales/
│   └── active/2026-11.json
├── monthly-archive/
│   └── 2026/11-November.json
├── yearly-reports/
│   └── 2026.json
├── reports/
│   ├── daily/
│   ├── monthly/
│   └── yearly/
├── backups/
│   ├── daily/2026/11/20-backup.json
│   ├── monthly/2026/
│   ├── manual/
│   ├── pre-restore/
│   └── annual/
├── audit-logs/
│   └── 2026/audit-2026-11.json
└── README.md
```

---

## 4. Monthly Closing & Clearing Protocol

At the start of every new month (e.g., Dec 1 closes Nov) via `ShopMaintenanceWorker` (WorkManager) or manual Admin trigger:
1. Checks `closing-history.json` to prevent duplicate closing.
2. Reads active `daily-sales/active/YYYY-MM.json`.
3. Computes totals, daily totals, and item summary table.
4. Uploads `monthly-archive/YYYY/MM-MonthName.json` to GitHub.
5. Reads back the uploaded archive from GitHub.
6. Verifies transaction count, total products sold, and integer-paise totals.
7. Only after verification succeeds: clears the closed month's active Daily Sales file, initializes the new month empty, updates `yearly-reports/YYYY.json`, records `COMPLETED` in `closing-history.json`, and writes an audit log.
8. **Ba Bu customers, balances, credit history, and payment history are never deleted.**

---

## 5. Backup & Restore

- **Automatic Daily Backup**: Runs via `ShopMaintenanceWorker` at the shop's configured local time (default `23:55`) even when the app is closed.
- **Manual & ZIP Export**: Export complete repository JSON files as a `.zip` archive via Android ShareSheet.
- **Safe Restore**: Always creates a `backups/pre-restore/` safety backup, validates JSON & SHA-256 checksum, verifies `shopId`, restores data, and writes a `BACKUP_RESTORE` audit log.

---

## 6. Testing Instructions

Run all local JVM and Robolectric unit tests (including the November 30 → December 1 Monthly Closing simulation):

```bash
gradle :app:testDebugUnitTest
```
