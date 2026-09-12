package com.pocketwise.core.data.local

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    val currencyCode: Flow<String> = context.dataStore.data.map { it[currencyKey] ?: "USD" }

    suspend fun setCurrency(code: String) {
        context.dataStore.edit { it[currencyKey] = code }
    }

    // 0.0 means "no budget set" — the same recurring amount applies to every
    // month, there's no per-month value to store.
    val monthlyBudget: Flow<Double> = context.dataStore.data.map { it[budgetKey] ?: 0.0 }

    suspend fun setMonthlyBudget(amount: Double) {
        context.dataStore.edit { it[budgetKey] = amount }
    }
}
