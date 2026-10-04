package com.dani.assistant.presentation.money

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.dani.assistant.core.money.MoneyEntry
import com.dani.assistant.core.money.MoneyReports
import com.dani.assistant.core.money.MoneyStore
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** زرّين: 📊 رسوم و📤 CSV للشهر المعروض. */
@Composable
fun MoneyToolsRow(entries: List<MoneyEntry>, start: Long, end: Long, title: String) {
    val context = LocalContext.current
    var showCharts by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { showCharts = true }) { Text("📊 رسوم") }
    OutlinedButton(onClick = {
        try {
            val dir = File(context.cacheDir, "exports")
            dir.mkdirs()
            val f = File(dir, "dani_money_" + SimpleDateFormat("yyyy-MM", Locale.US).format(Date(start)) + ".csv")
            f.writeText(MoneyReports.csv(entries, start, end), Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "com.dani.assistant.fileprovider", f)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "تصدير CSV"))
        } catch (e: Exception) {
            Toast.makeText(context, "ما قدرتش نصدّر: " + (e.message ?: "خطأ"), Toast.LENGTH_LONG).show()
        }
    }) { Text("📤 CSV") }

    if (showCharts) {
        val cats = MoneyReports.categoryTotals(entries, start, end)
        val months = MoneyReports.monthTotals(entries, 6, start)
        AlertDialog(
            onDismissRequest = { showCharts = false },
            confirmButton = { TextButton(onClick = { showCharts = false }) { Text("سكّر") } },
            title = { Text("📊 " + title) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("المصاريف حسب الفئة", fontWeight = FontWeight.Bold)
                    if (cats.isEmpty()) Text("ما كاينش مصاريف في هذا الشهر.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    val top = cats.maxOfOrNull { it.second } ?: 1L
                    val total = cats.sumOf { it.second }.coerceAtLeast(1L)
                    cats.forEach { (name, v) ->
                        Column {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(name, fontSize = 13.sp)
                                Text(MoneyStore.fmt(v) + " (" + (v * 100 / total) + "%)", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            }
                            Box(modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                Box(modifier = Modifier.fillMaxWidth((v.toFloat() / top).coerceIn(0.02f, 1f)).fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                    Text("آخر 6 أشهر (دخل / مصروف)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                    val mx = months.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1L) ?: 1L
                    Row(modifier = Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                        months.forEach { m ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.height(100.dp)) {
                                    Box(modifier = Modifier.width(10.dp).height((100f * m.income / mx).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.primary))
                                    Box(modifier = Modifier.width(10.dp).height((100f * m.expense / mx).dp.coerceAtLeast(2.dp)).background(MaterialTheme.colorScheme.error))
                                }
                                Text(m.month.toString(), fontSize = 11.sp)
                            }
                        }
                    }
                    Text("🟦 دخل   🟥 مصروف", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        )
    }
}
