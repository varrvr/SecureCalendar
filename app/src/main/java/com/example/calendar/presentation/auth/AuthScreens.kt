
package com.example.calendar.presentation.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp


data class LoginUiState(
    val login: String = "",
    val password: String = "",
    val loginError: String? = null,
    val passwordError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)

data class RegisterUiState(
    val username: String = "",
    val login: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val usernameError: String? = null,
    val loginError: String? = null,
    val passwordError: String? = null,
    val confirmPasswordError: String? = null,
    val error: String? = null,
    val isLoading: Boolean = false
)

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    enabled: Boolean
) {
    var visible by remember {
        mutableStateOf(false)
    }

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        visualTransformation = if (visible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            IconButton(
                onClick = { visible = !visible }
            ) {
                Icon(
                    imageVector = if (visible) {
                        Icons.Default.VisibilityOff
                    } else {
                        Icons.Default.Visibility
                    },
                    contentDescription = if (visible) {
                        "Скрыть пароль"
                    } else {
                        "Показать пароль"
                    }
                )
            }
        },
        isError = error != null,
        supportingText = error?.let {
            { Text(it) }
        }
    )
}

@Composable
fun LoginScreen(
    state: LoginUiState,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Вход",
            style = MaterialTheme.typography.headlineLarge
        )

        OutlinedTextField(
            value = state.login,
            onValueChange = onLoginChange,
            label = { Text("Логин") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.loginError != null,
            supportingText = state.loginError?.let { error ->
                { Text(error) }
            }
        )

        PasswordField(
            value = state.password,
            onChange = onPasswordChange,
            label = "Пароль",
            error = state.passwordError,
            enabled = !state.isLoading
        )

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onLoginClick,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Войти")
            }
        }

        TextButton(
            onClick = onRegisterClick,
            enabled = !state.isLoading
        ) {
            Text("Создать аккаунт")
        }
    }
}

@Composable
fun RegisterScreen(
    state: RegisterUiState,
    onUsernameChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Регистрация",
            style = MaterialTheme.typography.headlineLarge
        )

        OutlinedTextField(
            value = state.username,
            onValueChange = onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Имя пользователя") },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.usernameError != null,
            supportingText = state.usernameError?.let { error ->
                { Text(error) }
            }
        )

        OutlinedTextField(
            value = state.login,
            onValueChange = onLoginChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Логин") },
            singleLine = true,
            enabled = !state.isLoading,
            isError = state.loginError != null,
            supportingText = state.loginError?.let { error ->
                { Text(error) }
            }
        )

        PasswordField(
            value = state.password,
            onChange = onPasswordChange,
            label = "Пароль",
            error = state.passwordError,
            enabled = !state.isLoading
        )

        PasswordField(
            value = state.confirmPassword,
            onChange = onConfirmPasswordChange,
            label = "Подтверждение пароля",
            error = state.confirmPasswordError,
            enabled = !state.isLoading
        )

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            onClick = onRegisterClick,
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Зарегистрироваться")
            }
        }

        TextButton(
            onClick = onLoginClick,
            enabled = !state.isLoading
        ) {
            Text("Уже есть аккаунт? Войти")
        }
    }
}
