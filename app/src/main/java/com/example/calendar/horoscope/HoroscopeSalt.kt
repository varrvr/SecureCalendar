package com.example.calendar.horoscope

import android.content.Context
import android.util.Base64
import java.security.SecureRandom

object HoroscopeSalt {

    private const val PREFS = "horoscope_prefs"
    private const val KEY = "salt"
    private const val SIZE = 32

    fun get(context: Context): ByteArray {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        prefs.getString(KEY, null)?.let { stored ->
            return Base64.decode(stored, Base64.NO_WRAP)
        }

        val fresh = ByteArray(SIZE).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY, Base64.encodeToString(fresh, Base64.NO_WRAP))
            .apply()
        return fresh
    }
}