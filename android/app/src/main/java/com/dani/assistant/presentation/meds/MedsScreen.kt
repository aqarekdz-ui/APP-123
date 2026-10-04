package com.dani.assistant.presentation.meds

import android.app.TimePickerDialog
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.core.meds.Dose
import com.dani.assistant.core.meds.Med
import com.dani.assistant.core.meds.MedAlarms
import com.dani.assistant.core.meds.MedStore
import com.dani.assistant.core.meds.fmtMinute
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var meds by remember { mutableStateOf(MedStore.list(context)) }
    var doses by remember { mutableStateOf(MedStore.todayDoses(context)) }
    var edit by remember { mutableStateOf<Med?>(null) }
    var del by remember { mutableStateOf<Med?>(null) }

    fun refresh() { meds = MedStore.list(context); doses = MedStore.todayDoses(context) }
    val nowMin = MedStore.nowMinute()
    val (got, exp) = MedStore.adherence(context)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("💊 الأدوية", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Button(onClick = { edit = Med(id = 0, name = "") }) { Text("➕ دواء جديد") }
        if (exp > 0) Text("الالتزام آخر 7 أيام: " + got + " / " + exp + " (" + (got * 100 / exp) + "%)", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("جرعات اليوم", fontWeight = FontWeight.Bold) }
            if (doses.isEmpty()) item { Text("ما كاينش جرعات اليوم.", color = MaterialTheme.colorScheme.outline) }
            items(doses, key = { it.med.id.toString() + "_" + it.minute }) { d: Dose ->
                val missed = !d.taken && d.minute < nowMin - 60
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fmtMinute(d.minute) + "  💊 " + d.med.name, fontWeight = FontWeight.Bold)
                            val sub = (if (d.med.dose.isNotBlank()) d.med.dose + " • " else "") + (if (d.taken) "✅ أخذتو" else if (missed) "⚠️ فاتتك" else "⬜ باقية")
                            Text(sub, fontSize = 12.sp, color = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
                        }
                        TextButton(onClick = {
                            MedStore.mark(context, d.med.id, d.minute, !d.taken)
                            if (!d.taken) MedAlarms.cancelNotif(context, d.med.id, d.minute)
                            refresh()
                        }) { Text(if (d.taken) "↩ تراجع" else "✔ أخذتو") }
                    }
                }
            }
            item { Text("كل الأدوية", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
            items(meds, key = { it.id }) { m ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text((if (MedStore.isLive(m)) "" else "⏸ ") + m.name + (if (m.dose.isNotBlank()) " — " + m.dose else ""), fontWeight = FontWeight.Bold)
                        Text(m.times.joinToString("  ") { fmtMinute(it) } + (if (m.endAt > 0) "  • ينتهي " + java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault()).format(java.util.Date(m.endAt)) else "  • مستمر"),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            TextButton(onClick = {
                                val u = m.copy(active = !m.active)
                                MedStore.save(context, u)
                                if (u.active) MedAlarms.rescheduleAll(context) else MedAlarms.cancelMed(context, u)
                                refresh()
                            }) { Text(if (m.active) "⏸ وقف" else "▶ شغّل") }
                            TextButton(onClick = { edit = m }) { Text("✏️") }
                            TextButton(onClick = { del = m }) { Text("🗑") }
                        }
                    }
                }
            }
        }
    }

    edit?.let { em ->
        var name by remember(em.id) { mutableStateOf(em.name) }
        var dose by remember(em.id) { mutableStateOf(em.dose) }
        var times by remember(em.id) { mutableStateOf(em.times) }
        var days by remember(em.id) { mutableStateOf(if (em.endAt > 0) 7 else 0) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (em.id == 0L) "دواء جديد" else "تعديل الدواء") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم الدواء") }, singleLine = true)
                    OutlinedTextField(value = dose, onValueChange = { dose = it }, label = { Text("الجرعة (حبة، 5 مل...)") }, singleLine = true)
                    Text("المواعيد في اليوم:", fontSize = 13.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        times.forEach { t -> FilterChip(selected = true, onClick = { if (times.size > 1) times = times - t }, label = { Text(fmtMinute(t) + " ✕") }) }
                        TextButton(onClick = {
                            TimePickerDialog(context, { _, h, m -> times = (times + (h * 60 + m)).distinct().sorted() }, 8, 0, true).show()
                        }) { Text("➕ وقت") }
                    }
                    Text("المدة:", fontSize = 13.sp)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0 to "مستمر", 5 to "5 أيام", 7 to "7 أيام", 14 to "14 يوم", 30 to "30 يوم").forEach { (d, l) ->
                            FilterChip(selected = days == d, onClick = { days = d }, label = { Text(l) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank() && times.isNotEmpty(), onClick = {
                    var endAt = 0L
                    if (days > 0) {
                        val c = Calendar.getInstance()
                        c.add(Calendar.DAY_OF_YEAR, days - 1)
                        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59)
                        endAt = c.timeInMillis
                    }
                    val old = meds.firstOrNull { it.id == em.id }
                    if (old != null) MedAlarms.cancelMed(context, old)
                    MedStore.save(context, em.copy(id = if (em.id == 0L) System.currentTimeMillis() else em.id, name = name.trim(), dose = dose.trim(), times = times.sorted(), endAt = endAt))
                    MedAlarms.rescheduleAll(context)
                    refresh()
                    edit = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("إلغاء") } }
        )
    }
    del?.let { dm ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف الدواء؟") },
            text = { Text(dm.name) },
            confirmButton = {
                Button(onClick = {
                    MedAlarms.cancelMed(context, dm)
                    MedStore.delete(context, dm.id)
                    refresh()
                    del = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text("إلغاء") } }
        )
    }
}
