package com.dani.assistant.presentation.realestate

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.dani.assistant.core.ai.GeminiAI
import com.dani.assistant.core.realestate.REClient
import com.dani.assistant.core.realestate.REProperty
import com.dani.assistant.core.realestate.RealEstateStore
import com.dani.assistant.domain.model.PriorityLevel
import com.dani.assistant.domain.model.ReminderType
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun fmt(ms: Long): String = SimpleDateFormat("EEE dd/MM HH:mm", Locale.getDefault()).format(Date(ms))

private fun waNumber(raw: String): String {
    var d = raw.filter { it.isDigit() }
    if (d.startsWith("00")) d = d.substring(2)
    else if (d.startsWith("0")) d = "213" + d.substring(1)
    return d
}

private fun propDetails(p: REProperty): String = buildString {
    append("النوع: ").append(p.deal).append(" ").append(p.kind).append('\n')
    if (p.title.isNotBlank()) append("العنوان/الوصف: ").append(p.title).append('\n')
    if (p.area.isNotBlank()) append("المنطقة: ").append(p.area).append('\n')
    if (p.price.isNotBlank()) append("السعر: ").append(p.price).append('\n')
    if (p.size.isNotBlank()) append("المساحة: ").append(p.size).append(" م²\n")
    if (p.rooms.isNotBlank()) append("عدد الغرف: ").append(p.rooms).append('\n')
    if (p.notes.isNotBlank()) append("ملاحظات: ").append(p.notes).append('\n')
}

