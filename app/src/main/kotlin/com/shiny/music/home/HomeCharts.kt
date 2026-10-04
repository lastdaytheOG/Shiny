package com.shiny.music.home

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Which YouTube Charts Home shows.
 *
 * The default is the listener's location: the country of their mobile network, else of
 * their SIM, else the device's region — all read without any location permission. The
 * listener can pick any other country YouTube Charts publishes, Global only, or no charts.
 */
object HomeCharts {
    const val AUTO = "AUTO"
    const val OFF = "OFF"
    const val GLOBAL = "ZZ"

    /** The countries YouTube Charts publishes a chart for, as its country picker listed them on 2026-09-18. */
    val COUNTRIES: List<String> = listOf(
        "AR", "AU", "AT", "BE", "BO", "BR", "CA", "CL", "CO", "CR", "CZ", "DK", "DO", "EC", "EG",
        "SV", "EE", "FI", "FR", "DE", "GT", "HN", "HK", "HU", "IS", "IN", "ID", "IE", "IL", "IT",
        "JP", "KE", "LU", "MY", "MX", "NL", "NZ", "NI", "NG", "NO", "PA", "PY", "PE", "PH", "PL",
        "PT", "RO", "RU", "SA", "RS", "SG", "ZA", "KR", "ES", "SE", "CH", "TW", "TZ", "TH", "TR",
        "UG", "UA", "AE", "GB", "US", "UY", "VN", "ZW",
    )

    /** The listener's country, if YouTube Charts has a chart for it. */
    fun detectCountry(context: Context): String? {
        val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        return listOf(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
            .mapNotNull { it?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotEmpty) }
            .firstOrNull { it in COUNTRIES }
    }

    /**
     * The charts to show, in order. With no detectable location the automatic setting falls
     * back to Global rather than guessing a country.
     */
    fun scopes(setting: String?, showGlobal: Boolean, detected: String?): List<String> = when (val s = setting ?: AUTO) {
        OFF -> emptyList()
        GLOBAL -> listOf(GLOBAL)
        AUTO -> if (detected == null) listOf(GLOBAL) else listOfNotNull(detected, GLOBAL.takeIf { showGlobal })
        else -> if (s in COUNTRIES) listOfNotNull(s, GLOBAL.takeIf { showGlobal }) else listOf(GLOBAL)
    }

    /** A country's name in the listener's language. */
    fun countryName(code: String): String =
        runCatching { Locale.Builder().setRegion(code).build().displayCountry }.getOrNull()?.ifBlank { null } ?: code
}
