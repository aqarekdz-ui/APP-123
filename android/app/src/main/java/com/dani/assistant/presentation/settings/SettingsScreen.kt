package com.dani.assistant.presentation.settings

import android.app.TimePickerDialog
import android.content.Context
import com.dani.assistant.core.digest.MorningDigest
import com.dani.assistant.core.watch.AdWatcher
import com.dani.assistant.core.watch.WatchSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.dani.assistant.core.designsystem.ThemeState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.core.ai.ProviderSettings
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.knowledge.KnowledgeBase
import com.dani.assistant.core.security.AppLock
import com.dani.assistant.core.settings.AppSettings
import com.dani.assistant.core.backup.AutoBackup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var autoLearn by remember { mutableStateOf(AppSettings.autoLearn(context)) }
    var localFirst by remember { mutableStateOf(AppSettings.localFirst(context)) }
    var digestOn by remember { mutableStateOf(AppSettings.digestEnabled(context)) }
    var digestMin by remember { mutableStateOf(AppSettings.digestMinutes(context)) }
    var weeklyOn by remember { mutableStateOf(AppSettings.weeklyReviewEnabled(context)) }
    var monthlyOn by remember { mutableStateOf(AppSettings.monthlyReportEnabled(context)) }
    var groqKey by remember { mutableStateOf(ProviderSettings.groqKey(context)) }
    var orKey by remember { mutableStateOf(ProviderSettings.openRouterKey(context)) }
    var knowledgeCount by remember { mutableStateOf(KnowledgeBase.getAll(context).size) }
    var confirmClearKnowledge by remember { mutableStateOf(false) }
    var confirmClearChat by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var lockOn by remember { mutableStateOf(AppSettings.lockEnabled(context)) }
    var watchOn by remember { mutableStateOf(WatchSettings.enabled(context)) }
    var watchUrl by remember { mutableStateOf(WatchSettings.url(context)) }
    var watchReq by remember { mutableStateOf(WatchSettings.required(context)) }
    var watchEx by remember { mutableStateOf(WatchSettings.exclude(context)) }
    var watchInt by remember { mutableStateOf(WatchSettings.intervalMin(context)) }
    var watchLast by remember { mutableStateOf(WatchSettings.lastSummary(context)) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var abOn by remember { mutableStateOf(AutoBackup.enabled(context)) }
    var abFolder by remember { mutableStateOf(AutoBackup.folderLabel(context)) }
    var abPass by remember { mutableStateOf(AutoBackup.pass(context)) }
    var abResult by remember { mutableStateOf<String?>(null) }
    var abBusy by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            } catch (e: Exception) { }
            AutoBackup.setTree(context, uri)
            abFolder = AutoBackup.folderLabel(context)
        }
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("الإعدادات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }

        // ---- التعلم ----
        Text("التعلم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "التعلم التلقائي",
            desc = "يستخرج معلومات عنك من كلامك ويحفظ أجوبة الذكاء الاصطناعي في ملف المعرفة. إيقافه يوفّر طلبات من الحصة المجانية.",
            checked = autoLearn
        ) { autoLearn = it; AppSettings.setAutoLearn(context, it) }
        SettingSwitch(
            title = "المحلي أولاً",
            desc = "يبحث في ملف المعرفة قبل ما يسأل الذكاء الاصطناعي، وهذا يوفّر الطلبات.",
            checked = localFirst
        ) { localFirst = it; AppSettings.setLocalFirst(context, it) }

        // ---- الملخص الصباحي ----
        val releaseSigned = remember { com.dani.assistant.core.backup.SigningCheck.isReleaseSigned(context) }
        Text("🔏 توقيع التطبيق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (releaseSigned) {
            Text("✅ نسخة موقّعة بمفتاح ثابت: التحديثات الجاية تتثبّت فوقها بلا حذف وبلا ضياع بيانات.", fontSize = 13.sp)
        } else {
            Text("⚠️ نسخة debug: مفتاحها يتبدّل، وتثبيت نسخة بمفتاح آخر يفرض حذف التطبيق (والبيانات تضيع). للانتقال لنسخة release (dani-apk-release) بأمان:", fontSize = 13.sp)
            Text("1) الذاكرة ← تصدير نسخة احتياطية مع كلمة سر (باش الأسرار تتحفظ).\n2) تأكد أن الملف في Documents ولا انقلو لمكان خارج التطبيق.\n3) احذف التطبيق وثبّت dani-apk-release.\n4) الذاكرة ← استيراد الملف، وأعد مفاتيح Groq/OpenRouter من الإعدادات.\nمن بعد، كل التحديثات تتثبّت فوقها عادي.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }

        AlarmReliabilityCard()

        Text("التنبيهات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "ملخص صباحي يومي",
            desc = "إشعار كل صباح بمهام اليوم والمتأخرة. ما يطلعش إذا ما عندكش مهام.",
            checked = digestOn
        ) {
            digestOn = it
            AppSettings.setDigestEnabled(context, it)
            MorningDigest.schedule(context)
        }
        if (digestOn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("الوقت: " + String.format("%02d:%02d", digestMin / 60, digestMin % 60), fontSize = 14.sp)
                Button(onClick = {
                    TimePickerDialog(context, { _, h, m ->
                        digestMin = h * 60 + m
                        AppSettings.setDigestMinutes(context, digestMin)
                        MorningDigest.schedule(context)
                    }, digestMin / 60, digestMin % 60, true).show()
                }) { Text("تغيير") }
            }
        }

        SettingSwitch(
            title = "مراجعة أسبوعية بالذكاء الاصطناعي",
            desc = "كل أحد على 19:00 إشعار بملخص الأسبوع (المنجز، المصاريف، العادات) مع نصيحة قصيرة. تقدر تطلبها في أي وقت من الشات: \"مراجعة الأسبوع\".",
            checked = weeklyOn
        ) {
            weeklyOn = it
            AppSettings.setWeeklyReviewEnabled(context, it)
            com.dani.assistant.core.digest.WeeklyReview.schedule(context)
        }

        SettingSwitch(
            title = "تقرير شهري",
            desc = "يوم 1 على 09:30 إشعار بملخص الشهر الفايت (المصروف، الدخل، الفئات، المقارنة، المهام). تقدر تطلبو من الشات: \"تقرير الشهر\".",
            checked = monthlyOn
        ) {
            monthlyOn = it
            AppSettings.setMonthlyReportEnabled(context, it)
            com.dani.assistant.core.digest.MonthlyReport.schedule(context)
        }

        // ---- الصوت الجزائري ----
        Text("الصوت الجزائري", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        var edgeOn by remember { mutableStateOf(com.dani.assistant.core.tts.EdgeTts.enabled(context)) }
        var edgeVoice by remember { mutableStateOf(com.dani.assistant.core.tts.EdgeTts.voice(context)) }
        SettingSwitch(
            title = "صوت جزائري مجاني (Edge)",
            desc = "يقرا الردود بلهجة جزائرية (ar-DZ) بدون مفتاح ولا حساب، يحتاج إنترنت. خدمة غير رسمية من Microsoft، وإذا فشلت يرجع لصوت الهاتف.",
            checked = edgeOn
        ) { edgeOn = it; com.dani.assistant.core.tts.EdgeTts.setEnabled(context, it) }
        if (edgeOn) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ar-DZ-AminaNeural" to "آمينة (امرأة)", "ar-DZ-IsmaelNeural" to "إسماعيل (رجل)").forEach { (v, label) ->
                    val pick = { edgeVoice = v; com.dani.assistant.core.tts.EdgeTts.setVoice(context, v) }
                    if (edgeVoice == v) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
                }
            }
        }
        Text("اختياري: مفتاح Azure (أولوية على Edge إذا انكتب)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        var azKey by remember { mutableStateOf(com.dani.assistant.core.tts.AzureTts.key(context)) }
        var azRegion by remember { mutableStateOf(com.dani.assistant.core.tts.AzureTts.region(context)) }
        var azVoice by remember { mutableStateOf(com.dani.assistant.core.tts.AzureTts.voice(context)) }
        androidx.compose.material3.OutlinedTextField(value = azKey, onValueChange = { azKey = it; com.dani.assistant.core.tts.AzureTts.setKey(context, it) }, label = { Text("Azure Speech key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        androidx.compose.material3.OutlinedTextField(value = azRegion, onValueChange = { azRegion = it; com.dani.assistant.core.tts.AzureTts.setRegion(context, it) }, label = { Text("المنطقة (مثلا francecentral)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ar-DZ-AminaNeural" to "آمينة (امرأة)", "ar-DZ-IsmaelNeural" to "إسماعيل (رجل)").forEach { (v, label) ->
                val pick = { azVoice = v; com.dani.assistant.core.tts.AzureTts.setVoice(context, v) }
                if (azVoice == v) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
            }
        }

        // ---- مراقب الإعلانات ----
        Text("مراقب الإعلانات (Ouedkniss)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "مراقبة تلقائية",
            desc = "يفتح صفحة البحث في الخلفية، يفحص الإعلانات الجديدة، ويرسل تنبيه إذا وصف الإعلان ما فيهش الكلمات الممنوعة.",
            checked = watchOn
        ) {
            watchOn = it
            WatchSettings.setEnabled(context, it)
            AdWatcher.reschedule(context)
        }
        androidx.compose.material3.OutlinedTextField(value = watchUrl, onValueChange = { watchUrl = it; WatchSettings.setUrl(context, it) }, label = { Text("رابط صفحة البحث") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.material3.OutlinedTextField(value = watchReq, onValueChange = { watchReq = it; WatchSettings.setRequired(context, it) }, label = { Text("كلمات لازم تكون في الإعلان (بفاصلة)") }, modifier = Modifier.fillMaxWidth())
        androidx.compose.material3.OutlinedTextField(value = watchEx, onValueChange = { watchEx = it; WatchSettings.setExclude(context, it) }, label = { Text("كلمات ممنوعة في الوصف (بفاصلة)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("كل:", fontSize = 13.sp)
            listOf(30 to "30 د", 60 to "1 س", 180 to "3 س").forEach { (m, label) ->
                val pick = { watchInt = m; WatchSettings.setIntervalMin(context, m); AdWatcher.reschedule(context) }
                if (watchInt == m) Button(onClick = pick) { Text(label) } else OutlinedButton(onClick = pick) { Text(label) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { AdWatcher.runNow(context); msg = "بدا الفحص في الخلفية (يدوم دقيقة تقريباً). ارجع شوف النتيجة." }) { Text("جرّب الآن") }
            OutlinedButton(onClick = { watchLast = WatchSettings.lastSummary(context) }) { Text("حدّث النتيجة") }
            OutlinedButton(onClick = { WatchSettings.clearSeen(context); msg = "تم مسح قائمة الإعلانات المشاهدة" }) { Text("صفّر") }
        }
        Text("آخر فحص: " + watchLast, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

        // ---- المظهر ----
        Text("المظهر", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "داكن", 1 to "فاتح", 2 to "النظام").forEach { (m, label) ->
                val pick = {
                    ThemeState.mode = m
                    AppSettings.setThemeMode(context, m)
                }
                if (ThemeState.mode == m) Button(onClick = pick) { Text(label) }
                else OutlinedButton(onClick = pick) { Text(label) }
            }
        }

        // ---- الأمان ----
        Text("الأمان", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch(
            title = "قفل التطبيق",
            desc = "يطلب البصمة أو قفل شاشة الهاتف كل ما تفتح DANI (وبعد غياب أكثر من 30 ثانية).",
            checked = lockOn
        ) { want ->
            val act = AppLock.findActivity(context)
            if (!want) {
                lockOn = false
                AppSettings.setLockEnabled(context, false)
                msg = "تم إيقاف القفل"
            } else if (act == null || !AppLock.available(context)) {
                msg = "الهاتف ما فيهش بصمة ولا قفل شاشة مفعّل. فعّل واحد من إعدادات الهاتف ثم عاود."
            } else {
                AppLock.authenticate(act,
                    onSuccess = {
                        lockOn = true
                        AppSettings.setLockEnabled(context, true)
                        msg = "تم تفعيل القفل ✅"
                    },
                    onFail = { m -> msg = m })
            }
        }

        // ---- مزودات AI ----
        Text("مزودات الذكاء الاصطناعي المجانية", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "Gemini يخدم دايماً. المفاتيح التالية احتياط تلقائي إذا Gemini وصل الحد (بدون بطاقة بنكية).",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
        )
        TextField(
            value = groqKey, onValueChange = { groqKey = it },
            label = { Text("Groq key (console.groq.com/keys)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        TextField(
            value = orKey, onValueChange = { orKey = it },
            label = { Text("OpenRouter key (openrouter.ai/keys)") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(onClick = {
            ProviderSettings.save(context, groqKey, orKey)
            msg = "تم حفظ المفاتيح ✅"
        }) { Text("حفظ المفاتيح") }
        Text(
            "الحالة: Gemini ✅ | Groq " + (if (groqKey.isNotBlank()) "✅" else "—") + " | OpenRouter " + (if (orKey.isNotBlank()) "✅" else "—"),
            fontSize = 12.sp
        )

        Button(enabled = !testing, onClick = {
            testing = true
            testResult = "⏳ جاري الاختبار..."
            scope.launch {
                testResult = try { GeminiAI.testAll() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { "❌ " + (e.message ?: "خطأ") }
                testing = false
            }
        }) { Text("🔌 اختبار الاتصال") }
        testResult?.let { Text(it, fontSize = 12.sp) }

        // ---- البيانات ----
        Text("البيانات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("ملف المعرفة: " + knowledgeCount + " مدخل", fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { confirmClearKnowledge = true }) { Text("مسح المعرفة") }
            Button(onClick = { confirmClearChat = true }) { Text("مسح المحادثة") }
        }
        Text("للنسخ الاحتياطي (تصدير/استيراد) افتح تبويب الذاكرة.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)

        // ---- نسخ احتياطي تلقائي ----
        Text("💾 نسخ احتياطي تلقائي", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        SettingSwitch("نسخة كل أسبوع", "ملف نسخة لكل بياناتك (مهام، عادات، مال، عقار، ذاكرة، شات...). تبقى آخر 5 نسخ.", abOn) {
            abOn = it
            AutoBackup.setEnabled(context, it)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { folderLauncher.launch(null) }) { Text("📁 اختر مجلد") }
            Text(abFolder ?: "بدون: يحفظ داخل التطبيق فقط", fontSize = 12.sp)
        }
        TextField(
            value = abPass,
            onValueChange = { abPass = it; AutoBackup.setPass(context, it) },
            label = { Text("كلمة سر النسخة (اختياري: باش تدخل الأسرار)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Button(enabled = !abBusy, onClick = {
            abBusy = true
            abResult = "⏳ جاري النسخ..."
            scope.launch {
                abResult = AutoBackup.runNow(context)
                abBusy = false
            }
        }) { Text("💾 نسخ الآن") }
        val abShown = abResult ?: AutoBackup.lastInfo(context).takeIf { it.isNotBlank() }?.let { "آخر نسخة: " + it }
        if (abShown != null) Text(abShown, fontSize = 12.sp)
        Text("نصيحة: اختر مجلداً يتزامن مع Google Drive ولا Syncthing باش ما تضيعش النسخ إذا ضاع الهاتف. للاستعادة: تبويب الذاكرة ← استيراد.", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)

        val m = msg
        if (m != null) Text(m, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
    }

    if (confirmClearKnowledge) {
        AlertDialog(
            onDismissRequest = { confirmClearKnowledge = false },
            title = { Text("مسح ملف المعرفة؟") },
            text = { Text("كل الأجوبة اللي تعلّمها DANI تتمسح نهائياً. المعلومات الشخصية والأسرار والمهام ما تتأثرش.") },
            confirmButton = {
                Button(onClick = {
                    KnowledgeBase.clearAll(context)
                    knowledgeCount = 0
                    confirmClearKnowledge = false
                    msg = "تم مسح المعرفة"
                }) { Text("امسح") }
            },
            dismissButton = { TextButton(onClick = { confirmClearKnowledge = false }) { Text("إلغاء") } }
        )
    }

    if (confirmClearChat) {
        AlertDialog(
            onDismissRequest = { confirmClearChat = false },
            title = { Text("مسح المحادثة؟") },
            text = { Text("كل رسائل الشات تتمسح. الذاكرة والمهام ما تتأثرش.") },
            confirmButton = {
                Button(onClick = {
                    context.getSharedPreferences("chat", Context.MODE_PRIVATE).edit().clear().apply()
                    confirmClearChat = false
                    msg = "تم مسح المحادثة"
                }) { Text("امسح") }
            },
            dismissButton = { TextButton(onClick = { confirmClearChat = false }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