/** متابعة العميل: تتحول لمهمة بتذكير (تظهر في المهام والتقويم والويدجت والملخص الصباحي). */
private suspend fun scheduleFollowUp(ctx: Context, c: REClient, due: Long): REClient {
    val repo = DaniApplication.instance.taskRepository
    val old = c.taskId
    if (old != null) { try { repo.deleteTaskById(old) } catch (e: Exception) { } }
    val title = "📞 متابعة: " + c.name
    val desc = buildString {
        if (c.phone.isNotBlank()) append(c.phone).append(" — ")
        append(c.wants)
        if (c.budget.isNotBlank()) append(" • ").append(c.budget)
        if (c.area.isNotBlank()) append(" • ").append(c.area)
        if (c.notes.isNotBlank()) append("\n").append(c.notes)
    }
    val id = repo.insertTask(Task(title = title, description = desc, priority = PriorityLevel.IMPORTANT, dueDate = due))
    if (due > System.currentTimeMillis()) repo.setTaskReminder(id, title, due, ReminderType.NOTIFICATION)
    val updated = c.copy(nextFollowUp = due, taskId = id)
    RealEstateStore.saveClient(ctx, updated)
    return updated
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealEstateScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var clients by remember { mutableStateOf(RealEstateStore.clients(context)) }
    var props by remember { mutableStateOf(RealEstateStore.properties(context)) }
    var editClient by remember { mutableStateOf<REClient?>(null) }
    var editProp by remember { mutableStateOf<REProperty?>(null) }
    var delClient by remember { mutableStateOf<REClient?>(null) }
    var delProp by remember { mutableStateOf<REProperty?>(null) }
    var adFor by remember { mutableStateOf<REProperty?>(null) }
    var adText by remember { mutableStateOf("") }
    var adBusy by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(RealEstateStore.contact(context)) }
    val ai = remember { GeminiAI() }

    fun refresh() {
        clients = RealEstateStore.clients(context)
        props = RealEstateStore.properties(context)
    }

    fun pickFollowUp(c: REClient) {
        val cal = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val t = Calendar.getInstance()
                t.set(y, m, d, h, min, 0)
                t.set(Calendar.MILLISECOND, 0)
                scope.launch {
                    try {
                        scheduleFollowUp(context, c, t.timeInMillis)
                        Toast.makeText(context, "✅ تسجلت المتابعة وتذكير في المهام", Toast.LENGTH_SHORT).show()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Toast.makeText(context, "ما قدرتش نسجل المتابعة", Toast.LENGTH_SHORT).show()
                    }
                    refresh()
                }
            }, 10, 0, true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    fun genAd(p: REProperty) {
        adFor = p
        adText = ""
        adBusy = true
        scope.launch {
            adText = try {
                ai.writeAd(propDetails(p), contact)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GeminiAI.friendlyError(e)
            }
            adBusy = false
        }
    }

    fun open(intent: Intent) {
        try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {
            Toast.makeText(context, "ما لقيتش تطبيق يفتح هذا", Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("🏠 العقار", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("العملاء (" + clients.size + ")") })
            FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("العقارات (" + props.size + ")") })
        }

        if (tab == 0) {
            Button(onClick = { editClient = REClient(id = 0, name = "") }) { Text("➕ عميل جديد") }
            if (clients.isEmpty()) Text("ما كاينش عملاء بعد.", color = MaterialTheme.colorScheme.outline)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clients, key = { it.id }) { c ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(c.status, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            if (c.phone.isNotBlank()) Text("📞 " + c.phone, fontSize = 13.sp)
                            val line = listOf(c.wants, c.budget, c.area).filter { it.isNotBlank() }.joinToString(" • ")
                            if (line.isNotBlank()) Text(line, fontSize = 13.sp)
                            if (c.notes.isNotBlank()) Text(c.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            val next = c.nextFollowUp
                            if (next != null) Text("⏰ متابعة: " + fmt(next), fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                if (c.phone.isNotBlank()) {
                                    TextButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone))) }) { Text("📞 اتصل") }
                                    TextButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + waNumber(c.phone)))) }) { Text("💬 واتساب") }
                                }
                                TextButton(onClick = { pickFollowUp(c) }) { Text("📅 متابعة") }
                                TextButton(onClick = {
                                    val i = RealEstateStore.clientStatuses.indexOf(c.status)
                                    RealEstateStore.saveClient(context, c.copy(status = RealEstateStore.clientStatuses[(i + 1) % RealEstateStore.clientStatuses.size]))
                                    refresh()
                                }) { Text("⏩ الحالة") }
                                TextButton(onClick = { editClient = c }) { Text("✏️") }
                                TextButton(onClick = { delClient = c }) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it; RealEstateStore.setContact(context, it) },
                label = { Text("سطر التواصل في الإعلان (رقم/اسم الصفحة)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { editProp = REProperty(id = 0, title = "") }) { Text("➕ عقار جديد") }
            if (props.isEmpty()) Text("ما كاينش عقارات بعد.", color = MaterialTheme.colorScheme.outline)
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(props, key = { it.id }) { p ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(p.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(p.status, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                                }
                            }
                            Text(listOf(p.deal, p.kind, p.area).filter { it.isNotBlank() }.joinToString(" • "), fontSize = 13.sp)
                            val l2 = listOfNotNull(
                                if (p.price.isNotBlank()) "💰 " + p.price else null,
                                if (p.size.isNotBlank()) p.size + " م²" else null,
                                if (p.rooms.isNotBlank()) p.rooms + " غرف" else null
                            ).joinToString(" • ")
                            if (l2.isNotBlank()) Text(l2, fontSize = 13.sp)
                            if (p.notes.isNotBlank()) Text(p.notes, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                TextButton(onClick = { genAd(p) }) { Text("✍️ إعلان") }
                                TextButton(onClick = {
                                    val i = RealEstateStore.propertyStatuses.indexOf(p.status)
                                    RealEstateStore.saveProperty(context, p.copy(status = RealEstateStore.propertyStatuses[(i + 1) % RealEstateStore.propertyStatuses.size]))
                                    refresh()
                                }) { Text("⏩ الحالة") }
                                TextButton(onClick = { editProp = p }) { Text("✏️") }
                                TextButton(onClick = { delProp = p }) { Text("🗑") }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- dialogs ----
    editClient?.let { ec ->
        ClientDialog(ec, onDismiss = { editClient = null }, onSave = { c ->
            RealEstateStore.saveClient(context, if (c.id == 0L) c.copy(id = System.currentTimeMillis()) else c)
            refresh()
            editClient = null
        })
    }
    editProp?.let { ep ->
        PropertyDialog(ep, onDismiss = { editProp = null }, onSave = { p ->
            RealEstateStore.saveProperty(context, if (p.id == 0L) p.copy(id = System.currentTimeMillis()) else p)
            refresh()
            editProp = null
        })
    }
    delClient?.let { dc ->
        AlertDialog(
            onDismissRequest = { delClient = null },
            title = { Text("حذف العميل؟") },
            text = { Text(dc.name + " (وتذكير المتابعة المرتبط به)") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val t = dc.taskId
                        if (t != null) { try { DaniApplication.instance.taskRepository.deleteTaskById(t) } catch (e: Exception) { } }
                        RealEstateStore.deleteClient(context, dc.id)
                        refresh()
                    }
                    delClient = null
                }) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { delClient = null }) { Text("إلغاء") } }
        )
    }
    delProp?.let { dp ->
        AlertDialog(
            onDismissRequest = { delProp = null },
            title = { Text("حذف العقار؟") },
            text = { Text(dp.title) },
            confirmButton = { Button(onClick = { RealEstateStore.deleteProperty(context, dp.id); refresh(); delProp = null }) { Text("حذف") } },
            dismissButton = { TextButton(onClick = { delProp = null }) { Text("إلغاء") } }
        )
    }
    adFor?.let { ap ->
        AlertDialog(
            onDismissRequest = { adFor = null },
            title = { Text("✍️ إعلان: " + ap.title.take(30)) },
            text = {
                if (adBusy) Text("⏳ نكتب الإعلان...")
                else OutlinedTextField(value = adText, onValueChange = { adText = it }, modifier = Modifier.fillMaxWidth().height(280.dp))
            },
            confirmButton = {
                Row {
                    TextButton(enabled = !adBusy && adText.isNotBlank(), onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("ad", adText))
                        Toast.makeText(context, "تم النسخ ✅", Toast.LENGTH_SHORT).show()
                    }) { Text("نسخ") }
                    TextButton(enabled = !adBusy && adText.isNotBlank(), onClick = {
                        open(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, adText)
                        }, "مشاركة الإعلان"))
                    }) { Text("مشاركة") }
                }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !adBusy, onClick = { genAd(ap) }) { Text("🔄") }
                    TextButton(onClick = { adFor = null }) { Text("إغلاق") }
                }
            }
        )
    }
}

