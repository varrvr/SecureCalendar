package com.example.calendar.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import monthFormatter
import ruLocale
import java.time.LocalDate
import java.time.YearMonth

// -------------------- MONTH CALENDAR --------------------

fun buildMonthDates(
    month: YearMonth
): List<LocalDate> {

    val firstDay = month.atDay(1)

    val offset = firstDay.dayOfWeek.value - 1

    val gridStart = firstDay.minusDays(
        offset.toLong()
    )

    val requiredCells = offset + month.lengthOfMonth()
    val rows = (requiredCells + 6) / 7

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
    onNextMonth: () -> Unit
) {
    Column {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            TextButton(onClick = onPreviousMonth) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }

            Text(
                text = month.format(monthFormatter)
                    .replaceFirstChar { it.titlecase(ruLocale) },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )

            TextButton(onClick = onNextMonth) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            listOf(
                "Пн", "Вт", "Ср", "Чт",
                "Пт", "Сб", "Вс"
            ).forEach { name ->

                Text(
                    text = name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        val dates = buildMonthDates(month)

        dates.chunked(7).forEach { week ->

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                week.forEach { date ->

                    val isSelected = date == selectedDate
                    val isCurrentMonth =
                        YearMonth.from(date) == month

                    val background = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    }

                    val textColor = when {
                        isSelected ->
                            MaterialTheme.colorScheme.onPrimary

                        isCurrentMonth ->
                            MaterialTheme.colorScheme.onSurface

                        else ->
                            MaterialTheme.colorScheme.outline
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .padding(2.dp)
                            .background(
                                background,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                onDateSelected(date)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {

                        Text(
                            text = date.dayOfMonth.toString(),
                            color = textColor,
                            fontWeight = if (isSelected) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )

                        if (date in datesWithEvents) {
                            Spacer(Modifier.height(4.dp))

                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
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

