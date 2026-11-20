package com.example.calendar.presentation.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedBee(modifier: Modifier = Modifier) {
    val animation = rememberInfiniteTransition(label = "Пчёлка Bee2")
    val flight by animation.animateFloat(-5f, 5f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "Полёт")
    val wings by animation.animateFloat(0.6f, 1f,
        infiniteRepeatable(tween(90), RepeatMode.Reverse), label = "Крылья")
    Canvas(modifier.size(56.dp).semantics { contentDescription = "Летающая пчёлка Bee2" }) {
        val u = size.width / 56f
        translate(0f, flight * u) {
            drawOval(Color(0xFFB9E9F2), Offset(13*u, (10-4*wings)*u), Size(16*u, 17*wings*u))
            drawOval(Color(0xFFDDF6FA), Offset(27*u, (8-4*wings)*u), Size(16*u, 18*wings*u))
            drawOval(Color(0xFFFFD65B), Offset(10*u, 22*u), Size(35*u, 23*u))
            drawLine(Color(0xFF493329), Offset(20*u, 24*u), Offset(20*u, 43*u), 4*u)
            drawLine(Color(0xFF493329), Offset(28*u, 23*u), Offset(28*u, 44*u), 4*u)
            drawCircle(Color(0xFF493329), 2*u, Offset(39*u, 30*u))
            drawLine(Color(0xFF493329), Offset(39*u, 24*u), Offset(43*u, 17*u), 2*u)
            drawCircle(Color(0xFFF697AE), 3*u, Offset(40*u, 36*u))
        }
    }
}