@Composable
private fun ChipRow(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o -> ChipItem(o, o == selected) { onSelect(o) } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
private fun ClientDialog(initial: REClient, onDismiss: () -> Unit, onSave: (REClient) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var phone by remember { mutableStateOf(initial.phone) }
    var wants by remember { mutableStateOf(initial.wants) }
    var budget by remember { mutableStateOf(initial.budget) }
    var area by remember { mutableStateOf(initial.area) }
    var notes by remember { mutableStateOf(initial.notes) }
    var status by remember { mutableStateOf(initial.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "عميل جديد" else "تعديل العميل") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true)
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it }, label = { Text("الهاتف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                Text("يريد:", fontSize = 12.sp)
                ChipRow(RealEstateStore.wantsList, wants) { wants = it }
                OutlinedTextField(value = budget, onValueChange = { budget = it }, label = { Text("الميزانية") }, singleLine = true)
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("المنطقة المطلوبة") }, singleLine = true)
                Text("الحالة:", fontSize = 12.sp)
                ChipRow(RealEstateStore.clientStatuses, status) { status = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات") }, maxLines = 4)
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = {
                onSave(initial.copy(name = name.trim(), phone = phone.trim(), wants = wants, budget = budget.trim(), area = area.trim(), notes = notes.trim(), status = status))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PropertyDialog(initial: REProperty, onDismiss: () -> Unit, onSave: (REProperty) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var deal by remember { mutableStateOf(initial.deal) }
    var kind by remember { mutableStateOf(initial.kind) }
    var area by remember { mutableStateOf(initial.area) }
    var price by remember { mutableStateOf(initial.price) }
    var size by remember { mutableStateOf(initial.size) }
    var rooms by remember { mutableStateOf(initial.rooms) }
    var notes by remember { mutableStateOf(initial.notes) }
    var status by remember { mutableStateOf(initial.status) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.id == 0L) "عقار جديد" else "تعديل العقار") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("العنوان (مثال: شقة F3 قرب الترامواي)") })
                ChipRow(RealEstateStore.deals, deal) { deal = it }
                ChipRow(RealEstateStore.kinds, kind) { kind = it }
                OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("المنطقة / الولاية") }, singleLine = true)
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("السعر") }, singleLine = true)
                OutlinedTextField(
                    value = size, onValueChange = { size = it }, label = { Text("المساحة (م²)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = rooms, onValueChange = { rooms = it }, label = { Text("عدد الغرف") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("الحالة:", fontSize = 12.sp)
                ChipRow(RealEstateStore.propertyStatuses, status) { status = it }
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("ملاحظات (طابق، عقد، تجهيزات...)") }, maxLines = 4)
            }
        },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = {
                onSave(initial.copy(title = title.trim(), deal = deal, kind = kind, area = area.trim(), price = price.trim(), size = size.trim(), rooms = rooms.trim(), notes = notes.trim(), status = status))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
