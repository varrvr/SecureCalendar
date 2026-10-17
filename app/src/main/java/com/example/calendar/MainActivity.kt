
package com.example.calendar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.calendar.presentation.navigation.SecureCalendarApp
import com.example.calendar.ui.theme.SecureCalendarTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            SecureCalendarTheme {
                SecureCalendarApp()
            }
        }
    }
}
