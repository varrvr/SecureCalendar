package com.example.calendar

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalRegistrationUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun userCanRegisterAndReachCalendar() {
        val username = "ui${System.currentTimeMillis()}"
        compose.onNodeWithText("Создать аккаунт").performClick()
        compose.onNodeWithText("Имя пользователя").performTextInput("UI Test User")
        compose.onNodeWithText("Логин").performTextInput(username)
        compose.onNodeWithText("Пароль").performTextInput("SecurePass123!")
        compose.onNodeWithText("Подтверждение пароля").performTextInput("SecurePass123!")
        compose.onNodeWithText("Зарегистрироваться").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Мой календарь").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Мой календарь").assertIsDisplayed()
    }
}
