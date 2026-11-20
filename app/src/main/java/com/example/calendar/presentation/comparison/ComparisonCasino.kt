package com.example.calendar.presentation.comparison

import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private val CabinetGreen = Color(0xFF315B19)
private val CabinetLime = Color(0xFFD8FA9B)

@Composable
internal fun ComparisonCasino(
    date: LocalDate,
    enabled: Boolean,
    onDateSelected: (LocalDate) -> Unit
) {
    var time by rememberSaveable { mutableStateOf("09:00") }
    var day by rememberSaveable(date) { mutableStateOf(date.dayOfMonth.toString()) }
    var month by rememberSaveable(date) { mutableStateOf(date.monthValue.toString()) }
    var year by rememberSaveable(date) { mutableStateOf(date.year.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0) }
    val lever = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val active = enabled && !running
    val launchMachine: () -> Unit = launchMachine@{
        if (!active) return@launchMachine
        val chosenDate: LocalDate
        try {
            chosenDate = LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            require(year.length == 4 && chosenDate.year in 1..9999)
            require(Regex("[0-9]{2}:[0-9]{2}").matches(time))
            require(time.substring(0, 2).toInt() in 0..23 && time.substring(3).toInt() in 0..59)
            LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (_: DateTimeException) {
            error = "Проверьте дату и время (ЧЧ:ММ)"
            return@launchMachine
        } catch (_: IllegalArgumentException) {
            error = "Введите существующую дату и время в формате ЧЧ:ММ"
            return@launchMachine
        }
        error = null
        running = true
        completed = 0
        scope.launch {
            try {
                lever.animateTo(1f, tween(250))
                onDateSelected(chosenDate)
                repeat(40) { step ->
                    tick++
                    completed = when {
                        step >= 36 -> 4
                        step >= 31 -> 3
                        step >= 26 -> 2
                        step >= 21 -> 1
                        else -> 0
                    }
                    delay(55L + step * 2L)
                }
                lever.animateTo(0f, tween(350))
            } finally {
                running = false
            }
        }
    }
    val currentLaunch by rememberUpdatedState(launchMachine)
    fun changed() { completed = 0; error = null }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(CabinetLime, Color(0xFF9CD453))))
            .border(2.dp, CabinetGreen, RoundedCornerShape(24.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Календарный автомат", color = CabinetGreen,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Введите дату выбранной границы и опустите рычаг", color = CabinetGreen)
        // Keep the four input fields readable on narrow screens and with large fonts.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Время" to time, "День" to day, "Месяц" to month, "Год" to year)
                .forEachIndexed { index, (label, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { input ->
                            val filtered = input.filter { it in '0'..'9' || (index == 0 && it == ':') }
                                .take(if (index == 0) 5 else if (index == 3) 4 else 2)
                            when (index) {
                                0 -> time = filtered
                                1 -> day = filtered
                                2 -> month = filtered
                                else -> year = filtered
                            }
                            changed()
                        },
                        label = { Text(label) },
                        enabled = active,
                        singleLine = true,
                        modifier = Modifier.width(if (index == 0) 100.dp else 80.dp),
                        keyboardOptions = KeyboardOptions(keyboardType =
                            if (index == 0) KeyboardType.Text else KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = CabinetGreen, unfocusedTextColor = CabinetGreen,
                            focusedLabelColor = CabinetGreen, unfocusedLabelColor = CabinetGreen,
                            focusedBorderColor = CabinetGreen, unfocusedBorderColor = CabinetGreen)
                    )
                }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Two rows keep all four windows visible on small phones.
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(2) { column ->
                            val index = row * 2 + column
                            val value = when {
                                completed > index -> "Готово"
                                running -> when (index) {
                                    0 -> "%02d:%02d".format((tick * 7) % 24, (tick * 13) % 60)
                                    1 -> ((tick * 7) % 31 + 1).toString().padStart(2, '0')
                                    2 -> ((tick * 5) % 12 + 1).toString().padStart(2, '0')
                                    else -> (2000 + (tick * 17) % 100).toString()
                                }
                                else -> listOf(time, day, month, year)[index]
                            }
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(listOf("Время", "День", "Месяц", "Год")[index], color = CabinetGreen,
                                    style = MaterialTheme.typography.labelSmall)
                                Box(Modifier.fillMaxWidth().height(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Brush.verticalGradient(listOf(CabinetGreen, Color(0xFF568C2D), CabinetGreen)))
                                    .border(2.dp, CabinetLime, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center) {
                                    androidx.compose.animation.AnimatedContent(
                                        targetState = value,
                                        transitionSpec = {
                                            (androidx.compose.animation.slideInVertically(tween(65)) { -it } +
                                                androidx.compose.animation.fadeIn(tween(65))) togetherWith
                                                (androidx.compose.animation.slideOutVertically(tween(65)) { it } +
                                                    androidx.compose.animation.fadeOut(tween(65)))
                                        }, label = "Барабан $index"
                                    ) { text ->
                                        Text(text, color = CabinetLime, fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            var drag by remember { mutableStateOf(0f) }
            Canvas(Modifier.width(44.dp).height(168.dp)
                .semantics {
                    contentDescription = "Рычаг автомата. Потяните вниз, чтобы запустить"
                    onClick(label = "Опустить рычаг") { launchMachine(); true }
                }
                .pointerInput(active) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onVerticalDrag = { change, amount ->
                            if (active) { change.consume(); drag += amount }
                        },
                        onDragEnd = { if (drag > 24.dp.toPx()) currentLaunch(); drag = 0f },
                        onDragCancel = { drag = 0f }
                    )
                }) {
                val x = size.width / 2
                val knobY = 24.dp.toPx() + lever.value * 100.dp.toPx()
                drawLine(CabinetGreen, Offset(x, size.height * 0.65f), Offset(x, knobY), 8.dp.toPx())
                drawCircle(CabinetGreen, 12.dp.toPx(), Offset(x, size.height * 0.65f))
                drawCircle(Color(0xFF75B82D), 18.dp.toPx(), Offset(x, knobY))
                drawCircle(CabinetLime, 6.dp.toPx(), Offset(x - 4.dp.toPx(), knobY - 5.dp.toPx()))
            }
        }
        Button(onClick = launchMachine, enabled = active, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = CabinetGreen, contentColor = CabinetLime)) {
            Text(if (running) "Барабаны вращаются…" else "Опустить рычаг")
        }
        if (error != null) Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
        Text("Время используется в анимации; период сравнения задаётся датами.",
            color = CabinetGreen, style = MaterialTheme.typography.bodySmall)
        Text(if (completed == 4 && !running) "Готово. Дата периода установлена."
            else "Анимация подготовки. Сравнение запускается кнопкой ниже.",
            color = CabinetGreen, style = MaterialTheme.typography.bodySmall)
    }
}
