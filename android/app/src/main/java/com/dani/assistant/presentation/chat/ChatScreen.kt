package com.dani.assistant.presentation.chat

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import com.dani.assistant.core.web.WebSearch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.ai.ProviderSettings
import com.dani.assistant.core.settings.AppSettings
import androidx.compose.material3.AlertDialog
import com.dani.assistant.core.ai.ParsedTask
import com.dani.assistant.core.alarm.ScheduleResult
import com.dani.assistant.domain.model.Recurrence
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.core.knowledge.KnowledgeBase
import com.dani.assistant.core.memory.MemoryStore
import com.dani.assistant.core.memory.SecretStore
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Message(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis(), val mood: String = "")

private val commandTriggers = listOf(
    "ذكرني", "ذكرلي", "فكرني", "فكرلي", "ضيف مهمة", "اضف مهمة", "زيد مهمة", "سجل مهمة", "اضافة مهمة",
    "ضيف تذكير", "زيد تذكير", "remind me", "rappelle moi", "rappelle-moi", "add task"
)

private fun looksLikeTaskCommand(text: String): Boolean {
    val n = text.lowercase()
        .replace(Regex("[\\u064B-\\u0652\\u0640]"), "")
        .replace("أ", "ا").replace("إ", "ا").replace("آ", "ا")
        .replace("ى", "ي")
    return commandTriggers.any { n.contains(it) }
}

/** ينشئ المهمة والتذكير مباشرة، ويرجع نص التأكيد. */
private suspend fun createTaskFromCommand(cmd: ParsedTask): String {
    val repo = DaniApplication.instance.taskRepository
    val id = repo.insertTask(
        Task(title = cmd.title, priority = cmd.priority, dueDate = cmd.dueDate, recurrence = cmd.recurrence)
    )
    val sb = StringBuilder("✅ زدت المهمة: ").append(cmd.title)
    val due = cmd.dueDate
    if (due != null) {
        sb.append("\n⏰ ").append(SimpleDateFormat("EEEE dd/MM HH:mm", Locale.getDefault()).format(Date(due)))
        if (cmd.recurrence != Recurrence.NONE) sb.append("\n🔁 ").append(cmd.recurrence.arabic)
        val r = repo.setTaskReminder(id, cmd.title, due, ReminderType.NOTIFICATION)
        if (r is ScheduleResult.ExactAlarmPermissionRequired) {
            sb.append("\n⚠️ التذكير مجدول لكن تقريبي: إذن المنبهات الدقيقة مش مفعّل (تبويب المهام)")
        }
    } else {
        sb.append("\n(بدون وقت تذكير)")
    }
    return sb.toString()
}

private val SEARCH_PREFIXES = listOf("ابحث لي عن", "ابحث عن", "ابحث", "دور لي على", "دور على", "قوقل", "جوجل", "سيرش", "search for", "search", "google", "recherche", "cherche")
private val SEARCH_HINTS = listOf("اخر اخبار", "آخر أخبار", "آخر اخبار", "اخبار اليوم", "أخبار اليوم", "latest news", "news about")

/** يرجع نص البحث إذا الرسالة طلب بحث على الإنترنت، وإلا null. */
private fun searchQueryOf(text: String): String? {
    val t = text.trim()
    val low = t.lowercase()
    for (p in SEARCH_PREFIXES) {
        if (low.startsWith(p)) {
            val q = t.substring(p.length).trim().trimStart(':', '-', '،').trim()
            return if (q.length >= 2) q else null
        }
    }
    if (SEARCH_HINTS.any { low.contains(it) }) return t
    return null
}

private const val KEY_JSON = "msg_json"
private const val KEY_LEGACY = "msg"

private fun saveMessages(prefs: SharedPreferences, list: List<Message>) {
    val arr = JSONArray()
    list.forEach {
        arr.put(JSONObject().put("t", it.text).put("u", it.isUser).put("ts", it.timestamp).put("m", it.mood))
    }
    prefs.edit().putString(KEY_JSON, arr.toString()).remove(KEY_LEGACY).apply()
}

