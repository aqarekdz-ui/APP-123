package com.dani.assistant.presentation.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.dani.assistant.core.habits.Habit
import com.dani.assistant.core.habits.HabitStats
import com.dani.assistant.core.habits.HabitStore
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private val headerLetters = listOf("ح", "ن", "ث", "ر", "خ", "ج", "س") // الأحد..السبت

private fun createdDate(h: Habit): LocalDate =
    Instant.ofEpochMilli(h.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()

@Composable
fun HabitStatsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val habits = remember { HabitStore.list(context) }
    val today = LocalDate.now()
    val nowYm = YearMonth.from(today)
    var ym by remember { mutableStateOf(nowYm) }

    val rates30 = habits.map { HabitStats.rate(it.days, createdDate(it), today, 30) }
    val overall = HabitStats.overall(rates30)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("📊 إحصائيات العادات", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            TextButton(onClick = onBack) { Text("رجوع") }
        }
        if (habits.isEmpty()) {
            Text("ما كاينش عادات بعد. زيد عادة وتابعها باش تطلع الإحصائيات.", color = MaterialTheme.colorScheme.outline)
        } else {
            Text("الالتزام العام (30 يوم): " + overall.percent + "%", fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { ym = ym.minusMonths(1) }) { Text("◀") }
                Text(ym.year.toString() + "/" + ym.monthValue.toString().padStart(2, '0'), fontWeight = FontWeight.Bold)
                TextButton(enabled = ym.isBefore(nowYm), onClick = { ym = ym.plusMonths(1) }) { Text("▶") }
            }
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(habits, key = { it.id }) { h ->
                    val r30 = HabitStats.rate(h.days, createdDate(h), today, 30)
                    val r7 = HabitStats.rate(h.days, createdDate(h), today, 7)
                    val cur = HabitStats.currentStreak(h.days, today)
                    val best = HabitStats.bestStreak(h.days)
                    val monthDone = HabitStats.monthCount(h.days, ym)
                    val grid = HabitStats.monthGrid(h.days, ym, today)
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(h.emoji + " " + h.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("🔥 حالية: " + cur + "   🏆 أفضل: " + best + "   📅 الشهر: " + monthDone, fontSize = 13.sp)
                            Text("آخر 7 أيام: " + r7.percent + "% (" + r7.done + "/" + r7.window + ")", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            Text("آخر 30 يوم: " + r30.percent + "% (" + r30.done + "/" + r30.window + ")", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                            LinearProgressIndicator(progress = { r30.percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                headerLetters.forEach { l ->
                                    Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                                        Text(l, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                            grid.chunked(7).forEach { week ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    week.forEach { c ->
                                        val bg = when {
                                            c.day == 0 -> MaterialTheme.colorScheme.surface
                                            c.done -> MaterialTheme.colorScheme.primary
                                            c.future -> MaterialTheme.colorScheme.surface
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                        val fg = if (c.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                        Box(
                                            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(6.dp)).background(bg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (c.day > 0) Text(c.day.toString(), fontSize = 11.sp, color = fg)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
