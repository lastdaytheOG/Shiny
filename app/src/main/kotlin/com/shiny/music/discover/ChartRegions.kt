package com.shiny.music.discover

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

data class ChartRegion(val storefront: String, val source: Source) {
    enum class Source { Detected, Chosen, Fallback }
}

object ChartRegions {
    const val FALLBACK = "us"

    // Apple publishes no worldwide feed, so there is deliberately no Global choice.
    val STOREFRONTS: List<String> = listOf(
        "ae", "ar", "at", "au", "be", "bg", "bh", "br", "ca", "ch", "cl", "cn", "co", "cy", "cz",
        "de", "dk", "ee", "eg", "es", "fi", "fr", "gb", "gh", "gr", "hk", "hr", "hu", "id", "ie",
        "il", "in", "is", "it", "jo", "jp", "ke", "kr", "kw", "kz", "lb", "lk", "lt", "lu", "lv",
        "ma", "mt", "mx", "my", "ng", "nl", "no", "np", "nz", "om", "pe", "ph", "pk", "pl", "pt",
        "qa", "ro", "ru", "sa", "se", "sg", "si", "sk", "th", "tr", "tw", "tz", "ua", "ug", "us",
        "vn", "za", "zw",
    )

    private val known = STOREFRONTS.toHashSet()

    fun normalize(code: String?): String? =
        code?.trim()?.lowercase(Locale.ROOT)?.takeIf { it in known }

    fun detect(candidates: List<String?>): String? = candidates.firstNotNullOfOrNull(::normalize)

    fun candidates(context: Context): List<String?> {
        val telephony = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        return listOf(
            runCatching { telephony?.networkCountryIso }.getOrNull(),
            runCatching { telephony?.simCountryIso }.getOrNull(),
            Locale.getDefault().country,
        )
    }

    fun has(code: String): Boolean = normalize(code) != null

    fun resolve(setting: String?, detected: String?): ChartRegion {
        normalize(setting)?.let { return ChartRegion(it, ChartRegion.Source.Chosen) }
        normalize(detected)?.let { return ChartRegion(it, ChartRegion.Source.Detected) }
        return ChartRegion(FALLBACK, ChartRegion.Source.Fallback)
    }

    fun name(code: String, locale: Locale = Locale.getDefault()): String =
        runCatching { Locale.Builder().setRegion(code.uppercase(Locale.ROOT)).build().getDisplayCountry(locale) }
            .getOrNull()?.ifBlank { null } ?: code.uppercase(Locale.ROOT)
}
