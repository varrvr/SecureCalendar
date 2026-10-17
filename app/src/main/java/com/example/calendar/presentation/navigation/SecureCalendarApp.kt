
package com.example.calendar.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calendar.domain.model.TimeInterval
import com.example.calendar.presentation.auth.*
import com.example.calendar.presentation.calendar.CalendarScreen
import com.example.calendar.presentation.calendar.CalendarViewModel
import com.example.calendar.presentation.comparison.*
import com.example.calendar.presentation.editor.EventEditorScreen
import com.example.calendar.presentation.profile.*
import java.time.LocalDate
import java.time.ZoneId


enum class AppPage {
    LOGIN,
    REGISTER,
    CALENDAR,
    COMPARISON,
    PROFILE
}

@Composable
fun SecureCalendarApp(
    calendarViewModel: CalendarViewModel = viewModel()
) {
    var page by remember {
        mutableStateOf(AppPage.LOGIN)
    }

    var loginState by remember {
        mutableStateOf(LoginUiState())
    }

    var registerState by remember {
        mutableStateOf(RegisterUiState())
    }

    var profileState by remember {
        mutableStateOf(ProfileUiState())
    }

    var comparison by remember {
        mutableStateOf(
            ComparisonUiState(
                users = listOf("Алиса", "Боб", "Мария")
            )
        )
    }

    var showQrInfo by remember {
        mutableStateOf(false)
    }

    val events by calendarViewModel.events.collectAsState()
    val editor = calendarViewModel.editorState

    val showTabs = editor == null &&
            page in listOf(
        AppPage.CALENDAR,
        AppPage.COMPARISON,
        AppPage.PROFILE
    )

    Scaffold(
        bottomBar = {
            if (showTabs) {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == AppPage.CALENDAR,
                        onClick = { page = AppPage.CALENDAR },
                        icon = {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null
                            )
                        },
                        label = { Text("Календарь") }
                    )

                    NavigationBarItem(
                        selected = page == AppPage.COMPARISON,
                        onClick = { page = AppPage.COMPARISON },
                        icon = {
                            Icon(
                                Icons.Default.CompareArrows,
                                contentDescription = null
                            )
                        },
                        label = { Text("Сравнение") }
                    )

                    NavigationBarItem(
                        selected = page == AppPage.PROFILE,
                        onClick = { page = AppPage.PROFILE },
                        icon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null
                            )
                        },
                        label = { Text("Профиль") }
                    )
                }
            }
        }
    ) { insets ->
        val modifier = Modifier.padding(insets)


        val editor = calendarViewModel.editorState

        if (editor != null) {
            EventEditorScreen(
                uiState = editor,
                onTitleChange = calendarViewModel::changeTitle,
                onStartDateChange = calendarViewModel::changeStartDate,
                onStartTimeChange = calendarViewModel::changeStartTime,
                onEndDateChange = calendarViewModel::changeEndDate,
                onEndTimeChange = calendarViewModel::changeEndTime,
                onPublicChange = calendarViewModel::changePublic,
                onSaveClick = calendarViewModel::saveEditor,
                onDeleteClick = calendarViewModel::deleteEditor,
                onBackClick = calendarViewModel::closeEditor
            )
    } else {
            when (page) {
                AppPage.LOGIN -> {
                    LoginScreen(
                        state = loginState,
                        onLoginChange = {
                            loginState = loginState.copy(
                                login = it,
                                loginError = null,
                                error = null
                            )
                        },
                        onPasswordChange = {
                            loginState = loginState.copy(
                                password = it,
                                passwordError = null,
                                error = null
                            )
                        },
                        onLoginClick = {
                            val loginError =
                                if (loginState.login.isBlank()) {
                                    "Введите логин"
                                } else null

                            val passwordError =
                                if (loginState.password.isBlank()) {
                                    "Введите пароль"
                                } else null

                            loginState = loginState.copy(
                                loginError = loginError,
                                passwordError = passwordError
                            )

                            if (
                                loginError == null &&
                                passwordError == null
                            ) {
                                profileState = profileState.copy(
                                    login = loginState.login
                                )

                                page = AppPage.CALENDAR
                            }
                        },
                        onRegisterClick = {
                            page = AppPage.REGISTER
                        },
                        modifier = modifier
                    )
                }

                AppPage.REGISTER -> {
                    RegisterScreen(
                        state = registerState,
                        onUsernameChange = {
                            registerState = registerState.copy(
                                username = it,
                                usernameError = null
                            )
                        },
                        onLoginChange = {
                            registerState = registerState.copy(
                                login = it,
                                loginError = null
                            )
                        },
                        onPasswordChange = {
                            registerState = registerState.copy(
                                password = it,
                                passwordError = null
                            )
                        },
                        onConfirmPasswordChange = {
                            registerState = registerState.copy(
                                confirmPassword = it,
                                confirmPasswordError = null
                            )
                        },
                        onRegisterClick = {
                            val nameError =
                                if (registerState.username.isBlank()) {
                                    "Введите имя"
                                } else null

                            val loginError =
                                if (registerState.login.isBlank()) {
                                    "Введите логин"
                                } else null

                            val passwordError =
                                if (registerState.password.isBlank()) {
                                    "Введите пароль"
                                } else null

                            val confirmError = when {
                                registerState.confirmPassword.isBlank() ->
                                    "Подтвердите пароль"

                                registerState.password !=
                                        registerState.confirmPassword ->
                                    "Пароли не совпадают"

                                else -> null
                            }

                            registerState = registerState.copy(
                                usernameError = nameError,
                                loginError = loginError,
                                passwordError = passwordError,
                                confirmPasswordError = confirmError
                            )

                            if (
                                listOf(
                                    nameError,
                                    loginError,
                                    passwordError,
                                    confirmError
                                ).all { it == null }
                            ) {
                                profileState = profileState.copy(
                                    username = registerState.username,
                                    login = registerState.login
                                )

                                page = AppPage.CALENDAR
                            }
                        },
                        onLoginClick = {
                            page = AppPage.LOGIN
                        },
                        modifier = modifier
                    )
                }

                AppPage.CALENDAR -> {
                    CalendarScreen(
                        displayedMonth = calendarViewModel.displayedMonth,
                        selectedDate = calendarViewModel.selectedDate,
                        events = events,
                        onDateSelected = calendarViewModel::selectDate,
                        onPreviousMonth = calendarViewModel::previousMonth,
                        onNextMonth = calendarViewModel::nextMonth,
                        onTodayClick = calendarViewModel::goToday,
                        onEventClick = calendarViewModel::openEditEditor,
                        onAddEventClick = calendarViewModel::openCreateEditor,
                        modifier = modifier
                    )
                }

                AppPage.COMPARISON -> {
                    ComparisonScreen(
                        state = comparison,
                        onUserSelected = {
                            comparison = comparison.copy(
                                selectedUser = it,
                                status = ComparisonStatus.INITIAL
                            )
                        },
                        onPeriodStartChanged = {
                            comparison = comparison.copy(
                                periodStart = it
                            )
                        },
                        onPeriodEndChanged = {
                            comparison = comparison.copy(
                                periodEnd = it
                            )
                        },
                        onRequestClick = {
                            comparison = comparison.copy(
                                status = ComparisonStatus.WAITING
                            )
                        },
                        onRetryClick = {
                            comparison = comparison.copy(
                                status = ComparisonStatus.INITIAL
                            )
                        },
                        onDemoStatusSelected = { status ->
                            val zone = ZoneId.systemDefault()
                            val today = LocalDate.now()

                            val intervals = listOf(
                                TimeInterval(
                                    today.atTime(10, 30)
                                        .atZone(zone)
                                        .toInstant(),
                                    today.atTime(11, 0)
                                        .atZone(zone)
                                        .toInstant()
                                ),
                                TimeInterval(
                                    today.plusDays(1)
                                        .atTime(15, 30)
                                        .atZone(zone)
                                        .toInstant(),
                                    today.plusDays(1)
                                        .atTime(16, 0)
                                        .atZone(zone)
                                        .toInstant()
                                )
                            )

                            comparison = comparison.copy(
                                status = status,
                                intersections =
                                    if (status == ComparisonStatus.SUCCESS) {
                                        intervals
                                    } else {
                                        emptyList()
                                    },
                                error =
                                    if (status == ComparisonStatus.ERROR) {
                                        "Не удалось выполнить сравнение"
                                    } else null
                            )
                        },
                        modifier = modifier
                    )
                }

                AppPage.PROFILE -> {
                    ProfileScreen(
                        state = profileState,
                        onShowQrClick = {
                            showQrInfo = true
                        },
                        onLogoutClick = {
                            loginState = LoginUiState()
                            registerState = RegisterUiState()
                            page = AppPage.LOGIN
                        },
                        modifier = modifier
                    )
                }
            }
        }
    }

    if (showQrInfo) {
        AlertDialog(
            onDismissRequest = {
                showQrInfo = false
            },
            title = {
                Text("QR-код публичного ключа")
            },
            text = {
                Text(
                    "Здесь будет отображаться QR-код, " +
                            "когда другой разработчик подключит работу с ключами."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showQrInfo = false
                    }
                ) {
                    Text("Понятно")
                }
            }
        )
    }
}
