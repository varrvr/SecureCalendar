
package com.example.calendar.presentation.calendar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.presentation.calendar.components.EventCard
import com.example.calendar.presentation.calendar.components.MonthCalendar
import com.example.calendar.presentation.calendar.components.buildMonthDates
import com.example.calendar.util.dayBounds
import com.example.calendar.util.overlaps
import com.example.calendar.util.ruLocale
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CalendarScreen(
    displayedMonth: YearMonth,
    selectedDate: LocalDate,
    events: List<CalendarEvent>,
    onDateSelected: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClick: () -> Unit,
    onEventClick: (CalendarEvent) -> Unit,
    onAddEventClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val zone = ZoneId.systemDefault()

    val bounds = dayBounds(selectedDate, zone)

    val selectedEvents = events
        .filter { it.start.isBefore(bounds.end) && it.end.isAfter(bounds.start) }
        .sortedBy { it.start }

    val datesWithEvents = buildMonthDates(displayedMonth)
        .filter { date ->
            val interval = dayBounds(date, zone)

            events.any {
                it.start.isBefore(interval.end) && it.end.isAfter(interval.start)
            }
        }
        .toSet()

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Мой календарь",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            MonthCalendar(
                month = displayedMonth,
                selectedDate = selectedDate,
                datesWithEvents = datesWithEvents,
                onDateSelected = onDateSelected,
                onPreviousMonth = onPreviousMonth,
                onNextMonth = onNextMonth,
                onTodayClick = onTodayClick
            )

            Spacer(Modifier.height(20.dp))

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
                        .padding(top = 28.dp),
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
                    contentPadding = PaddingValues(bottom = 96.dp)
                ) {
                    items(
                        items = selectedEvents,
                        key = { it.id }
                    ) { event ->
                        EventCard(
                            event = event,
                            onClick = { onEventClick(event) }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onAddEventClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Создать встречу"
            )
        }
    }
}
