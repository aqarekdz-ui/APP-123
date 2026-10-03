package com.dani.assistant

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.fragment.app.FragmentActivity
import com.dani.assistant.core.designsystem.DaniTheme
import com.dani.assistant.core.security.AppLock
import com.dani.assistant.core.security.LockScreen
import com.dani.assistant.presentation.main.MainScreen

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lastStop = 0L

    companion object {
        /** يبقى true أثناء حياة العملية (تدوير الشاشة ما يقفلش). موت العملية = قفل من جديد. */
        private var sessionUnlocked = false
        /** القفل يرجع بعد غياب أطول من هذي المدة (ملفات النسخ الاحتياطي تفتح تطبيقات خارجية). */
        private const val RELOCK_AFTER_MS = 30_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        locked = AppLock.shouldLock(this) && !sessionUnlocked

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            // Requirement: Wrap content with RTL LayoutDirection and DaniTheme
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DaniTheme {
                    if (locked) {
                        LockScreen(activity = this@MainActivity) {
                            sessionUnlocked = true
                            locked = false
                        }
                    } else {
                        MainScreen()
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (lastStop != 0L &&
            SystemClock.elapsedRealtime() - lastStop > RELOCK_AFTER_MS &&
            AppLock.shouldLock(this)
        ) {
            sessionUnlocked = false
            locked = true
        }
    }
}
