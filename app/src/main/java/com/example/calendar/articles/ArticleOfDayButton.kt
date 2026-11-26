package com.example.calendar.articles

import android.content.ActivityNotFoundException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.time.LocalDate

@Composable
fun ArticleOfDayButton(date: LocalDate, repository: DailyArticleRepository, modifier: Modifier = Modifier) {
    key(date) {
        val uriHandler = LocalUriHandler.current
        val context = LocalContext.current
        val translator = remember(context.applicationContext) { RussianArticleTranslator(context.applicationContext) }
        var expanded by rememberSaveable { mutableStateOf(false) }
        var loading by remember { mutableStateOf(false) }
        var article by remember { mutableStateOf<DailyArticle?>(null) }
        var teaser by remember { mutableStateOf<RussianArticleTeaser?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var retry by remember { mutableIntStateOf(0) }
        LaunchedEffect(expanded, retry) {
            if (!expanded || teaser != null) return@LaunchedEffect
            loading = true
            error = null
            try {
                val selected = article ?: repository.articleFor(date)
                article = selected
                teaser = translator.preview(selected)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: IOException) {
                error = if (article == null) "Не удалось загрузить статью. Проверьте интернет и попробуйте ещё раз."
                    else "Русский перевод пока недоступен. Подключитесь к Wi-Fi и попробуйте ещё раз."
            }
            finally { loading = false }
        }
        Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.Article, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Статья дня", style = MaterialTheme.typography.labelLarge)
                        Text(if (loading) "Готовим короткую версию на русском…" else teaser?.headline ?: "Криптография · коротко и по-русски",
                            maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    article?.let { selected ->
                        Text(teaser?.headline ?: russianArticleHeadline(selected.title), style = MaterialTheme.typography.titleSmall)
                        teaser?.let {
                            Text(it.text, style = MaterialTheme.typography.bodyMedium)
                            Text("Автоперевод · Google Translate", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilledTonalButton(onClick = {
                            try { uriHandler.openUri(selected.url) }
                            catch (_: ActivityNotFoundException) { error = "Не удалось открыть браузер." }
                            catch (_: IllegalArgumentException) { error = "Не удалось открыть ссылку на статью." }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("Читать на ePrint")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (loading && teaser == null) Text("Первый перевод может занять немного больше времени.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        if (teaser == null) TextButton(onClick = { retry++ }) { Text("Попробовать снова") }
                    }
                }
            }
        }
    }
}
