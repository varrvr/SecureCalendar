package com.example.calendar.presentation.editor

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calendar.domain.model.CalendarTab
import com.example.calendar.presentation.calendar.CalendarScreen
import com.example.calendar.presentation.calendar.CalendarViewModel
import com.example.calendar.presentation.comparison.ComparisonScreen

// -------------------- APPLICATION --------------------

@Composable
fun SecureCalendarApp(
    calendarViewModel: CalendarViewModel = viewModel()
) {
    val events by calendarViewModel.events.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {

                NavigationBarItem(
                    selected = calendarViewModel.selectedTab ==
                            CalendarTab.CALENDAR,
                    onClick = {
                        calendarViewModel.selectTab(
                            CalendarTab.CALENDAR
                        )
                    },
                    icon = {
                        Text("📅")
                    },
                    label = {
                        Text("Календарь")
                    }
                )

                NavigationBarItem(
                    selected = calendarViewModel.selectedTab ==
                            CalendarTab.COMPARISON,
                    onClick = {
                        calendarViewModel.selectTab(
                            CalendarTab.COMPARISON
                        )
                    },
                    icon = {
                        Text("🔐")
                    },
                    label = {
                        Text("Сравнение")
                    }
                )
            }
        },
        floatingActionButton = {
            if (
                calendarViewModel.selectedTab ==
                CalendarTab.CALENDAR
            ) {
                FloatingActionButton(
                    onClick = {
                        calendarViewModel.openCreateEditor()
                    }
                ) {
                    Text("+")
                }
            }
        }
    ) { padding ->

        when (calendarViewModel.selectedTab) {

            CalendarTab.CALENDAR -> {
                CalendarScreen(
                    modifier = Modifier.padding(padding),
                    displayedMonth =
                        calendarViewModel.displayedMonth,
                    selectedDate =
                        calendarViewModel.selectedDate,
                    events = events,
                    onDateSelected =
                        calendarViewModel::selectDate,
                    onPreviousMonth =
                        calendarViewModel::previousMonth,
                    onNextMonth =
                        calendarViewModel::nextMonth,
                    onEventClick =
                        calendarViewModel::openEditEditor
                )
            }

            CalendarTab.COMPARISON -> {
                ComparisonScreen(
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }

    if (calendarViewModel.showEditor) {
        EventEditorDialog(
            existingEvent = calendarViewModel.editingEvent,
            selectedDate = calendarViewModel.selectedDate,
            onDismiss = calendarViewModel::closeEditor,
            onSave = calendarViewModel::saveEvent,
            onDelete = calendarViewModel::deleteEvent
        )
    }
}

