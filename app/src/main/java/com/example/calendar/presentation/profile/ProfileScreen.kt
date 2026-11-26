
package com.example.calendar.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.calendar.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.util.Log
import com.example.calendar.presentation.components.russianPasswordKeyboard
import com.example.calendar.presentation.components.KeyboardLanguageSettingsButton

data class ProfileUiState(
    val username: String = "",
    val login: String = "",
    val isConnected: Boolean = false,
    val serverBaseUrl: String = "",
    val serverRegistrationStatus: String = "NOT_REGISTERED",
    val serverAuthenticated: Boolean = false,
    val serverConfigured: Boolean = false,
    val publicKeyFingerprint: String? = null,
    val isCreatingKey: Boolean = false,
    val keyError: String? = null,
    val isServerAuthLoading: Boolean = false,
    val serverAuthError: String? = null
)

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onShowQrClick: () -> Unit,
    onConnectClick: (String) -> Unit,
    onCreateKeyClick: () -> Unit,
    onServerAddressSave: (String) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val logTag = "SecureCalendar.ProfileUi"
    var showPasswordDialog by remember { mutableStateOf(false) }
    var serverPassword by remember { mutableStateOf("") }
    var editedServerUrl by remember(state.serverBaseUrl) { mutableStateOf(state.serverBaseUrl) }
    var awaitingServerAuth by remember { mutableStateOf(false) }
    var showAddress by rememberSaveable { mutableStateOf(false) }
    var showSecurity by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.serverAuthenticated, state.isServerAuthLoading, state.serverAuthError, awaitingServerAuth) {
        if (awaitingServerAuth && state.serverAuthenticated && !state.isServerAuthLoading) {
            Log.i(logTag, "Server authentication succeeded; closing password dialog")
            serverPassword = ""
            showPasswordDialog = false
            awaitingServerAuth = false
        } else if (awaitingServerAuth && state.serverAuthError != null) {
            awaitingServerAuth = false
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = context.getString(R.string.profile_title),
            style = MaterialTheme.typography.headlineMedium
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.username, style = MaterialTheme.typography.titleLarge)
                Text("@${state.login}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Подключение", style = MaterialTheme.typography.titleMedium)
                Text(if (state.isConnected) "Сервер подключён" else "Сервер не подключён",
                    color = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Календарь доступен без подключения. Сервер нужен для сравнения встреч.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    Log.i(logTag, "Connect-to-server button clicked; configured=${state.serverConfigured}")
                    showPasswordDialog = true
                }, enabled = state.serverConfigured && !state.isServerAuthLoading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (state.isServerAuthLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Подключаемся…")
                    } else Text(if (state.isConnected) context.getString(R.string.profile_refresh_session)
                        else context.getString(R.string.profile_connect))
                }
                state.serverAuthError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { showAddress = !showAddress }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showAddress) "Скрыть настройки сервера" else "Настроить адрес сервера")
                }
                AnimatedVisibility(showAddress) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = editedServerUrl, onValueChange = { editedServerUrl = it },
                            label = { Text(context.getString(R.string.profile_server_url)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            enabled = !state.isServerAuthLoading, modifier = Modifier.fillMaxWidth())
                        Text(context.getString(R.string.profile_address_help), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { onServerAddressSave(editedServerUrl) },
                            enabled = editedServerUrl.isNotBlank() && editedServerUrl != state.serverBaseUrl && !state.isServerAuthLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(context.getString(R.string.profile_save_address))
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Защита данных", style = MaterialTheme.typography.titleMedium)
                Text(if (state.publicKeyFingerprint == null) "Создайте ключ для защищённого сравнения встреч."
                    else "Ключ для защищённого сравнения создан.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.publicKeyFingerprint == null) {
                    FilledTonalButton(onClick = onCreateKeyClick, enabled = !state.isCreatingKey,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        if (state.isCreatingKey) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(context.getString(if (state.isCreatingKey) R.string.profile_creating_key else R.string.profile_create_key))
                    }
                }
                state.keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { showSecurity = !showSecurity }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (showSecurity) "Скрыть сведения" else "Сведения о защите")
                }
                AnimatedVisibility(showSecurity) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("OPAQUE: ${if (state.serverRegistrationStatus == "REGISTERED") context.getString(R.string.profile_registered) else context.getString(R.string.profile_not_registered)}",
                            style = MaterialTheme.typography.bodySmall)
                        Text(context.getString(R.string.profile_auth_state, context.getString(if (state.serverAuthenticated)
                            R.string.profile_authenticated else R.string.profile_not_authenticated)), style = MaterialTheme.typography.bodySmall)
                        Text(context.getString(R.string.profile_fingerprint, state.publicKeyFingerprint ?: context.getString(R.string.profile_key_missing)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        KeyboardLanguageSettingsButton(Modifier.fillMaxWidth())
        TextButton(onClick = onLogoutClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(context.getString(R.string.profile_logout))
        }
    }

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!state.isServerAuthLoading) {
                    showPasswordDialog = false
                    serverPassword = ""
                }
            },
            title = { Text(context.getString(R.string.profile_connect_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(context.getString(R.string.profile_password_help))
                    state.serverAuthError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = serverPassword,
                        onValueChange = { serverPassword = it },
                        label = { Text(context.getString(R.string.profile_password)) },
                        singleLine = true,
                        enabled = !state.isServerAuthLoading,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = russianPasswordKeyboard,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = serverPassword.isNotEmpty() && !state.isServerAuthLoading,
                    onClick = {
                        val password = serverPassword
                        serverPassword = ""
                        awaitingServerAuth = true
                        Log.i(logTag, "Server authentication submitted; password omitted")
                        onConnectClick(password)
                    }
                ) { Text(context.getString(R.string.profile_continue)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isServerAuthLoading,
                    onClick = {
                        serverPassword = ""
                        showPasswordDialog = false
                    }
                ) { Text(context.getString(R.string.profile_cancel)) }
            }
        )
    }
}
