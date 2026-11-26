package com.example.calendar.presentation.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

val russianTextKeyboard = KeyboardOptions(
    hintLocales = LocaleList("ru-RU,en-US"),
    capitalization = KeyboardCapitalization.Sentences,
    keyboardType = KeyboardType.Text
)
val russianLoginKeyboard = KeyboardOptions(
    hintLocales = LocaleList("ru-RU,en-US"),
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Text
)
val russianPasswordKeyboard = KeyboardOptions(
    hintLocales = LocaleList("ru-RU,en-US"),
    autoCorrectEnabled = false,
    keyboardType = KeyboardType.Password
)

fun openKeyboardLanguageSettings(context: Context) {
    val keyboard = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
    val intent = Intent(Settings.ACTION_INPUT_METHOD_SUBTYPE_SETTINGS).apply {
        if (keyboard != null) putExtra(Settings.EXTRA_INPUT_METHOD_ID, keyboard)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try { context.startActivity(intent) }
    catch (_: ActivityNotFoundException) {
        try { context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Откройте настройки Android → Клавиатура → Языки → Русский", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun KeyboardLanguageSettingsButton(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextButton(onClick = { openKeyboardLanguageSettings(context) }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Keyboard, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Языки клавиатуры · RU / EN")
    }
}
