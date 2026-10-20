package com.example.calendar

import android.app.Application
import com.example.calendar.di.AppContainer

class SecureCalendarApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }
}
