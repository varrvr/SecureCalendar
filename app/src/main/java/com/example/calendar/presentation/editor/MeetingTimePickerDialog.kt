package com.example.calendar.presentation.editor

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val LimeDark = Color(0xFF285416)
private val LimeLight = Color(0xFFDAFFA0)
private val WheelColors = listOf(Color(0xFFB6ED64), Color(0xFF8ACB42), Color(0xFFD5F99A))
private val TimeLabel = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MeetingTimePickerDialog(
    current: LocalTime,
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = current.hour,
        initialMinute = current.minute,
        is24Hour = true
    )
    var manual by rememberSaveable { mutableStateOf(true) }
    var spinning by remember { mutableStateOf(false) }
    var hasResult by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf(randomTimes()) }
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = manual,
                        enabled = !spinning,
                        onClick = { manual = true },
                        label = { Text("Вручную ввести") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = !manual,
                        enabled = !spinning,
                        onClick = { manual = false },
                        label = { Text("Крутить барабан") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (manual) {
                    TimeInput(state = state)
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.verticalGradient(listOf(LimeLight, Color(0xFFB1E66B))))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("КОЛЕСО ВРЕМЕНИ", color = LimeDark,
                            style = MaterialTheme.typography.labelLarge)
                        CasinoTimeWheel(candidates, rotation.value)
                        Text(
                            if (spinning) "Колесо вращается…"
                            else if (hasResult) "Выбрано: ${LocalTime.of(state.hour, state.minute).format(TimeLabel)}"
                            else "Испытайте удачу",
                            color = LimeDark,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            enabled = !spinning,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = LimeDark, contentColor = LimeLight),
                            onClick = {
                                spinning = true
                                hasResult = false
                                candidates = randomTimes()
                                val selected = Random.nextInt(candidates.size)
                                scope.launch {
                                    try {
                                        rotation.snapTo(rotation.value % 360f)
                                        // Sector centers land exactly beneath the fixed top pointer.
                                        val landing = (360f - (selected + 0.5f) * 30f) % 360f
                                        val distance = (landing - rotation.value + 360f) % 360f
                                        rotation.animateTo(
                                            rotation.value + 360f * Random.nextInt(5, 8) + distance,
                                            tween(4200, easing = CubicBezierEasing(0.12f, 0.75f, 0.15f, 1f))
                                        )
                                        val chosen = candidates[selected]
                                        state.hour = chosen.hour
                                        state.minute = chosen.minute
                                        hasResult = true
                                    } finally {
                                        spinning = false
                                    }
                                }
                            }
                        ) {
                            Text(if (spinning) "Выбираем время…" else "Крутить колесо")
                        }
                        Text("Случайное время от 00:00 до 23:59",
                            color = LimeDark, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) },
                enabled = !spinning
            ) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

private fun randomTimes(): List<LocalTime> =
    (0 until 1440).shuffled().take(12).map { LocalTime.of(it / 60, it % 60) }

@Composable
private fun CasinoTimeWheel(times: List<LocalTime>, rotation: Float) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics {
        contentDescription = "Колесо случайного выбора времени. Двенадцать секторов, указатель сверху."
    }) {
        val radius = size.minDimension * 0.45f
        val wheelCenter = center
        val bounds = Offset(wheelCenter.x - radius, wheelCenter.y - radius)
        val diameter = Size(radius * 2, radius * 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 68, 16)
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.115f
            typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        }
        rotate(rotation, wheelCenter) {
            times.forEachIndexed { index, time ->
                val start = -90f + index * 30f
                drawArc(WheelColors[index % WheelColors.size], start, 30f,
                    useCenter = true, topLeft = bounds, size = diameter)
                drawArc(LimeDark.copy(alpha = 0.4f), start, 30f,
                    useCenter = true, topLeft = bounds, size = diameter, style = Stroke(1.dp.toPx()))
                val angle = Math.toRadians((start + 15f).toDouble())
                val x = wheelCenter.x + cos(angle).toFloat() * radius * 0.73f
                val y = wheelCenter.y + sin(angle).toFloat() * radius * 0.73f
                rotate(start + 105f, Offset(x, y)) {
                    drawContext.canvas.nativeCanvas.drawText(
                        time.format(TimeLabel), x, y - (paint.ascent() + paint.descent()) / 2, paint)
                }
            }
            drawCircle(LimeDark, radius, wheelCenter, style = Stroke(6.dp.toPx()))
            repeat(36) { index ->
                val angle = Math.toRadians(index * 10.0)
                drawCircle(LimeLight, 2.dp.toPx(), Offset(
                    wheelCenter.x + cos(angle).toFloat() * radius * 0.96f,
                    wheelCenter.y + sin(angle).toFloat() * radius * 0.96f))
            }
        }
        drawCircle(LimeDark, radius * 0.19f, wheelCenter)
        drawCircle(LimeLight, radius * 0.11f, wheelCenter)
        val pointer = Path().apply {
            moveTo(wheelCenter.x - radius * 0.1f, wheelCenter.y - radius - 8.dp.toPx())
            lineTo(wheelCenter.x + radius * 0.1f, wheelCenter.y - radius - 8.dp.toPx())
            lineTo(wheelCenter.x, wheelCenter.y - radius + 14.dp.toPx())
            close()
        }
        drawPath(pointer, LimeDark)
    }
}
