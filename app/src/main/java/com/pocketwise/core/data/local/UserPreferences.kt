package com.pocketwise.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import com.pocketwise.feature.widget.PocketWiseWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "user_prefs")

/** Small standalone app settings (currency, recurring budget, ...) — not
 * expense data, so this is DataStore, not Room. */
@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val currencyKey = stringPreferencesKey("currency_code")
    private val budgetKey = doublePreferencesKey("monthly_budget")
    private val appLockKey = booleanPreferencesKey("app_lock_enabled")

    /** Opt-in: ask for fingerprint/face (or phone PIN) on open and after 1+ minute away. */
    val appLockEnabled: Flow<Boolean> = context.dataStore.data.map { it[appLockKey] ?: false }

    suspend fun setAppLockEnabled(enabled: Boolean) {
        context.dataStore.edit { it[appLockKey] = enabled }
    }

    val currencyCode: Flow<String> = context.dataStore.data.map { it[currencyKey] ?: "USD" }

    suspend fun setCurrency(code: String) {
        context.dataStore.edit { it[currencyKey] = code }
    }

    // 0.0 means "no budget set" — the same recurring amount applies to every
    // month, there's no per-month value to store.
    val monthlyBudget: Flow<Double> = context.dataStore.data.map { it[budgetKey] ?: 0.0 }

    suspend fun setMonthlyBudget(amount: Double) {
        context.dataStore.edit { it[budgetKey] = amount }
        // The widget shows budget status, and nothing else refreshes it on a
        // budget change (currency changes refresh via ExpenseRepository).
        PocketWiseWidget().updateAll(context)
    }
}
