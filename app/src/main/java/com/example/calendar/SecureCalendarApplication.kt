package com.example.calendar

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.calendar.BuildConfig
import com.example.calendar.di.AppContainer

class SecureCalendarApplication : Application() {
    val container: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        Log.i(
            "SecureCalendar.Startup",
            "Application started; version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}); " +
                "debug=${BuildConfig.DEBUG}; supportedAbis=${Build.SUPPORTED_ABIS.joinToString()}; " +
                "server=${BuildConfig.SERVER_BASE_URL}"
        )
    }
}
