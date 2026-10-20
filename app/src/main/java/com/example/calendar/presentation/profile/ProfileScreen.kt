
package com.example.calendar.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ProfileUiState(
    val username: String = "",
    val login: String = "",
    val isConnected: Boolean = false,
    val serverRegistrationStatus: String = "NOT_REGISTERED",
    val serverAuthenticated: Boolean = false,
    val serverConfigured: Boolean = false,
    val publicKeyFingerprint: String? = null
)

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onShowQrClick: () -> Unit,
    onConnectClick: () -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                Text("Публичный ключ: ${state.publicKeyFingerprint ?: "Криптографический ключ ещё не создан"}")
            }
        }

        if (!state.serverConfigured) {
            Text(
                "Адрес сервера и согласованные параметры OPAQUE не настроены.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = onConnectClick,
            enabled = state.serverConfigured,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (state.isConnected) "Подключено к серверу" else if (state.serverConfigured) "Подключиться к серверу" else "Сервер не настроен")
        }

        OutlinedButton(
            onClick = onShowQrClick,
            enabled = state.publicKeyFingerprint != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Показать QR-код ключа") }

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выйти из аккаунта")
        }
    }
}
