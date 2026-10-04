package com.dani.assistant.presentation.money

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.DaniApplication
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyStore
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun fmtDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(ms))

private fun monthRange(offset: Int): Pair<Long, Long> {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, offset)
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    val start = c.timeInMillis
    c.add(Calendar.MONTH, 1)
    return start to c.timeInMillis
}

private fun monthTitle(offset: Int): String {
    val c = Calendar.getInstance()
    c.add(Calendar.MONTH, offset)
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(c.time)
}

private fun digits(s: String): Long = s.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(MoneyStore.entries(context)) }
    var tab by remember { mutableStateOf(0) }
    var monthOffset by remember { mutableStateOf(0) }
    var editTx by remember { mutableStateOf<MoneyEntry?>(null) }
    var editDebt by remember { mutableStateOf<MoneyEntry?>(null) }
    var delEntry by remember { mutableStateOf<MoneyEntry?>(null) }

    fun refresh() { entries = MoneyStore.entries(context) }

    suspend fun dropReminder(e: MoneyEntry): MoneyEntry {
        val t = e.taskId ?: return e
        try { DaniApplication.instance.taskRepository.deleteTaskById(t) } catch (ex: Exception) { }
        return e.copy(taskId = null)
    }

    fun pickDebtReminder(e: MoneyEntry) {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val t = Calendar.getInstance()
                t.set(y, m, d, h, min, 0)
                t.set(Calendar.MILLISECOND, 0)
                scope.launch {
                    try {
                        val repo = DaniApplication.instance.taskRepository
                        val cleaned = dropReminder(e)
                        val who = if (e.type == "debt_to_me") "تذكير: " + e.person + " عندو لي " else "تسديد: عليّ لـ " + e.person + " "
                        val title = "💰 " + who + MoneyStore.fmt(e.amount)
                        val id = repo.insertTask(Task(title = title, description = e.note.ifBlank { null }, priority = PriorityLevel.IMPORTANT, dueDate = t.timeInMillis))
                        if (t.timeInMillis > System.currentTimeMillis()) repo.setTaskReminder(id, title, t.timeInMillis, ReminderType.NOTIFICATION)
                        MoneyStore.save(context, cleaned.copy(taskId = id))
                        Toast.makeText(context, "✅ تسجل التذكير في المهام", Toast.LENGTH_SHORT).show()
                    } catch (ex: Exception) {
                        Toast.makeText(context, "ما قدرتش نسجل التذكير", Toast.LENGTH_SHORT).show()
                    }
                    refresh()
                }
            }, 10, 0, true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("💰 المال", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("المصاريف والمداخيل") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("الديون") })
        }

        if (tab == 0) {
            val (start, end) = monthRange(monthOffset)
            val month = entries.filter { (it.type == "expense" || it.type == "income") && it.date in start until end }
            val income = month.filter { it.type == "income" }.sumOf { it.amount }
            val expense = month.filter { it.type == "expense" }.sumOf { it.amount }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { monthOffset -= 1 }) { Text("›", fontSize = 20.sp) }
                Text(monthTitle(monthOffset), fontWeight = FontWeight.Bold)
                TextButton(onClick = { monthOffset += 1 }) { Text("‹", fontSize = 20.sp) }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("➕ دخل: " + MoneyStore.fmt(income), fontSize = 14.sp)
                    Text("➖ مصروف: " + MoneyStore.fmt(expense), fontSize = 14.sp)
                    Text("الرصيد: " + MoneyStore.fmt(income - expense), fontWeight = FontWeight.Bold, fontSize = 16.sp,
                        color = if (income - expense >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                    val byCat = month.filter { it.type == "expense" }.groupBy { it.category.ifBlank { "أخرى" } }
                        .map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }.take(4)
                    if (byCat.isNotEmpty()) Text(byCat.joinToString("  •  ") { it.first + " " + MoneyStore.fmt(it.second) }, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            Button(onClick = { editTx = MoneyEntry(id = 0, type = "expense", amount = 0) }) { Text("➕ عملية جديدة") }
            if (month.isEmpty()) Text("ما كاينش عمليات في هذا الشهر.", color = MaterialTheme.colorScheme.outline)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(month, key = { it.id }) { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text((if (e.type == "income") "➕ " else "➖ ") + MoneyStore.fmt(e.amount), fontWeight = FontWeight.Bold,
                                    color = if (e.type == "income") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                Text(listOf(e.category, e.note).filter { it.isNotBlank() }.joinToString(" • ") + "  (" + fmtDate(e.date) + ")", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            TextButton(onClick = { editTx = e }) { Text("✏️") }
                            TextButton(onClick = { delEntry = e }) { Text("🗑") }
                        }
                    }
                }
            }
        } else {
            val debts = entries.filter { it.type == "debt_to_me" || it.type == "debt_i_owe" }
            val open = debts.filter { !it.settled }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("💸 عند الناس لي: " + MoneyStore.fmt(open.filter { it.type == "debt_to_me" }.sumOf { it.amount }), fontSize = 14.sp)
                    Text("🙏 عليّ للناس: " + MoneyStore.fmt(open.filter { it.type == "debt_i_owe" }.sumOf { it.amount }), fontSize = 14.sp)
                }
            }
            Button(onClick = { editDebt = MoneyEntry(id = 0, type = "debt_to_me", amount = 0) }) { Text("➕ دين جديد") }
            if (debts.isEmpty()) Text("ما كاينش ديون مسجلة.", color = MaterialTheme.colorScheme.outline)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(debts.sortedBy { it.settled }, key = { it.id }) { e ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                (if (e.type == "debt_to_me") "💸 " + e.person + " عندو لي " else "🙏 عليّ لـ " + e.person + " ") + MoneyStore.fmt(e.amount) + (if (e.settled) "  ✔ مسدّد" else ""),
                                fontWeight = FontWeight.Bold
                            )
                            if (e.note.isNotBlank()) Text(e.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text(fmtDate(e.date) + (if (e.taskId != null) "  ⏰ فيه تذكير" else ""), fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = {
                                    scope.launch {
                                        var upd = e.copy(settled = !e.settled)
                                        if (upd.settled) upd = dropReminder(upd)
                                        MoneyStore.save(context, upd)
                                        refresh()
                                    }
                                }) { Text(if (e.settled) "↩ رجّعو" else "✔ سدّد") }
                                if (!e.settled) TextButton(onClick = { pickDebtReminder(e) }) { Text("📅 تذكير") }
                                TextButton(onClick = { editDebt = e }) { Text("✏️") }
                                TextButton(onClick = { delEntry = e }) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        }
    }

    editTx?.let { et ->
        TxDialog(et, onDismiss = { editTx = null }, onSave = { e ->
            MoneyStore.save(context, if (e.id == 0L) e.copy(id = System.currentTimeMillis()) else e)
            refresh()
            editTx = null
        })
    }
    editDebt?.let { ed ->
        DebtDialog(ed, onDismiss = { editDebt = null }, onSave = { e ->
            MoneyStore.save(context, if (e.id == 0L) e.copy(id = System.currentTimeMillis()) else e)
            refresh()
            editDebt = null
        })
    }
    delEntry?.let { de ->
        AlertDialog(
            onDismissRequest = { delEntry = null },
            title = { Text("حذف؟") },
            text = { Text(MoneyStore.fmt(de.amount) + " " + de.person + de.category) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        dropReminder(de)
                        MoneyStore.delete(context, de.id)
                        refresh()
                    }
                    delEntry = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { delEntry = null }) { Text("إلغاء") } }
        )
    }
}