private fun loadMessages(prefs: SharedPreferences): List<Message> {
    val json = prefs.getString(KEY_JSON, null)
    if (json != null && json.isNotEmpty()) {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Message(o.optString("t"), o.optBoolean("u"), o.optLong("ts", System.currentTimeMillis()), o.optString("m"))
            }
        } catch (e: Exception) { emptyList() }
    }
    // ترحيل من الصيغة القديمة (:::/|||)
    val legacy = prefs.getString(KEY_LEGACY, null)
    if (legacy != null && legacy.isNotEmpty()) {
        val list = mutableListOf<Message>()
        legacy.split("|||").forEach { part ->
            val parts = part.split(":::")
            if (parts.size >= 2) {
                val ts = if (parts.size >= 3) parts[2].toLongOrNull() ?: System.currentTimeMillis() else System.currentTimeMillis()
                val md = if (parts.size >= 4) parts[3] else ""
                list.add(Message(parts[0], parts[1] == "true", ts, md))
            }
        }
        return list
    }
    return emptyList()
}

// ---- محادثات متعددة: المحادثة الحالية تبقى في prefs "chat"، والقديمة تتأرشف في "chat_archive" ----
private const val ARCH_PREFS = "chat_archive"
private const val ARCH_KEY = "convs"
private const val ARCH_MAX = 20

private data class ArchivedChat(val id: Long, val title: String, val msgs: List<Message>)

private fun chatTitle(msgs: List<Message>): String {
    val t = msgs.firstOrNull { it.isUser }?.text?.trim().orEmpty()
    return if (t.isEmpty()) "محادثة" else t.take(30)
}

private fun loadArchive(ctx: Context): MutableList<ArchivedChat> {
    val json = ctx.getSharedPreferences(ARCH_PREFS, Context.MODE_PRIVATE).getString(ARCH_KEY, null)
    val out = mutableListOf<ArchivedChat>()
    if (json.isNullOrEmpty()) return out
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ma = o.getJSONArray("msgs")
            val msgs = (0 until ma.length()).map {
                val m = ma.getJSONObject(it)
                Message(m.optString("t"), m.optBoolean("u"), m.optLong("ts", System.currentTimeMillis()), m.optString("m"))
            }
            out.add(ArchivedChat(o.optLong("id"), o.optString("title"), msgs))
        }
    } catch (e: Exception) { }
    return out
}

