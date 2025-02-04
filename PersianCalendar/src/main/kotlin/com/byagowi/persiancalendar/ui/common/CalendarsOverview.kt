package com.byagowi.persiancalendar.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.entities.CalendarType
import com.byagowi.persiancalendar.entities.EventsStore
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.global.isAstronomicalExtraFeaturesEnabled
import com.byagowi.persiancalendar.global.isForcedIranTimeEnabled
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.spacedColon
import com.byagowi.persiancalendar.ui.theme.appCrossfadeSpec
import com.byagowi.persiancalendar.utils.calculateDaysDifference
import com.byagowi.persiancalendar.utils.formatDate
import com.byagowi.persiancalendar.utils.formatDateAndTime
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.generateZodiacInformation
import com.byagowi.persiancalendar.utils.getA11yDaySummary
import com.byagowi.persiancalendar.utils.isMoonInScorpio
import com.byagowi.persiancalendar.utils.monthName
import com.byagowi.persiancalendar.utils.toGregorianCalendar
import com.byagowi.persiancalendar.utils.toLinearDate
import io.github.cosinekitty.astronomy.seasons
import io.github.persiancalendar.calendar.PersianDate
import java.util.Date

@Composable
fun CalendarsOverview(
    jdn: Jdn,
    selectedCalendar: CalendarType,
    shownCalendars: List<CalendarType>,
    isExpanded: Boolean,
    toggleExpansion: () -> Unit
) {
    val context = LocalContext.current
    val isToday = Jdn.today() == jdn
    Column(
        Modifier
            .clickable(
                indication = rememberRipple(bounded = false),
                interactionSource = remember { MutableInteractionSource() },
                onClickLabel = stringResource(R.string.more),
                onClick = toggleExpansion,
            )
            .semantics {
                this.contentDescription = getA11yDaySummary(
                    context.resources,
                    jdn,
                    isToday,
                    EventsStore.empty(),
                    withZodiac = true,
                    withOtherCalendars = true,
                    withTitle = true
                )
            },
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            AnimatedVisibility(isAstronomicalExtraFeaturesEnabled && isExpanded) {
                AndroidView(
                    factory = ::MoonView,
                    update = { it.jdn = jdn.value.toFloat() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                )
            }
            AnimatedContent(
                if (isToday && isForcedIranTimeEnabled.value) language.value.inParentheses.format(
                    jdn.dayOfWeekName, stringResource(R.string.iran_time)
                ) else jdn.dayOfWeekName,
                transitionSpec = appCrossfadeSpec,
                label = "weekday name",
            ) { SelectionContainer { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
        Spacer(Modifier.height(8.dp))
        CalendarsFlow(shownCalendars, jdn)
        Spacer(Modifier.height(4.dp))

        val date = jdn.toCalendar(selectedCalendar)
        val equinox = remember(selectedCalendar, jdn) {
            if (date !is PersianDate) return@remember null
            if (date.month == 12 && date.dayOfMonth >= 20 || date.month == 1 && date.dayOfMonth == 1) {
                val addition = if (date.month == 12) 1 else 0
                val equinoxYear = date.year + addition
                val calendar = Date(
                    seasons(jdn.toCivilDate().year).marchEquinox.toMillisecondsSince1970()
                ).toGregorianCalendar()
                context.getString(
                    R.string.spring_equinox, formatNumber(equinoxYear), calendar.formatDateAndTime()
                )
            } else null
        }
        AnimatedVisibility(visible = equinox != null) {
            SelectionContainer {
                Text(
                    equinox ?: "",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }

        AnimatedVisibility(!isToday) {
            AnimatedContent(
                listOf(
                    stringResource(R.string.days_distance),
                    spacedColon,
                    calculateDaysDifference(context.resources, jdn)
                ).joinToString(""),
                transitionSpec = appCrossfadeSpec,
                label = "diff days",
            ) { state ->
                SelectionContainer {
                    Text(
                        state,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
        }

        val showIsMoonInScorpio = isAstronomicalExtraFeaturesEnabled && isMoonInScorpio(jdn)
        AnimatedVisibility(showIsMoonInScorpio) {
            SelectionContainer {
                Text(
                    stringResource(R.string.moonInScorpio),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        AnimatedVisibility(isExpanded && isAstronomicalExtraFeaturesEnabled) {
            SelectionContainer {
                Text(
                    generateZodiacInformation(context.resources, jdn, withEmoji = true),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        val startOfYearJdn = Jdn(selectedCalendar, date.year, 1, 1)
        val endOfYearJdn = Jdn(selectedCalendar, date.year + 1, 1, 1) - 1
        val currentWeek = jdn.getWeekOfYear(startOfYearJdn)
        val weeksCount = endOfYearJdn.getWeekOfYear(startOfYearJdn)
        val progresses = remember(jdn, selectedCalendar) {
            val (seasonPassedDays, seasonDaysCount) = jdn.calculatePersianSeasonPassedDaysAndCount()
            val monthLength = selectedCalendar.getMonthLength(date.year, date.month)
            listOfNotNull(
                Triple(R.string.month, date.dayOfMonth, monthLength),
                Triple(R.string.season, seasonPassedDays, seasonDaysCount),
                Triple(R.string.year, jdn - startOfYearJdn, endOfYearJdn - startOfYearJdn),
            )
        }

        var firstShow by rememberSaveable { mutableStateOf(true) }
        LaunchedEffect(Unit) { firstShow = false }
        val indicatorStrokeWidth by animateDpAsState(
            if (isExpanded && !firstShow) ProgressIndicatorDefaults.CircularStrokeWidth else 0.dp,
            animationSpec = tween(800),
            label = "stroke width",
        )

        AnimatedVisibility(isExpanded) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                progresses.forEach { (stringId, current, max) ->
                    val title = stringResource(stringId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .semantics {
                                this.contentDescription = "$title$spacedColon$current / $max"
                            }
                            .padding(all = 8.dp),
                    ) {
                        val progress by animateFloatAsState(
                            current.toFloat() / max,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessLow,
                            ),
                            label = "label"
                        )
                        CircularProgressIndicator(
                            progress = { progress },
                            strokeWidth = indicatorStrokeWidth
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        AnimatedVisibility(isExpanded) {
            val startOfYearText = stringResource(
                R.string.start_of_year_diff,
                formatNumber(jdn - startOfYearJdn + 1),
                formatNumber(currentWeek),
                formatNumber(date.month)
            )
            val endOfYearText = stringResource(
                R.string.end_of_year_diff,
                formatNumber(endOfYearJdn - jdn),
                formatNumber(weeksCount - currentWeek),
                formatNumber(12 - date.month)
            )
            SelectionContainer {
                Text(
                    "$startOfYearText\n$endOfYearText",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        val angle by animateFloatAsState(if (isExpanded) 180f else 0f, label = "angle")
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            modifier = Modifier
                .rotate(angle)
                .size(16.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CalendarsFlow(calendarsToShow: List<CalendarType>, jdn: Jdn) {
    @OptIn(ExperimentalLayoutApi::class) FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        calendarsToShow.forEach { calendar ->
            AnimatedContent(
                targetState = jdn.toCalendar(calendar),
                label = "date",
                transitionSpec = appCrossfadeSpec,
            ) { date ->
                Column(
                    modifier = Modifier.defaultMinSize(
                        minWidth = dimensionResource(R.dimen.calendar_item_size),
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val clipboardManager = LocalClipboardManager.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = false),
                            ) { clipboardManager.setText(AnnotatedString(formatDate(date))) }
                            .semantics { this.contentDescription = formatDate(date) },
                    ) {
                        Text(
                            formatNumber(date.dayOfMonth),
                            style = MaterialTheme.typography.displayMedium,
                        )
                        Text(date.monthName)
                    }
                    SelectionContainer { Text(date.toLinearDate()) }
                }
            }
        }
    }
}