@Composable
private fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (key, label) -> OneChip(label, key == selected) { onSelect(key) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun TxDialog(initial: MoneyEntry, onDismiss: () -> Unit, onSave: (MoneyEntry) -> Unit) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(initial.type) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var category by remember { mutableStateOf(initial.category) }
    var note by remember { mutableStateOf(initial.note) }
    var date by remember { mutableStateOf(initial.date) }
    val cats = if (type == "income") MoneyStore.incomeCategories else MoneyStore.expenseCategories
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "عملية جديدة" else "تعديل العملية") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chips(listOf("expense" to "➖ مصروف", "income" to "➕ دخل"), type) { type = it; category = "" }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("المبلغ (دج)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Chips(cats.map { it to it }, category) { category = it }
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة") }, singleLine = true)
                TextButton(onClick = {
                    val c = Calendar.getInstance()
                    c.timeInMillis = date
                    DatePickerDialog(context, { _, y, m, d ->
                        val n = Calendar.getInstance()
                        n.set(y, m, d, 12, 0, 0)
                        date = n.timeInMillis
                    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
                }) { Text("📅 " + fmtDate(date)) }
            }
        },
        confirmButton = {
            Button(enabled = digits(amount) > 0, onClick = {
                onSave(initial.copy(type = type, amount = digits(amount), category = category, note = note.trim(), date = date))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun DebtDialog(initial: MoneyEntry, onDismiss: () -> Unit, onSave: (MoneyEntry) -> Unit) {
    var type by remember { mutableStateOf(initial.type) }
    var person by remember { mutableStateOf(initial.person) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) initial.amount.toString() else "") }
    var note by remember { mutableStateOf(initial.note) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "دين جديد" else "تعديل الدين") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Chips(listOf("debt_to_me" to "💸 عندو لي", "debt_i_owe" to "🙏 عليّ ليه"), type) { type = it }
                OutlinedTextField(value = person, onValueChange = { person = it }, label = { Text("الشخص") }, singleLine = true)
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text("المبلغ (دج)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("ملاحظة") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(enabled = person.isNotBlank() && digits(amount) > 0, onClick = {
                onSave(initial.copy(type = type, person = person.trim(), amount = digits(amount), note = note.trim()))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
