
package com.example.calendar.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.util.Log

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
    val logTag = "SecureCalendar.ProfileUi"
    var showPasswordDialog by remember { mutableStateOf(false) }
    var serverPassword by remember { mutableStateOf("") }
    var editedServerUrl by remember(state.serverBaseUrl) { mutableStateOf(state.serverBaseUrl) }
    var awaitingServerAuth by remember { mutableStateOf(false) }
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
            text = "Профиль",
            style = MaterialTheme.typography.headlineMedium
        )

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Адрес Python-сервера", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = editedServerUrl,
                    onValueChange = { editedServerUrl = it },
                    label = { Text("http://192.168.1.10:8000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "На планшете укажите IP компьютера в той же Wi-Fi сети. Адрес 10.0.2.2 работает только в Android-эмуляторе. Сервер должен слушать 0.0.0.0:8000.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = {
                        Log.i(logTag, "Save server endpoint clicked; value=$editedServerUrl")
                        onServerAddressSave(editedServerUrl)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Сохранить адрес") }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Имя пользователя",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    state.username,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    "Логин",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(state.login)

                Text(
                    "Подключение: " + if (state.isConnected) {
                        "В сети"
                    } else {
                        "Нет подключения"
                    }
                )

                HorizontalDivider()

                Text("OPAQUE: ${if (state.serverRegistrationStatus == "REGISTERED") "зарегистрирован" else "не зарегистрирован"}")
                Text("Серверная авторизация: ${if (state.serverAuthenticated) "выполнена" else "не выполнена"}")
                Text("Отпечаток публичного ключа: ${state.publicKeyFingerprint ?: "Криптографический ключ ещё не создан"}")
            }
        }

        if (!state.serverConfigured) {
            Text(
                "Адрес сервера и согласованные параметры OPAQUE не настроены.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.publicKeyFingerprint == null) {
            Button(
                onClick = onCreateKeyClick,
                enabled = !state.isCreatingKey,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isCreatingKey) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (state.isCreatingKey) "Создаём ключ…" else "Создать ключ Bee2")
            }
        }
        state.keyError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = {
                Log.i(logTag, "Connect-to-server button clicked; configured=${state.serverConfigured}")
                showPasswordDialog = true
            },
            enabled = state.serverConfigured && !state.isServerAuthLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isServerAuthLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (state.isConnected) "Обновить серверную сессию" else if (state.serverConfigured) "Подключиться к серверу" else "Сервер не настроен")
        }
        state.serverAuthError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        OutlinedButton(
            onClick = onShowQrClick,
            enabled = false,
            modifier = Modifier.fillMaxWidth()
        ) { Text("QR-код ключа пока недоступен") }

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выйти из аккаунта")
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
            title = { Text("Подключение к Secure Calendar") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пароль используется только для OPAQUE и не сохраняется.")
                    state.serverAuthError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = serverPassword,
                        onValueChange = { serverPassword = it },
                        label = { Text("Пароль аккаунта") },
                        singleLine = true,
                        enabled = !state.isServerAuthLoading,
                        visualTransformation = PasswordVisualTransformation()
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
                ) { Text("Продолжить") }
            },
            dismissButton = {
                TextButton(
                    enabled = !state.isServerAuthLoading,
                    onClick = {
                        serverPassword = ""
                        showPasswordDialog = false
                    }
                ) { Text("Отмена") }
            }
        )
    }
}
