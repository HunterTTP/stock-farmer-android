package com.hunter.stockfarmer.ui.hud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hunter.stockfarmer.core.rules.DayNightCycle
import com.hunter.stockfarmer.core.rules.MIDNIGHT_PRESET_MINUTE
import com.hunter.stockfarmer.core.rules.Season
import com.hunter.stockfarmer.core.rules.Weather
import com.hunter.stockfarmer.core.rules.minuteOfDay
import com.hunter.stockfarmer.core.rules.setTimeOfDay
import com.hunter.stockfarmer.core.rules.setTimeToMidnight
import com.hunter.stockfarmer.core.rules.skipNewDayInProgress
import com.hunter.stockfarmer.game.GameSession
import com.hunter.stockfarmer.ui.theme.HudSheet
import com.hunter.stockfarmer.ui.theme.HudType
import com.hunter.stockfarmer.ui.theme.PillButton
import com.hunter.stockfarmer.ui.theme.PillRole
import com.hunter.stockfarmer.ui.theme.Theme
import com.hunter.stockfarmer.ui.theme.categoryCard
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.abs
import com.hunter.stockfarmer.core.rules.setSeason as setEngineSeason

/** A time of day the Time sheet can jump to, picked to try something out. */
enum class TimePreset(
    val title: String,
    val minute: Double,
    val symbol: String,
) {
    Midnight("Midnight", MIDNIGHT_PRESET_MINUTE, "🌙"),
    Morning("Morning", 6 * 60.0, "🌅"),
    Midday("Midday", 12 * 60.0, "☀️"),
    Rain("Rain", Weather.rainStartMinute, "🌧️"),
    Evening("Evening", 18 * 60.0, "🌇"),
    Night("Night", 22 * 60.0, "🌑"),
    ;

    val clockText: String get() = DayNightCycle.clockText(minute)
}

/** Jumps the clock to a preset. Only Midnight begins a day; any other time counts none, so a night is never replayed. */
fun GameSession.setTime(
    preset: TimePreset,
    now: Instant = Instant.now(),
) = act { engine ->
    endNight(now)
    if (preset == TimePreset.Midnight) {
        engine.setTimeToMidnight(now)
    } else {
        engine.setTimeOfDay(preset.minute, now)
        engine.skipNewDayInProgress(now)
    }
}

fun GameSession.setSeason(season: Season) = act { it.setEngineSeason(season) }

/** The sky at a minute of the day: light blue by day, orange at dawn and dusk, deep blue at night and grey in the rain. */
object TimeSky {
    data class Rgb(
        val red: Double,
        val green: Double,
        val blue: Double,
    )

    private val day = Rgb(0.45, 0.75, 1.0)
    private val night = Rgb(0.05, 0.08, 0.22)
    private val dusk = Rgb(0.95, 0.55, 0.35)
    private val grey = Rgb(0.45, 0.5, 0.58)

    private fun mix(
        a: Double,
        b: Double,
        amount: Double,
    ) = a + (b - a) * amount

    private fun mix(
        a: Rgb,
        b: Rgb,
        amount: Double,
    ) = Rgb(mix(a.red, b.red, amount), mix(a.green, b.green, amount), mix(a.blue, b.blue, amount))

    fun color(minute: Double): Rgb {
        val darkness = DayNightCycle.darkness(minute)
        val rain = Weather.rainAmount(minute)
        val twilight = if (darkness > 0.02 && darkness < 0.6) 1 - abs(darkness - 0.3) / 0.3 else 0.0
        val tinted = mix(mix(day, night, darkness), dusk, twilight * 0.6)
        val dim = 1 - darkness * 0.7
        return mix(tinted, Rgb(grey.red * dim, grey.green * dim, grey.blue * dim), rain * 0.7)
    }

    fun symbol(minute: Double): String {
        val darkness = DayNightCycle.darkness(minute)
        return when {
            Weather.rainAmount(minute) > 0 -> "🌧️"
            darkness > 0.5 -> "🌙"
            darkness > 0.02 -> if (minute < 12 * 60) "🌅" else "🌇"
            else -> "☀️"
        }
    }
}

/** The Time sheet: the clock, the sky, a button for each time of day worth trying and for each season. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSheet(
    session: GameSession,
    onDismiss: () -> Unit,
) {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val (minute, day, season) = session.act { Triple(it.minuteOfDay(now), it.state.day, it.state.season) }
    HudSheet(onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    "Time",
                    style = HudType.headline.copy(color = Theme.textPrimary),
                )
                PillButton(
                    title = "Close",
                    onClick = onDismiss,
                    role = PillRole.neutral,
                    fillWidth = false,
                )
            }
            val sky = TimeSky.color(minute)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(
                        Color(sky.red.toFloat(), sky.green.toFloat(), sky.blue.toFloat()),
                        RoundedCornerShape(18.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) { BasicText(TimeSky.symbol(minute), style = HudType.title.copy(fontSize = 54.sp)) }
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BasicText(
                    "Day $day",
                    style = HudType.title3.copy(fontWeight = FontWeight.SemiBold, color = Theme.textSecondary),
                )
                BasicText(
                    DayNightCycle.clockText(minute),
                    style =
                        HudType.title.copy(
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Theme.textPrimary,
                        ),
                )
            }
            Column(Modifier.categoryCard(false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimePreset.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { preset ->
                            TimeButton(
                                "${preset.symbol} ${preset.title}",
                                preset.clockText,
                                false,
                                Modifier.weight(1f),
                            ) {
                                session.setTime(preset)
                            }
                        }
                    }
                }
            }
            Column(Modifier.categoryCard(false), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Season.entries.forEach {
                    TimeButton(
                        "${seasonSymbol(it)} ${it.name.replaceFirstChar(Char::uppercase)}",
                        null,
                        it == season,
                        Modifier.fillMaxWidth(),
                    ) {
                        session.setSeason(it)
                    }
                }
            }
            BasicText(
                "Night lasts from 9 PM to 5 AM",
                Modifier.fillMaxWidth(),
                style = HudType.footnote.copy(color = Theme.textSecondary, textAlign = TextAlign.Center),
            )
        }
    }
}

@Composable
private fun TimeButton(
    title: String,
    detail: String?,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val ink = if (isSelected) Theme.onAccent else Theme.onNeutral
    Column(
        modifier
            .heightIn(min = if (detail == null) 32.dp else 44.dp)
            .background(if (isSelected) Theme.accent else Theme.textSecondary.copy(alpha = 0.3f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = if (detail == null) Alignment.Start else Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(title, style = HudType.subheadline.copy(fontWeight = FontWeight.SemiBold, color = ink))
        detail?.let { BasicText(it, style = HudType.caption.copy(color = ink.copy(alpha = 0.8f))) }
    }
}

/** Stands in for the SF Symbol each season shows beside its name (leaf, wind, snowflake). */
private fun seasonSymbol(season: Season) =
    when (season) {
        Season.spring -> "🍃"
        Season.fall -> "💨"
        Season.winter -> "❄️"
    }
