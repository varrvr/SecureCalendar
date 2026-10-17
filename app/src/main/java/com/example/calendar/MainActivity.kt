package com.example.calendar

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

// -------------------- DATABASE --------------------

@Entity(
    tableName = "events",
    indices = [
        Index("startTime"),
        Index("endTime")
    ]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val title: String,

    val startTime: Long,

    val endTime: Long,

    val isPublic: Boolean = false
)

@Dao
interface EventDao {

    @Query("SELECT * FROM events ORDER BY startTime")
    fun observeAll(): Flow<List<EventEntity>>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): EventEntity?

    @Query(
        """
        SELECT * FROM events
        WHERE startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getForPeriod(
        start: Long,
        end: Long
    ): List<EventEntity>

    @Query(
        """
        SELECT * FROM events
        WHERE isPublic = 1
        AND startTime < :end
        AND endTime > :start
        ORDER BY startTime
        """
    )
    suspend fun getPublicForPeriod(
        start: Long,
        end: Long
    ): List<EventEntity>
}

@Database(
    entities = [EventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class CalendarDatabase : RoomDatabase() {

    abstract fun eventDao(): EventDao

    companion object {

        @Volatile
        private var instance: CalendarDatabase? = null

        fun getInstance(application: Application): CalendarDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    application,
                    CalendarDatabase::class.java,
                    "secure_calendar.db"
                ).build().also {
                    instance = it
                }
            }
        }
    }
}

// -------------------- DOMAIN --------------------

data class TimeInterval(
    val start: Instant,
    val end: Instant
)

enum class CalendarTab {
    CALENDAR,
    COMPARISON
}

private val ruLocale = Locale.forLanguageTag("ru-RU")
private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val monthFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", ruLocale)

fun EventEntity.startInstant(): Instant =
    Instant.ofEpochMilli(startTime)

fun EventEntity.endInstant(): Instant =
    Instant.ofEpochMilli(endTime)

fun EventEntity.overlaps(
    start: Instant,
    end: Instant
): Boolean {
    return startTime < end.toEpochMilli() &&
            endTime > start.toEpochMilli()
}

fun dayBounds(
    date: LocalDate,
    zone: ZoneId = ZoneId.systemDefault()
): TimeInterval {
    return TimeInterval(
        start = date.atStartOfDay(zone).toInstant(),
        end = date.plusDays(1).atStartOfDay(zone).toInstant()
    )
}

fun formatTime(instant: Instant): String {
    return instant
        .atZone(ZoneId.systemDefault())
        .toLocalTime()
        .format(timeFormatter)
}

// -------------------- VIEWMODEL --------------------

class CalendarViewModel(application: Application) :
    AndroidViewModel(application) {

    private val dao = CalendarDatabase
        .getInstance(application)
        .eventDao()

    val events = dao.observeAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    var displayedMonth by mutableStateOf(YearMonth.now())
        private set

    var selectedDate by mutableStateOf(LocalDate.now())
        private set

    var selectedTab by mutableStateOf(CalendarTab.CALENDAR)
        private set

    var editingEvent by mutableStateOf<EventEntity?>(null)
        private set

    var showEditor by mutableStateOf(false)
        private set

    fun selectTab(tab: CalendarTab) {
        selectedTab = tab
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        displayedMonth = YearMonth.from(date)
    }

    fun previousMonth() {
        displayedMonth = displayedMonth.minusMonths(1)
        selectedDate = displayedMonth.atDay(1)
    }

    fun nextMonth() {
        displayedMonth = displayedMonth.plusMonths(1)
        selectedDate = displayedMonth.atDay(1)
    }

    fun openCreateEditor() {
        editingEvent = null
        showEditor = true
    }

    fun openEditEditor(event: EventEntity) {
        editingEvent = event
        showEditor = true
    }

    fun closeEditor() {
        showEditor = false
        editingEvent = null
    }

    fun saveEvent(
        existing: EventEntity?,
        title: String,
        start: Instant,
        end: Instant,
        isPublic: Boolean
    ) {
        require(title.isNotBlank())
        require(end.isAfter(start))

        viewModelScope.launch {
            val entity = EventEntity(
                id = existing?.id ?: 0,
                title = title.trim(),
                startTime = start.toEpochMilli(),
                endTime = end.toEpochMilli(),
                isPublic = isPublic
            )

            if (existing == null) {
                dao.insert(entity)
            } else {
                dao.update(entity)
            }

            closeEditor()
        }
    }

    fun deleteEvent(event: EventEntity) {
        viewModelScope.launch {
            dao.delete(event)
            closeEditor()
        }
    }

    suspend fun getIntervalsForPsi(
        period: TimeInterval,
        publicOnly: Boolean
    ): List<TimeInterval> {

        val matchingEvents = if (publicOnly) {
            dao.getPublicForPeriod(
                period.start.toEpochMilli(),
                period.end.toEpochMilli()
            )
        } else {
            dao.getForPeriod(
                period.start.toEpochMilli(),
                period.end.toEpochMilli()
            )
        }

        return matchingEvents.mapNotNull { event ->

            val start = maxOf(
                event.startInstant(),
                period.start
            )

            val end = minOf(
                event.endInstant(),
                period.end
            )

            if (end.isAfter(start)) {
                TimeInterval(start, end)
            } else {
                null
            }
        }
    }
}

// -------------------- ACTIVITY --------------------

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                SecureCalendarApp()
            }
        }
    }
}

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

// -------------------- EVENT CARD --------------------

@Composable
fun EventCard(
    event: EventEntity,
    onClick: () -> Unit
) {
    val start = event.startInstant()
    val end = event.endInstant()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${formatTime(start)}–${formatTime(end)}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (event.isPublic) {
                    "Публичная встреча"
                } else {
                    "Приватная встреча"
                },
                color = if (event.isPublic) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

// -------------------- EVENT EDITOR --------------------

@Composable
fun EventEditorDialog(
    existingEvent: EventEntity?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (
        EventEntity?,
        String,
        Instant,
        Instant,
        Boolean
    ) -> Unit,
    onDelete: (EventEntity) -> Unit
) {
    val zone = ZoneId.systemDefault()

    val existingStart = existingEvent
        ?.startInstant()
        ?.atZone(zone)

    val existingEnd = existingEvent
        ?.endInstant()
        ?.atZone(zone)

    var title by remember(existingEvent) {
        mutableStateOf(existingEvent?.title ?: "")
    }

    var startDateText by remember(existingEvent) {
        mutableStateOf(
            (existingStart?.toLocalDate() ?: selectedDate)
                .format(dateFormatter)
        )
    }

    var endDateText by remember(existingEvent) {
        mutableStateOf(
            (existingEnd?.toLocalDate() ?: selectedDate)
                .format(dateFormatter)
        )
    }

    var startTimeText by remember(existingEvent) {
        mutableStateOf(
            existingStart?.toLocalTime()
                ?.format(timeFormatter) ?: "09:00"
        )
    }

    var endTimeText by remember(existingEvent) {
        mutableStateOf(
            existingEnd?.toLocalTime()
                ?.format(timeFormatter) ?: "10:00"
        )
    }

    var isPublic by remember(existingEvent) {
        mutableStateOf(existingEvent?.isPublic ?: false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingEvent == null) {
                    "Новая встреча"
                } else {
                    "Редактирование встречи"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        error = null
                    },
                    label = {
                        Text("Название")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startDateText,
                    onValueChange = {
                        startDateText = it
                        error = null
                    },
                    label = {
                        Text("Дата начала")
                    },
                    supportingText = {
                        Text("ГГГГ-ММ-ДД")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startTimeText,
                    onValueChange = {
                        startTimeText = it
                        error = null
                    },
                    label = {
                        Text("Время начала")
                    },
                    supportingText = {
                        Text("ЧЧ:ММ")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endDateText,
                    onValueChange = {
                        endDateText = it
                        error = null
                    },
                    label = {
                        Text("Дата окончания")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endTimeText,
                    onValueChange = {
                        endTimeText = it
                        error = null
                    },
                    label = {
                        Text("Время окончания")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Text("Публичная встреча")

                    Switch(
                        checked = isPublic,
                        onCheckedChange = {
                            isPublic = it
                        }
                    )
                }

                if (error != null) {
                    Text(
                        text = error.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (existingEvent != null) {
                    TextButton(
                        onClick = {
                            onDelete(existingEvent)
                        }
                    ) {
                        Text(
                            text = "Удалить встречу",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    try {
                        if (title.isBlank()) {
                            error = "Введите название встречи"
                            return@TextButton
                        }

                        val startDate =
                            LocalDate.parse(startDateText, dateFormatter)

                        val endDate =
                            LocalDate.parse(endDateText, dateFormatter)

                        val startTime =
                            LocalTime.parse(startTimeText, timeFormatter)

                        val endTime =
                            LocalTime.parse(endTimeText, timeFormatter)

                        val start = startDate
                            .atTime(startTime)
                            .atZone(zone)
                            .toInstant()

                        val end = endDate
                            .atTime(endTime)
                            .atZone(zone)
                            .toInstant()

                        if (!end.isAfter(start)) {
                            error = "Окончание должно быть позже начала"
                            return@TextButton
                        }

                        onSave(
                            existingEvent,
                            title,
                            start,
                            end,
                            isPublic
                        )

                    } catch (_: DateTimeParseException) {
                        error = "Проверьте формат даты и времени"
                    } catch (_: DateTimeException) {
                        error = "Некорректные дата или время"
                    }
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

// -------------------- COMPARISON --------------------

@Composable
fun ComparisonScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "Защищённое сравнение",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Здесь можно будет находить пересечения " +
                    "календарей с помощью протокола PSI.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Криптографические операции будут " +
                    "выполняться на устройствах пользователей " +
                    "с использованием Bee2.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {},
            enabled = false
        ) {
            Text("Сравнить календари")
        }
    }
}