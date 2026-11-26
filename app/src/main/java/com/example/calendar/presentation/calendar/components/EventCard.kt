package com.example.calendar.presentation.calendar.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calendar.domain.model.CalendarEvent
import com.example.calendar.util.ruLocale
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun EventCard(event: CalendarEvent, onClick: () -> Unit) {
    val start = event.start.atZone(ZoneId.systemDefault())
    val end = event.end.atZone(ZoneId.systemDefault())
    val time = DateTimeFormatter.ofPattern("HH:mm", ruLocale)
    val dateTime = DateTimeFormatter.ofPattern("d MMM, HH:mm", ruLocale)
    val interval = if (start.toLocalDate() == end.toLocalDate())
        "${start.format(time)} – ${end.format(time)}"
        else "${start.format(dateTime)} – ${end.format(dateTime)}"
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(interval, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(event.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (event.isPublic) Icons.Outlined.PeopleOutline else Icons.Outlined.Lock, null,
                        modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (event.isPublic) "Учитывается при сравнении" else "Только для вас",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
