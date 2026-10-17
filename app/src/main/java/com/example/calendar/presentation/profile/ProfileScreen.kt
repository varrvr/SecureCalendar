
package com.example.calendar.presentation.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ProfileUiState(
    val username: String = "Тестовый пользователь",
    val login: String = "demo",
    val isConnected: Boolean = false,
    val publicKeyFingerprint: String =
        "Тестовые данные: ключ ещё не создан"
)

@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onShowQrClick: () -> Unit,
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

                Text(
                    "Отпечаток публичного ключа",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(state.publicKeyFingerprint)
            }
        }

        Button(
            onClick = onShowQrClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Показать QR-код ключа")
        }

        OutlinedButton(
            onClick = onLogoutClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Выйти из аккаунта")
        }
    }
}