private fun saveArchive(ctx: Context, list: List<ArchivedChat>) {
    val arr = JSONArray()
    list.take(ARCH_MAX).forEach { c ->
        val ma = JSONArray()
        c.msgs.forEach { ma.put(JSONObject().put("t", it.text).put("u", it.isUser).put("ts", it.timestamp).put("m", it.mood)) }
        arr.put(JSONObject().put("id", c.id).put("title", c.title).put("msgs", ma))
    }
    ctx.getSharedPreferences(ARCH_PREFS, Context.MODE_PRIVATE).edit().putString(ARCH_KEY, arr.toString()).apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    var messages by remember { mutableStateOf(listOf<Message>()) }
    var userInput by remember { mutableStateOf("") }
    var suggestedTask by remember { mutableStateOf<String?>(null) }
    var currentMood by remember { mutableStateOf("Neutral") }
    var lastLocalKey by remember { mutableStateOf<String?>(null) }
    var showProviders by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var sendJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var showConvs by remember { mutableStateOf(false) }
    var convs by remember { mutableStateOf(listOf<ArchivedChat>()) }
    var groqKeyInput by remember { mutableStateOf("") }
    var orKeyInput by remember { mutableStateOf("") }
    val ai = remember { GeminiAI() }
    val scope = rememberCoroutineScope()
    val prefs: SharedPreferences = context.getSharedPreferences("chat", Context.MODE_PRIVATE)

    LaunchedEffect(Unit) {
        val loaded = loadMessages(prefs)
        if (loaded.isNotEmpty()) messages = loaded
    }

    LaunchedEffect(messages) {
        if (messages.isNotEmpty()) saveMessages(prefs, messages)
    }

    // محرك Google TTS أولاً (سامسونغ الافتراضي ما فيهش عربي فيقرا بالإنجليزية)، وإذا مش موجود المحرك الافتراضي
    var ttsReady by remember { mutableStateOf(false) }
    var ttsArabic by remember { mutableStateOf(false) }
    var ttsFallback by remember { mutableStateOf(false) }
    val tts = remember(ttsFallback) {
        TextToSpeech(context, { st ->
            if (st == TextToSpeech.SUCCESS) ttsReady = true
            else if (!ttsFallback) ttsFallback = true
        }, if (ttsFallback) null else "com.google.android.tts")
    }
    LaunchedEffect(tts, ttsReady) {
        if (ttsReady) {
            val cands = listOf(Locale("ar", "DZ"), Locale("ar"), Locale("ar", "SA"), Locale("ar", "EG"))
            val ok = cands.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE && tts.setLanguage(it) >= TextToSpeech.LANG_AVAILABLE }
            ttsArabic = ok != null
        }
    }
    DisposableEffect(tts) { onDispose { tts.stop(); tts.shutdown() } }

    val speechLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0)
            if (!text.isNullOrBlank()) userInput = text
        }
    }

    fun analyzeMessage(text: String): Pair<String, String?> {
        val lower = text.lowercase()
        var mood = "Neutral"
        var task: String? = null
        if (lower.contains("happy") || lower.contains("good") || lower.contains("great") || lower.contains("farhan") || lower.contains("mabsoot")) mood = "Happy"
        else if (lower.contains("sad") || lower.contains("tired") || lower.contains("hazin") || lower.contains("taaban")) mood = "Sad"
        else if (lower.contains("angry") || lower.contains("mad") || lower.contains("ghadban") || lower.contains("3asab")) mood = "Angry"
        else if (lower.contains("worried") || lower.contains("scared") || lower.contains("khayef")) mood = "Worried"
        else if (lower.contains("love") || lower.contains("hub") || lower.contains("nhebbek")) mood = "Loved"
        if (lower.contains("remind") || lower.contains("tadhkir") || lower.contains("dhakker")) task = "Reminder: " + text
        else if (lower.contains("task") || lower.contains("muhimma") || lower.contains("mohim")) task = "Task: " + text
        else if (lower.contains("meeting") || lower.contains("maw3id") || lower.contains("rendez")) task = "Meeting: " + text
        else if (lower.contains("buy") || lower.contains("shop") || lower.contains("shri") || lower.contains("nshri")) task = "Shopping: " + text
        else if (lower.contains("call") || lower.contains("wasil") || lower.contains("nwasel")) task = "Call: " + text
        return Pair(mood, task)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("المزاج:", style = MaterialTheme.typography.bodyMedium)
                Text(moodAr(currentMood), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (messages.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("مرحبا! أنا DANI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("اضغط على الميكروفون باش تتكلم")
                            Text("اضغط على السماعة باش تسمع الرد")
                            Text("المحادثة تتحفظ تلقائياً")
                            Text("نقترح عليك مهام من كلامك")
                        }
                    }
                }
            }
            items(messages) { msg ->
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (msg.isUser) Alignment.End else Alignment.Start) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                        if (!msg.isUser) {
                            Button(onClick = { if (ttsArabic) tts.speak(msg.text.replace(Regex("[\\p{So}\\p{Cs}\\uFE0F\\u200D]"), ""), TextToSpeech.QUEUE_FLUSH, null, null)
                                else {
                                    android.widget.Toast.makeText(context, "ما كاين صوت عربي في الهاتف: اختار محرك Google ونزّل اللغة العربية", android.widget.Toast.LENGTH_LONG).show()
                                    try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                    catch (e: Exception) { try { context.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e2: Exception) { } }
                                } }, modifier = Modifier.padding(end = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("speak", fontSize = 14.sp) }
                        }
                        Surface(color = if (msg.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = msg.text, color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (msg.mood.isNotEmpty()) { Spacer(modifier = Modifier.height(4.dp)); Text(msg.mood, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline) }
                            }
                        }
                    }
                    Text(text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)), fontSize = 10.sp, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            if (suggestedTask != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) { Text("اقتراح مهمة:", fontWeight = FontWeight.Bold, fontSize = 12.sp); Text(suggestedTask!!, fontSize = 14.sp, modifier = Modifier.padding(end = 8.dp)) }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = { suggestedTask = null }) { Text("تجاهل") }
                                Button(onClick = {
                                    val t = suggestedTask
                                    if (t != null) scope.launch { DaniApplication.instance.taskRepository.insertTask(Task(title = t)) }
                                    suggestedTask = null
                                }) { Text("إضافة") }
                            }
                        }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-DZ") }; speechLauncher.launch(intent) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Text("mic", fontSize = 18.sp) }
            TextField(value = userInput, onValueChange = { userInput = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب أو تكلّم...") }, shape = RoundedCornerShape(24.dp))
            if (sending) {
                Button(onClick = { sendJob?.cancel() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(24.dp)) { Text("⏹ وقف") }
            } else Button(onClick = { if (userInput.isNotBlank()) { val inputText = userInput; if (inputText.startsWith("تذكر") || inputText.lowercase().startsWith("remember")) { val fact = inputText.substringAfter(" ", "").trim(); if (fact.isNotEmpty()) { if (SecretStore.looksSensitive(fact)) SecretStore.add(context, fact) else MemoryStore.add(context, fact) } }; val result = analyzeMessage(inputText); currentMood = result.first; suggestedTask = result.second; val msg = Message(inputText, true, mood = result.first); messages = messages + msg; userInput = ""; sending = true; sendJob = scope.launch { try {
                if (looksLikeTaskCommand(inputText)) {
                    val cmd = ai.parseTaskCommand(inputText)
                    if (cmd != null) {
                        val reply = try { createTaskFromCommand(cmd) } catch (e: Exception) { null }
                        if (reply != null) {
                            suggestedTask = null
                            messages = messages + Message(reply, false, mood = "مهمة")
                            return@launch
                        }
                    }
                }
                val searchQ = searchQueryOf(inputText)
                val qKey = KnowledgeBase.keyOf(inputText)
                val bypass = qKey.isNotEmpty() && qKey == lastLocalKey
                lastLocalKey = null
                val local = if (searchQ != null || bypass || !AppSettings.localFirst(context)) null else KnowledgeBase.findLocal(context, inputText)
                if (local != null) {
                    messages = messages + Message(local, false, mood = "محلي")
                    lastLocalKey = qKey
                    return@launch
                }
                val idx = messages.size
                messages = messages + Message("", false)
                fun setReply(t: String) { if (idx < messages.size) messages = messages.toMutableList().also { it[idx] = Message(t, false) } }
                val sb = StringBuilder()
                var failed = false
                var promptText = inputText
                var sources = ""
                if (searchQ != null) {
                    setReply("🔎 نقلّب في الإنترنت...")
                    val res = WebSearch.search(searchQ)
                    if (res.isNotEmpty()) {
                        promptText = "نتائج بحث حديثة من الإنترنت:\n" +
                            res.mapIndexed { i, r -> (i + 1).toString() + ". " + r.title + " — " + r.snippet.take(300) }.joinToString("\n") +
                            "\n\nأجب على سؤال المستخدم بالدارجة وباختصار اعتماداً على هذي النتائج، وإذا ما كفاتش قول هذا. سؤال المستخدم: " + inputText
                        sources = res.take(3).joinToString("\n") { it.url }
                    } else {
                        setReply("⚠️ البحث ما خدمش (الإنترنت ولا المحرك)، نجاوبك من معلوماتي...")
                    }
                }
                try {
                    ai.sendMessageStream(promptText, MemoryStore.getAll(context).takeLast(12) + (if (SecretStore.looksSensitive(inputText)) SecretStore.getAll(context) else emptyList())).collect { chunk -> sb.append(chunk); setReply(sb.toString()) }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    setReply(if (sb.isEmpty()) "⏹ تم الإيقاف" else sb.toString() + "\n⏹")
                    throw e
                } catch (e: Exception) { failed = true; if (sb.isEmpty()) setReply(GeminiAI.friendlyError(e)) }
                if (!failed && sources.isNotEmpty() && sb.isNotEmpty()) { sb.append("\n\n🔗 المصادر:\n").append(sources); setReply(sb.toString()) }
                if (sb.isEmpty()) setReply("...")
                if (AppSettings.autoLearn(context) && searchQ == null && !failed && sb.isNotEmpty() && KnowledgeBase.cacheable(inputText, sb.toString())) KnowledgeBase.put(context, inputText, sb.toString())
                val known = MemoryStore.getAll(context)
                val (t, facts, secrets) = if (AppSettings.autoLearn(context)) ai.analyze(inputText, known) else Triple(null, emptyList<String>(), emptyList<String>())
                if (t != null) suggestedTask = t
                facts.forEach { MemoryStore.add(context, it) }
                secrets.forEach { SecretStore.add(context, it) }
                val all = MemoryStore.getAll(context)
                if (all.size > 40) ai.consolidate(all)?.let { MemoryStore.replaceAll(context, it) }
            } finally { sending = false } } } }, shape = RoundedCornerShape(24.dp)) { Text("إرسال") }
        }
        Row(modifier = Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { messages = emptyList(); prefs.edit().clear().apply() }) { Text("مسح المحادثة", fontSize = 12.sp) }
            TextButton(enabled = !sending && messages.isNotEmpty(), onClick = {
                val l = loadArchive(context)
                l.add(0, ArchivedChat(System.currentTimeMillis(), chatTitle(messages), messages))
                saveArchive(context, l)
                messages = emptyList(); suggestedTask = null
                prefs.edit().clear().apply()
            }) { Text("➕ جديدة", fontSize = 12.sp) }
            TextButton(enabled = !sending, onClick = { convs = loadArchive(context); showConvs = true }) { Text("🗂 المحادثات", fontSize = 12.sp) }
            TextButton(onClick = {
                groqKeyInput = ProviderSettings.groqKey(context)
                orKeyInput = ProviderSettings.openRouterKey(context)
                showProviders = true
            }) { Text("⚙ مزودات AI", fontSize = 12.sp) }
        }
        if (showConvs) {
            AlertDialog(
                onDismissRequest = { showConvs = false },
                title = { Text("المحادثات السابقة") },
                text = {
                    if (convs.isEmpty()) Text("ما كاين حتى محادثة محفوظة.", fontSize = 13.sp)
                    else LazyColumn(modifier = Modifier.height(280.dp)) {
                        items(convs) { c ->
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                TextButton(modifier = Modifier.weight(1f), onClick = {
                                    val l = loadArchive(context)
                                    val chosen = l.firstOrNull { it.id == c.id }
                                    if (chosen != null) {
                                        l.remove(chosen)
                                        if (messages.isNotEmpty()) l.add(0, ArchivedChat(System.currentTimeMillis(), chatTitle(messages), messages))
                                        saveArchive(context, l)
                                        messages = chosen.msgs; suggestedTask = null
                                    }
                                    showConvs = false
                                }) { Text(c.title + " (" + c.msgs.size + ")", fontSize = 13.sp) }
                                TextButton(onClick = {
                                    val l = loadArchive(context).filter { it.id != c.id }
                                    saveArchive(context, l)
                                    convs = l
                                }) { Text("🗑") }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showConvs = false }) { Text("إغلاق") } }
            )
        }
        if (showProviders) {
            AlertDialog(
                onDismissRequest = { showProviders = false },
                title = { Text("مزودات AI المجانية") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gemini يخدم دايماً. زيد مفاتيح مجانية (بدون بطاقة) كاحتياط تلقائي إذا Gemini وصل الحد:", fontSize = 12.sp)
                        TextField(value = groqKeyInput, onValueChange = { groqKeyInput = it }, label = { Text("Groq key (console.groq.com/keys)") }, singleLine = true)
                        TextField(value = orKeyInput, onValueChange = { orKeyInput = it }, label = { Text("OpenRouter key (openrouter.ai/keys)") }, singleLine = true)
                    }
                },
                confirmButton = { Button(onClick = { ProviderSettings.save(context, groqKeyInput, orKeyInput); showProviders = false }) { Text("حفظ") } },
                dismissButton = { TextButton(onClick = { showProviders = false }) { Text("إلغاء") } }
            )
        }
    }
}

private fun moodAr(m: String): String = when (m) {
    "Neutral" -> "عادي"
    "Happy" -> "فرحان"
    "Sad" -> "حزين"
    "Angry" -> "غاضب"
    "Worried" -> "قلقان"
    "Loved" -> "محبوب"
    else -> m
}
