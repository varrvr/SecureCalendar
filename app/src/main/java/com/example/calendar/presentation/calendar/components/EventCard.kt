
package com.example.calendar.presentation.calendar.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.util.formatTime

@Composable
fun EventCard(
    event: CalendarEvent,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "${formatTime(event.start)}–" +
                        formatTime(event.end),
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
