package com.example.calendar.presentation.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.calendar.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.random.Random

private data class Dirt(val x: Float, val y: Float, val radius: Float)

@Composable
internal fun ConnectionCleaningDialog() {
    val context = LocalContext.current
    val dirt = remember {
        val random = Random(42)
        List(140) { Dirt(random.nextFloat(), random.nextFloat(), 8f + random.nextFloat() * 12f) }
    }
    val cleaned = remember { mutableStateListOf<Int>() }
    var sponge by remember { mutableStateOf<Offset?>(null) }
    Dialog(onDismissRequest = {}, properties = DialogProperties(
        dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Surface(shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(context.getString(R.string.cleaning_title), style = MaterialTheme.typography.titleLarge)
                Text(context.getString(R.string.cleaning_help))
                Canvas(Modifier.fillMaxWidth().height(300.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color(0xFFE4F5FC))
                    .semantics { contentDescription = context.getString(R.string.cleaning_description) }
                    .pointerInput(Unit) {
                        fun wipe(position: Offset) {
                            val previous = sponge ?: position
                            val segment = position - previous
                            val lengthSquared = segment.x * segment.x + segment.y * segment.y
                            dirt.forEachIndexed { index, spot ->
                                if (index !in cleaned) {
                                    val center = Offset(spot.x * size.width, spot.y * size.height)
                                    val relative = center - previous
                                    val t = if (lengthSquared == 0f) 0f else
                                        ((relative.x * segment.x + relative.y * segment.y) / lengthSquared).coerceIn(0f, 1f)
                                    if ((center - (previous + segment * t)).getDistance() < 32.dp.toPx() + spot.radius.dp.toPx()) {
                                        cleaned.add(index)
                                    }
                                }
                            }
                            sponge = position
                        }
                        detectDragGestures(onDragStart = { sponge = it; wipe(it) },
                            onDrag = { change, _ -> change.consume(); wipe(change.position) })
                    }) {
                    dirt.forEachIndexed { index, spot ->
                        if (index !in cleaned) {
                            drawCircle(Color(0xFF8C7359).copy(alpha = 0.68f), spot.radius.dp.toPx(),
                                Offset(spot.x * size.width, spot.y * size.height))
                        }
                    }
                    val position = sponge ?: Offset(size.width * 0.5f, size.height * 0.65f)
                    val w = 62.dp.toPx(); val h = 42.dp.toPx()
                    drawRoundRect(Color(0xFFF1D65B), position - Offset(w/2, h/2), Size(w,h),
                        androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()))
                    drawRoundRect(Color(0xFF8CC78B), position - Offset(w/2, h/2), Size(w,h*0.3f),
                        androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()))
                    repeat(5) { i ->
                        drawCircle(Color.White.copy(alpha = 0.8f), (3+i%3).dp.toPx(),
                            position + Offset((i*14-28).dp.toPx(), (-30-i%2*8).dp.toPx()),
                            style = Stroke(1.dp.toPx()))
                    }
                }
                Text(if (cleaned.size == dirt.size) context.getString(R.string.cleaning_done)
                    else context.getString(R.string.cleaning_percent, cleaned.size * 100 / dirt.size))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
