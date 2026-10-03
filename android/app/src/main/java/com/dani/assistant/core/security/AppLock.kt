package com.dani.assistant.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.dani.assistant.core.settings.AppSettings

/** قفل التطبيق: بصمة/وجه أو قفل شاشة الهاتف (PIN/نمط/كلمة سر). */
object AppLock {
    private const val AUTH = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** هل الهاتف فيه بصمة أو قفل شاشة مفعّل؟ */
    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    /** القفل مفعّل في الإعدادات والهاتف يدعمه (إذا الهاتف فقد القفل ما نحبسوش المستخدم). */
    fun shouldLock(ctx: Context): Boolean = AppSettings.lockEnabled(ctx) && available(ctx)

    fun findActivity(ctx: Context): FragmentActivity? {
        var c: Context? = ctx
        while (c is ContextWrapper) {
            if (c is FragmentActivity) return c
            c = c.baseContext
        }
        return null
    }

    fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFail(errString.toString())
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("فتح DANI")
            .setSubtitle("أكّد هويتك بالبصمة أو بقفل الهاتف")
            .setAllowedAuthenticators(AUTH)
            .build()
        prompt.authenticate(info)
    }
}

@Composable
fun LockScreen(activity: FragmentActivity, onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun ask() {
        if (busy) return
        busy = true
        error = null
        AppLock.authenticate(
            activity,
            onSuccess = { busy = false; onUnlocked() },
            onFail = { m -> busy = false; error = m }
        )
    }

    LaunchedEffect(Unit) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { ask() }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("DANI مقفول", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        val e = error
        if (e != null) Text(e, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
        Button(onClick = { ask() }) { Text("فتح القفل") }
    }
}
