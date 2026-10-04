package com.dani.assistant.presentation.focus

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.focus.FocusLog
import com.dani.assistant.core.focus.PomodoroTimer
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tasks by DaniApplication.instance.taskRepository.getAllTasks().collectAsState(initial = emptyList<Task>())
    var st by remember { mutableStateOf(PomodoroTimer.state(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var minutes by remember { mutableStateOf(25) }
    var picked by remember { mutableStateOf<Task?>(null) }
    var picking by remember { mutableStateOf(false) }
    var stats by remember { mutableStateOf(FocusLog.stats(context, 0)) }
    var week by remember { mutableStateOf(FocusLog.stats(context, 6)) }

    LaunchedEffect(Unit) {
        while (true) {
            PomodoroTimer.advanceIfDue(context)
            st = PomodoroTimer.state(context)
            now = System.currentTimeMillis()
            stats = FocusLog.stats(context, 0)
            week = FocusLog.stats(context, 6)
            delay(1000)
        }
    }

    val left = if (st.phase == "idle") 0L else (st.endAt - now).coerceAtLeast(0L) / 1000
    val clock = String.format(Locale.US, "%02d:%02d", left / 60, left % 60)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("⏱ التركيز", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (st.phase) { "focus" -> "🍅 تركيز"; "break" -> "☕ راحة"; else -> "جاهز؟" },
                    fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
                Text(if (st.phase == "idle") String.format(Locale.US, "%02d:00", minutes) else clock, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                if (st.phase == "focus" && st.taskTitle.isNotBlank()) Text("🎯 " + st.taskTitle, fontSize = 14.sp)
            }
        }
        if (st.phase == "idle") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                listOf(15, 25, 35, 50).forEach { m -> FilterChip(selected = minutes == m, onClick = { minutes = m }, label = { Text("$m د") }) }
            }
            OutlinedButton(onClick = { picking = true }) { Text(if (picked != null) "🎯 " + picked!!.title.take(30) else "🎯 اربطها بمهمة (اختياري)") }
            Button(onClick = {
                PomodoroTimer.startFocus(context, minutes, picked?.id ?: 0L, picked?.title ?: "")
                st = PomodoroTimer.state(context)
            }) { Text("▶ ابدأ التركيز") }
        } else {
            OutlinedButton(onClick = { PomodoroTimer.stop(context); st = PomodoroTimer.state(context) }) {
                Text(if (st.phase == "focus") "⏹ وقف (الجلسة ما تتحسبش)" else "⏭ تخطى الراحة")
            }
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("اليوم: " + stats.first + " جلسات • " + (stats.second / 60) + "س " + (stats.second % 60) + "د", fontSize = 14.sp)
                Text("آخر 7 أيام: " + week.first + " جلسات • " + (week.second / 60) + "س " + (week.second % 60) + "د", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
        Text("الإشعار يبقى فيه العدّ التنازلي، وينبهك كي تخلص الجلسة حتى لو سكرت التطبيق.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }

    if (picking) {
        val open = tasks.filter { !it.isCompleted && it.status.name != "CANCELLED" }.sortedBy { it.priority.level }.take(25)
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("اختار مهمة") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (open.isEmpty()) Text("ما كاينش مهام مفتوحة.")
                    open.forEach { t -> TextButton(onClick = { picked = t; picking = false }) { Text(t.title.take(50)) } }
                }
            },
            confirmButton = { TextButton(onClick = { picked = null; picking = false }) { Text("بلا مهمة") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("إلغاء") } }
        )
    }
}
