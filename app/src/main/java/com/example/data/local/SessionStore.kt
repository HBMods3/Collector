package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.domain.model.GitHubAccountSession
import com.example.domain.model.LocalShopBinding
import com.example.security.TokenCryptoManager
import com.example.ui.theme.ThemeMode
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.slrDataStore: DataStore<Preferences> by preferencesDataStore(name = "slr_note_bu_secure_prefs")

class SessionStore(
    private val context: Context,
    private val moshi: Moshi
) {
    private val accountsKey = stringPreferencesKey("encrypted_accounts_json")
    private val activeAccountIdKey = stringPreferencesKey("active_account_id")
    private val shopsKey = stringPreferencesKey("shops_registry_json")
    private val activeShopIdKey = stringPreferencesKey("active_shop_id")
    private val customClientIdKey = stringPreferencesKey("custom_github_client_id")
    private val themeModeKey = stringPreferencesKey("theme_mode")

    private val accountListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, GitHubAccountSession::class.java)
        moshi.adapter<List<GitHubAccountSession>>(type)
    }

    private val shopListAdapter by lazy {
        val type = Types.newParameterizedType(List::class.java, LocalShopBinding::class.java)
        moshi.adapter<List<LocalShopBinding>>(type)
    }

    val accountsFlow: Flow<List<GitHubAccountSession>> = context.slrDataStore.data.map { prefs ->
        val raw = prefs[accountsKey] ?: return@map emptyList()
        try {
            accountListAdapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val activeAccountIdFlow: Flow<String?> = context.slrDataStore.data.map { it[activeAccountIdKey] }

    val shopsFlow: Flow<List<LocalShopBinding>> = context.slrDataStore.data.map { prefs ->
        val raw = prefs[shopsKey] ?: return@map emptyList()
        try {
            shopListAdapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    val activeShopIdFlow: Flow<String?> = context.slrDataStore.data.map { it[activeShopIdKey] }

    val themeModeFlow: Flow<ThemeMode> = context.slrDataStore.data.map { prefs ->
        when (prefs[themeModeKey]) {
            ThemeMode.LIGHT.name -> ThemeMode.LIGHT
            ThemeMode.DARK.name -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val customClientIdFlow: Flow<String> = context.slrDataStore.data.map { prefs ->
        prefs[customClientIdKey] ?: ""
    }

    suspend fun getEffectiveGithubClientId(): String {
        val custom = customClientIdFlow.first().trim()
        if (custom.isNotEmpty()) return custom
        val fromEnv = BuildConfig.GITHUB_CLIENT_ID.trim()
        return if (fromEnv == "YOUR_GITHUB_OAUTH_CLIENT_ID") "" else fromEnv
    }

    suspend fun saveCustomGithubClientId(clientId: String) {
        context.slrDataStore.edit { prefs ->
            prefs[customClientIdKey] = clientId.trim()
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.slrDataStore.edit { prefs ->
            prefs[themeModeKey] = mode.name
        }
    }

    suspend fun upsertAccount(session: GitHubAccountSession, setActive: Boolean = true) {
        context.slrDataStore.edit { prefs ->
            val current = prefs[accountsKey]?.let {
                try { accountListAdapter.fromJson(it) } catch (_: Exception) { null }
            } ?: emptyList()
            val updated = current.filterNot { it.accountId == session.accountId || it.login.equals(session.login, ignoreCase = true) } + session
            prefs[accountsKey] = accountListAdapter.toJson(updated)
            if (setActive || prefs[activeAccountIdKey] == null) {
                prefs[activeAccountIdKey] = session.accountId
            }
        }
    }

    suspend fun setActiveAccount(accountId: String) {
        context.slrDataStore.edit { prefs ->
            prefs[activeAccountIdKey] = accountId
        }
    }

    suspend fun removeAccount(accountId: String) {
        context.slrDataStore.edit { prefs ->
            val current = prefs[accountsKey]?.let {
                try { accountListAdapter.fromJson(it) } catch (_: Exception) { null }
            } ?: emptyList()
            val remaining = current.filterNot { it.accountId == accountId }
            prefs[accountsKey] = accountListAdapter.toJson(remaining)
            if (prefs[activeAccountIdKey] == accountId) {
                val nextId = remaining.firstOrNull()?.accountId
                if (nextId != null) prefs[activeAccountIdKey] = nextId else prefs.remove(activeAccountIdKey)
            }
        }
    }

    suspend fun getDecryptedTokenForAccount(accountId: String): String? {
        val accounts = accountsFlow.first()
        val acc = accounts.firstOrNull { it.accountId == accountId } ?: return null
        return TokenCryptoManager.decrypt(acc.encryptedToken)
    }

    suspend fun upsertShop(binding: LocalShopBinding, setActive: Boolean = true) {
        context.slrDataStore.edit { prefs ->
            val current = prefs[shopsKey]?.let {
                try { shopListAdapter.fromJson(it) } catch (_: Exception) { null }
            } ?: emptyList()
            val updated = current.filterNot {
                it.shopId == binding.shopId ||
                    (it.repoOwner.equals(binding.repoOwner, true) && it.repoName.equals(binding.repoName, true) && it.accountId == binding.accountId)
            } + binding
            prefs[shopsKey] = shopListAdapter.toJson(updated)
            if (setActive || prefs[activeShopIdKey] == null) {
                prefs[activeShopIdKey] = binding.shopId
            }
        }
    }

    suspend fun setActiveShop(shopId: String?) {
        context.slrDataStore.edit { prefs ->
            if (shopId == null) prefs.remove(activeShopIdKey) else prefs[activeShopIdKey] = shopId
        }
    }

    suspend fun removeShop(shopId: String) {
        context.slrDataStore.edit { prefs ->
            val current = prefs[shopsKey]?.let {
                try { shopListAdapter.fromJson(it) } catch (_: Exception) { null }
            } ?: emptyList()
            val remaining = current.filterNot { it.shopId == shopId }
            prefs[shopsKey] = shopListAdapter.toJson(remaining)
            if (prefs[activeShopIdKey] == shopId) {
                val next = remaining.firstOrNull()?.shopId
                if (next != null) prefs[activeShopIdKey] = next else prefs.remove(activeShopIdKey)
            }
        }
    }

    suspend fun getActiveAccount(): GitHubAccountSession? {
        val accounts = accountsFlow.first()
        val id = activeAccountIdFlow.first()
        return accounts.firstOrNull { it.accountId == id } ?: accounts.firstOrNull()
    }

    suspend fun getActiveShop(): LocalShopBinding? {
        val shops = shopsFlow.first()
        val id = activeShopIdFlow.first()
        return shops.firstOrNull { it.shopId == id } ?: shops.firstOrNull()
    }

    suspend fun getAllShops(): List<LocalShopBinding> = shopsFlow.first()
}
