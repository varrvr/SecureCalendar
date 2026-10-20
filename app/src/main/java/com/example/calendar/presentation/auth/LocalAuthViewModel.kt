package com.example.calendar.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calendar.domain.model.LocalAccount
import com.example.calendar.domain.model.LocalAuthResult
import com.example.calendar.domain.repository.LocalAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import android.database.sqlite.SQLiteException
import java.security.GeneralSecurityException

data class AuthOperationState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val account: LocalAccount? = null
)

class LocalAuthViewModel(private val repository: LocalAuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthOperationState())
    val state = _state.asStateFlow()

    fun login(username: String, password: String) = runAuth(password) {
        repository.login(username, it)
    }

    fun register(displayName: String, username: String, password: String) = runAuth(password) {
        repository.register(displayName, username, it)
    }

    private fun runAuth(password: String, operation: suspend (CharArray) -> LocalAuthResult) {
        if (_state.value.isLoading) return
        _state.value = AuthOperationState(isLoading = true)
        viewModelScope.launch {
            val passwordChars = password.toCharArray()
            try {
                when (val result = operation(passwordChars)) {
                    is LocalAuthResult.Success -> _state.value = AuthOperationState(account = result.account)
                    LocalAuthResult.InvalidCredentials -> _state.value = AuthOperationState(error = "Неверный логин или пароль")
                    LocalAuthResult.UsernameTaken -> _state.value = AuthOperationState(error = "Этот логин уже занят")
                    is LocalAuthResult.Failure -> _state.value = AuthOperationState(error = result.message)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val message = when (error) {
                    is SQLiteException -> "Не удалось сохранить аккаунт в локальной базе данных. (${error.javaClass.simpleName})"
                    is GeneralSecurityException -> "На устройстве недоступна проверка пароля. (${error.javaClass.simpleName})"
                    else -> "Не удалось выполнить операцию. (${error.javaClass.simpleName})"
                }
                _state.value = AuthOperationState(error = message)
            } finally {
                passwordChars.fill('\u0000')
            }
        }
    }

    fun consumeResult() { _state.value = AuthOperationState() }
}
