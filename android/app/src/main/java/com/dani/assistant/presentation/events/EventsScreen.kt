package com.dani.assistant.presentation.events

import android.app.DatePickerDialog
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
import com.dani.assistant.core.events.EventAlarms
import com.dani.assistant.core.events.EventStore
import com.dani.assistant.core.events.LifeEvent
import com.dani.assistant.core.events.Upcoming
import com.dani.assistant.core.events.daysText
import com.dani.assistant.core.events.eventEmoji
import com.dani.assistant.core.events.fmtEventDate
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var ups by remember { mutableStateOf(EventStore.upcoming(context)) }
    var gone by remember { mutableStateOf(EventStore.past(context)) }
    var edit by remember { mutableStateOf<LifeEvent?>(null) }
    var del by remember { mutableStateOf<LifeEvent?>(null) }

    fun refresh() { ups = EventStore.upcoming(context); gone = EventStore.past(context) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🎂 المناسبات", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Button(onClick = {
            val c = Calendar.getInstance()
            edit = LifeEvent(id = 0, name = "", kind = "birthday", day = c.get(Calendar.DAY_OF_MONTH), month = c.get(Calendar.MONTH) + 1, year = 0, yearly = true)
        }) { Text("➕ مناسبة جديدة") }

        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item { Text("القادمة", fontWeight = FontWeight.Bold) }
            if (ups.isEmpty()) item { Text("ما كاينش مناسبات قادمة.", color = MaterialTheme.colorScheme.outline) }
            items(ups, key = { it.event.id }) { u: Upcoming ->
                val e = u.event
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(eventEmoji(e) + " " + e.name, fontWeight = FontWeight.Bold)
                        Text(
                            fmtEventDate(e) + (if (e.yearly) " • 🔁 كل سنة" else "") + " • " + daysText(u.daysLeft) + (if (u.age > 0) " • " + u.age + " سنة" else ""),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
                        )
                        Row {
                            TextButton(onClick = { edit = e }) { Text("✏️") }
                            TextButton(onClick = { del = e }) { Text("🗑") }
                        }
                    }
                }
            }
            if (gone.isNotEmpty()) {
                item { Text("فاتت", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)) }
                items(gone, key = { "g" + it.id }) { e: LifeEvent ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(eventEmoji(e) + " " + e.name + " — " + fmtEventDate(e), modifier = Modifier.weight(1f), fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                            TextButton(onClick = { del = e }) { Text("🗑") }
                        }
                    }
                }
            }
        }
    }

    edit?.let { em ->
        val curYear = Calendar.getInstance().get(Calendar.YEAR)
        var name by remember(em.id) { mutableStateOf(em.name) }
        var kind by remember(em.id) { mutableStateOf(em.kind) }
        var yearly by remember(em.id) { mutableStateOf(em.yearly) }
        var dd by remember(em.id) { mutableStateOf(em.day) }
        var mm by remember(em.id) { mutableStateOf(em.month) }
        var yy by remember(em.id) { mutableStateOf(if (em.year > 0) em.year else curYear) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text(if (em.id == 0L) "مناسبة جديدة" else "تعديل المناسبة") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم / المناسبة") }, singleLine = true)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = kind == "birthday", onClick = { kind = "birthday" }, label = { Text("🎂 عيد ميلاد") })
                        FilterChip(selected = kind == "occasion", onClick = { kind = "occasion" }, label = { Text("🎉 مناسبة") })
                    }
                    TextButton(onClick = {
                        DatePickerDialog(context, { _, y, m, d -> yy = y; mm = m + 1; dd = d }, yy, mm - 1, dd).show()
                    }) { Text("📅 " + String.format(Locale.US, "%02d/%02d/%d", dd, mm, yy)) }
                    FilterChip(selected = yearly, onClick = { yearly = !yearly }, label = { Text("🔁 تتكرر كل سنة") })
                    Text(
                        if (yearly) "السنة تستعمل لحساب العمر (سنة الميلاد). إذا ما تعرفهاش خلي سنة اليوم." else "مناسبة مرة وحدة في هذا التاريخ.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {
                Button(enabled = name.isNotBlank(), onClick = {
                    val saved = em.copy(
                        id = if (em.id == 0L) System.currentTimeMillis() else em.id,
                        name = name.trim(), kind = kind, day = dd, month = mm,
                        year = if (yearly && yy >= curYear) 0 else yy,
                        yearly = yearly
                    )
                    EventStore.save(context, saved)
                    EventAlarms.schedule(context, saved)
                    refresh()
                    edit = null
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("إلغاء") } }
        )
    }

    del?.let { de ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("حذف المناسبة؟") },
            text = { Text(de.name) },
            confirmButton = {
                Button(onClick = {
                    EventAlarms.cancel(context, de)
                    EventStore.delete(context, de.id)
                    refresh()
                    del = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { del = null }) { Text("إلغاء") } }
        )
    }
}
