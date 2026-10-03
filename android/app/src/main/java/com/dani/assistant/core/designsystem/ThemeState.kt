package com.dani.assistant.core.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** وضع الثيم الحالي: 0 = داكن (الافتراضي)، 1 = فاتح، 2 = حسب النظام. */
object ThemeState {
    var mode by mutableStateOf(0)
}
