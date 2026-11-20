package com.example.calendar

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream

/** Tests grant the same runtime LAN permission that the production UI requests. */
internal fun grantLocalNetworkForTests() {
    if (Build.VERSION.SDK_INT >= 37) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${instrumentation.targetContext.packageName} android.permission.ACCESS_LOCAL_NETWORK"
        ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
    }
}
