package com.example.calendar.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.data.local.EventEntity
import com.example.calendar.presentation.calendar.components.EventCard
import com.example.calendar.presentation.calendar.components.MonthCalendar
import com.example.calendar.presentation.calendar.components.buildMonthDates
import dayBounds
import overlaps
import ruLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// -------------------- CALENDAR SCREEN --------------------

@Composable
fun CalendarScreen(
    modifier: Modifier = Modifier,
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<EventEntity>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onEventClick: (EventEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()

    val selectedDayBounds = dayBounds(selectedDate, zone)

    val selectedEvents = events.filter {
        it.overlaps(
            selectedDayBounds.start,
            selectedDayBounds.end
        )
    }

    val monthDates = buildMonthDates(displayedMonth)

    val datesWithEvents = monthDates.filter { date ->
        val bounds = dayBounds(date, zone)

        events.any {
            it.overlaps(bounds.start, bounds.end)
        }
    }.toSet()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Мой календарь",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(20.dp))

        MonthCalendar(
            month = displayedMonth,
            selectedDate = selectedDate,
            datesWithEvents = datesWithEvents,
            onDateSelected = onDateSelected,
            onPreviousMonth = onPreviousMonth,
            onNextMonth = onNextMonth
        )

        Spacer(Modifier.height(24.dp))

        Text(
            text = selectedDate.format(
                DateTimeFormatter.ofPattern(
                    "d MMMM, EEEE",
                    ruLocale
                )
            ),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(12.dp))

        if (selectedEvents.isEmpty()) {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "На этот день встреч нет",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        } else {

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    bottom = 100.dp
                )
            ) {
                items(
                    items = selectedEvents,
                    key = { it.id }
                ) { event ->

                    EventCard(
                        event = event,
                        onClick = {
                            onEventClick(event)
                        }
                    )
                }
            }
        }
    }
}

