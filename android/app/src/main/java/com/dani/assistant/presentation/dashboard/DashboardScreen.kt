package com.dani.assistant.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.dani.assistant.core.areas.AreaStore
import com.dani.assistant.core.habits.HabitStore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.Task
import com.dani.assistant.presentation.tasks.TasksViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(viewModel: TasksViewModel, onOpenSettings: () -> Unit = {}, onOpenRealEstate: () -> Unit = {}, onOpenHabits: () -> Unit = {}, onOpenMoney: () -> Unit = {}, onOpenGoals: () -> Unit = {}, onOpenFocus: () -> Unit = {}, onOpenMeds: () -> Unit = {}, onOpenEvents: () -> Unit = {}, onOpenNotes: () -> Unit = {}, onOpenSearch: () -> Unit = {}, onOpenReceipt: () -> Unit = {}) {
    val tasks by viewModel.tasks.collectAsState()
    val pending = tasks.filter { !it.isCompleted }
    val done = tasks.count { it.isCompleted }
    val urgent = pending.count { it.priority == PriorityLevel.URGENT_CRITICAL || it.priority == PriorityLevel.IMPORTANT }
    val dashCtx = LocalContext.current
    val areaMap = remember(tasks) { AreaStore.all(dashCtx) }
    val areaCounts = AreaStore.areas.map { a -> a to pending.count { areaMap[it.id] == a.key } }.filter { it.second > 0 }
    val habitList = remember { HabitStore.list(dashCtx) }
    val todayKey = HabitStore.dayKey(0)
    val evSoon = remember { com.dani.assistant.core.events.EventStore.upcoming(dashCtx, 7).size }
    val eventsLabel = if (evSoon > 0) " (" + evSoon + ")" else ""
    val habitsLabel = if (habitList.isEmpty()) "" else " (" + habitList.filter { HabitStore.dueToday(it) }.count { it.days.contains(todayKey) } + "/" + habitList.count { HabitStore.dueToday(it) } + ")"

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("DANI", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = onOpenSearch) { Text("🔎 بحث") }
                TextButton(onClick = onOpenSettings) { Text("⚙ الإعدادات") }
            }
        }
        var remindBackup by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.dani.assistant.core.backup.BackupReminder.shouldRemindNow(dashCtx)) }
        var backupNote by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
        val backupScope = androidx.compose.runtime.rememberCoroutineScope()
        if (remindBackup) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val d = com.dani.assistant.core.backup.BackupReminder.daysSince(com.dani.assistant.core.backup.BackupReminder.last(dashCtx), System.currentTimeMillis())
                    Text("💾 " + (if (d == null) "ما درتش نسخة احتياطية بعد" else "آخر نسخة احتياطية قبل " + d + " يوم"), fontWeight = FontWeight.Bold)
                    Text("النسخة التلقائية داخل التطبيق تتمسح مع حذفو. للأمان الكامل صدّر ملف من الذاكرة (يتحفظ في Documents).", fontSize = 12.sp)
                    if (backupNote.isNotEmpty()) Text(backupNote, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            backupNote = "..."
                            backupScope.launch { backupNote = com.dani.assistant.core.backup.AutoBackup.runNow(dashCtx); remindBackup = false }
                        }) { Text("نسخ دابا") }
                        TextButton(onClick = { com.dani.assistant.core.backup.BackupReminder.snooze(dashCtx); remindBackup = false }) { Text("بعدين (3 أيام)") }
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenHabits) { Text("✅ العادات" + habitsLabel) }
            OutlinedButton(onClick = onOpenMoney) { Text("💰 المال") }
            OutlinedButton(onClick = onOpenReceipt) { Text("🧾 فاتورة") }
            OutlinedButton(onClick = onOpenRealEstate) { Text("🏠 العقار") }
            OutlinedButton(onClick = onOpenGoals) { Text("🎯 الأهداف") }
            OutlinedButton(onClick = onOpenFocus) { Text("⏱ تركيز") }
            OutlinedButton(onClick = onOpenMeds) { Text("💊 الأدوية") }
            OutlinedButton(onClick = onOpenNotes) { Text("📝 ملاحظات") }
            OutlinedButton(onClick = onOpenEvents) { Text("🎂 المناسبات" + eventsLabel) }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("متبقية", pending.size.toString(), Modifier.weight(1f))
            StatCard("مهمة", urgent.toString(), Modifier.weight(1f))
            StatCard("منجزة", done.toString(), Modifier.weight(1f))
        }
        if (areaCounts.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                areaCounts.forEach { (a, n) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(a.emoji + " " + a.label + " " + n, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
            }
        }
        WeeklyStats(tasks)
        Text("المهام القادمة", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (pending.isEmpty()) {
            Text("لا توجد مهام. أضف مهمة من تبويب المهام أو من الشات.", color = MaterialTheme.colorScheme.outline)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pending.take(8)) { task -> UpcomingTask(task) }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 12.sp)
        }
    }
}

@Composable
private fun UpcomingTask(task: Task) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(task.title, fontWeight = FontWeight.Bold)
            val due = task.dueDate
            if (due != null) {
                Text(
                    SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(due)),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun WeeklyStats(tasks: List<Task>) {
    val days = (6 downTo 0).map { off ->
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_YEAR, -off)
        c.timeInMillis
    }
    val dayMs = 24L * 60L * 60L * 1000L
    val counts = days.map { start ->
        tasks.count { t ->
            val ca = t.completedAt
            ca != null && ca >= start && ca < start + dayMs
        }
    }
    val total = counts.sum()
    val created = tasks.count { it.createdAt >= days.first() }
    val max = (counts.maxOrNull() ?: 0).coerceAtLeast(1)
    val fmt = SimpleDateFormat("EEE", Locale("ar"))
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("آخر 7 أيام", fontWeight = FontWeight.Bold)
            Text("أنجزت " + total + " ، وأضفت " + created, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                days.forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(counts[i].toString(), fontSize = 10.sp)
                        Box(modifier = Modifier.width(22.dp).height((8 + 48 * counts[i] / max).dp).background(MaterialTheme.colorScheme.primary))
                        Text(fmt.format(Date(d)), fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
