package com.pocketwise.core.model

data class CurrencyOption(val code: String, val symbol: String, val label: String)

val SupportedCurrencies = listOf(
    CurrencyOption("USD", "$", "US Dollar"),
    CurrencyOption("EUR", "€", "Euro"),
    CurrencyOption("GBP", "£", "British Pound"),
    CurrencyOption("INR", "₹", "Indian Rupee"),
    CurrencyOption("NPR", "₨", "Nepalese Rupee"),
    CurrencyOption("JPY", "¥", "Japanese Yen"),
    CurrencyOption("CAD", "$", "Canadian Dollar"),
    CurrencyOption("AUD", "$", "Australian Dollar"),
)

fun currencySymbolFor(code: String): String =
    SupportedCurrencies.find { it.code == code }?.symbol ?: code
