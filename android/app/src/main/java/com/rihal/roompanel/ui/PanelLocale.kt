package com.rihal.roompanel.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.rihal.roompanel.domain.PanelLanguage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats times for the door: the room's zone, the portal's 12/24-hour choice, the shown language. */
class TimeFormat(private val zone: ZoneId, clock24h: Boolean, locale: Locale) {
    private val time = DateTimeFormatter.ofPattern(if (clock24h) "HH:mm" else "h:mm a", locale)
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    fun time(i: Instant): String = time.format(i.atZone(zone))
    fun day(date: java.time.LocalDate): String = day.format(date)
    fun range(a: Instant, b: Instant) = "${time(a)} – ${time(b)}"
}

val LocalTimeFormat = staticCompositionLocalOf { TimeFormat(ZoneId.systemDefault(), true, Locale.ENGLISH) }

private val ARABIC: Locale = Locale.forLanguageTag("ar")

/** The language the door shows now: fixed, or English and Arabic taking turns every [turn] seconds. */
fun shownLocale(language: PanelLanguage, now: Instant, turn: Long = 15): Locale = when (language) {
    PanelLanguage.EN -> Locale.ENGLISH
    PanelLanguage.AR -> ARABIC
    PanelLanguage.BOTH -> if ((now.epochSecond / turn) % 2 == 0L) Locale.ENGLISH else ARABIC
}

/**
 * Renders [content] in [locale] (strings and RTL), whatever the tablet's system language.
 * The app ships as one APK with every language inside (no Play Store app bundle), so there are
 * no language splits to download.
 */
@SuppressLint("AppBundleLocaleChanges")
@Composable
fun PanelLocale(locale: Locale, zone: ZoneId, clock24h: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val base = LocalConfiguration.current
    val config = remember(locale, base) {
        Configuration(base).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
    }
    val localized = remember(config) { context.createConfigurationContext(config) }
    val rtl = config.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides config,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalTimeFormat provides remember(zone, clock24h, locale) { TimeFormat(zone, clock24h, locale) },
        content = content,
    )
}
