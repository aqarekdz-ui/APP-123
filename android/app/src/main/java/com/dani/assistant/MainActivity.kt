package com.dani.assistant

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
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
import com.dani.assistant.core.designsystem.ThemeState
import com.dani.assistant.core.settings.AppSettings
import androidx.compose.foundation.isSystemInDarkTheme
import com.dani.assistant.core.security.AppLock
import com.dani.assistant.core.security.LockScreen
import com.dani.assistant.presentation.main.MainScreen
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private var locked by mutableStateOf(false)
    private var lastStop = 0L
    private var startRoute by mutableStateOf<String?>(null)

    companion object {
        /** يبقى true أثناء حياة العملية (تدوير الشاشة ما يقفلش). موت العملية = قفل من جديد. */
        private var sessionUnlocked = false
        /** القفل يرجع بعد غياب أطول من هذي المدة (ملفات النسخ الاحتياطي تفتح تطبيقات خارجية). */
        private const val RELOCK_AFTER_MS = 30_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShare(intent)
        readRoute(intent)

        locked = AppLock.shouldLock(this) && !sessionUnlocked

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        ThemeState.mode = AppSettings.themeMode(this)

        setContent {
            var crash by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(com.dani.assistant.core.debug.CrashLog.read(this@MainActivity)) }
            val crashNow = crash
            if (crashNow != null) {
                com.dani.assistant.core.debug.CrashScreen(crashNow) {
                    com.dani.assistant.core.debug.CrashLog.clear(this@MainActivity)
                    crash = null
                }
            } else {
            // Requirement: Wrap content with RTL LayoutDirection and DaniTheme
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DaniTheme(darkTheme = when (ThemeState.mode) {
                    1 -> false
                    2 -> isSystemInDarkTheme()
                    else -> true
                }) {
                    if (locked) {
                        LockScreen(activity = this@MainActivity) {
                            sessionUnlocked = true
                            locked = false
                        }
                    } else {
                        MainScreen(startRoute = startRoute, onRouteHandled = { startRoute = null })
                    }
                }
            }
        }
        }
    }

    private val shortcutRoutes = setOf("notes", "receipt", "money", "dani", "search", "events", "meds", "focus", "habits", "goals")

    private fun readRoute(i: Intent?) {
        if (i == null) return
        val r = i.getStringExtra("route") ?: return
        i.removeExtra("route")
        if (r in shortcutRoutes) startRoute = r
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        readRoute(intent)
    }

    /** مشاركة نص/رابط من تطبيق آخر إلى DANI: تتحول لمهمة (العنوان + النص الكامل في الوصف). */
    private fun handleShare(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_SEND || i.type?.startsWith("text/") != true) return
        val text = i.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()
        i.action = null
        if (text.isEmpty() && subject.isEmpty()) return
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val title = (if (subject.isNotEmpty()) subject else firstLine).take(80)
        val desc = if (text.isNotEmpty() && (subject.isNotEmpty() || text.length > 80 || text.contains("\n"))) text else null
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DaniApplication.instance.taskRepository.insertTask(Task(title = title, description = desc))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "✅ زدتها للمهام: " + title.take(40), Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) { }
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
