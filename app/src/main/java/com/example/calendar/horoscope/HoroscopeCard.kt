package com.example.calendar.horoscope

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** A quiet, expandable part of the selected day, rather than a separate feed card. */
@Composable
fun HoroscopeCard(eventId: String, modifier: Modifier = Modifier, title: String = "Гороскоп на день") {
    val context = LocalContext.current
    val generator = remember(context.applicationContext) {
        HoroscopeGenerator(SecureCalendarCorpus.build(), HoroscopeSalt.get(context))
    }
    var nonce by rememberSaveable(eventId) { mutableLongStateOf(0L) }
    var expanded by rememberSaveable(eventId) { mutableStateOf(false) }
    val horoscope = remember(generator, eventId, nonce) { generator.forMeeting(eventId, nonce) }
    Column(modifier.fillMaxWidth().animateContentSize()) {
        Surface(onClick = { expanded = !expanded }, color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().semantics { stateDescription = if (expanded) "Развёрнут" else "Свёрнут" }) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.labelLarge)
                    if (!expanded) Text(horoscope.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(horoscope.text, style = MaterialTheme.typography.bodyMedium)
                Text("Немного звёздного настроения, просто для улыбки.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { nonce++ }) {
                    Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Другое предсказание")
                }
            }
        }
    }
}
