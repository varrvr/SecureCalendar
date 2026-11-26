
package com.example.calendar.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.calendar.presentation.components.DateSelectionDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.calendar.util.monthFormatter
import com.example.calendar.util.ruLocale
import java.time.LocalDate
import java.time.YearMonth


fun buildMonthDates(month: YearMonth): List<LocalDate> {
    val firstDay = month.atDay(1)
    val offset = firstDay.dayOfWeek.value - 1
    val gridStart = firstDay.minusDays(offset.toLong())
    val rows = (offset + month.lengthOfMonth() + 6) / 7

    return (0 until rows * 7).map {
        gridStart.plusDays(it.toLong())
    }
}

@Composable
fun MonthCalendar(
    month: YearMonth,
    selectedDate: LocalDate,
    datesWithEvents: Set<LocalDate>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    var chooseDate by rememberSaveable { mutableStateOf(false) }
    if (chooseDate) DateSelectionDialog(selectedDate, "Перейти к дню", { chooseDate = false }) {
        onDateSelected(it)
        chooseDate = false
    }

    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { chooseDate = true }, modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)) {
                Text(month.format(monthFormatter).replaceFirstChar { it.titlecase(ruLocale) },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ExpandMore, null, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onTodayClick) {
                Icon(Icons.Outlined.Today, contentDescription = "Перейти к сегодняшнему дню")
            }

            IconButton(onClick = onPreviousMonth) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Предыдущий месяц"
                )
            }

            IconButton(onClick = onNextMonth) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Следующий месяц"
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth()) {
            listOf(
                "Пн", "Вт", "Ср", "Чт",
                "Пт", "Сб", "Вс"
            ).forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        buildMonthDates(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val selected = date == selectedDate
                    val currentMonth = YearMonth.from(date) == month
                    val isToday = date == today

                    val textColor = when {
                        selected -> MaterialTheme.colorScheme.onPrimary
                        !currentMonth -> MaterialTheme.colorScheme.outline
                        isToday -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(2.dp)
                            .background(
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onDateSelected(date) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            color = textColor,
                            fontWeight = if (selected || isToday) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )

                        if (date in datesWithEvents) {
                            Spacer(Modifier.height(3.dp))

                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        shape = CircleShape
                                    )
                            )
                        } else if (isToday && !selected) {
                            Box(
                                modifier = Modifier
                                    .size(3.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
