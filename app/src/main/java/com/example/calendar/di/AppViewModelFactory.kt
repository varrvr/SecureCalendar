package com.example.calendar.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.calendar.presentation.auth.LocalAuthViewModel
import com.example.calendar.presentation.calendar.CalendarViewModel

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(CalendarViewModel::class.java) -> CalendarViewModel(
            container.eventRepository,
            container.sessionRepository
        ) as T
        modelClass.isAssignableFrom(LocalAuthViewModel::class.java) -> LocalAuthViewModel(
            container.localAuthRepository
        ) as T
        else -> error("Unknown ViewModel: ${modelClass.name}")
    }
}
