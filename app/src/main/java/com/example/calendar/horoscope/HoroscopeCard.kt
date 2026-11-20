package com.example.calendar.horoscope

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun HoroscopeCard(
    eventId: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // Генератор создаётся один раз на карточку. Корпус парсится в память.
    val generator = remember {
        HoroscopeGenerator(
            corpus = SecureCalendarCorpus.build(),
            salt = HoroscopeSalt.get(context),
        )
    }

    var nonce by remember(eventId) { mutableLongStateOf(0L) }

    val horoscope = remember(eventId, nonce) {
        generator.forMeeting(eventId, nonce)
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎲", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Гороскоп встречи",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                horoscope.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Сгенерировано локально. Не является прогнозом.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { nonce = System.currentTimeMillis() }) {
                Text("🎲 Другой")
            }
        }
    }
}