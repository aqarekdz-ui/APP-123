package com.dani.assistant.presentation.dashboard

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dani.assistant.core.progress.BrainProgressMath
import com.dani.assistant.domain.model.Task
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val UNIT = 100f          // الرأس مرسوم في مربع 100x100
private const val HEAD_SCALE = 0.0055f // نسبة حجم الرأس من قطر العنصر
private const val ORBIT_MS = 24000     // دورة كاملة 360° (بطيئة)

private fun headPath(closed: Boolean): Path = Path().apply {
    moveTo(40f, 92f)
    cubicTo(40f, 80f, 38f, 72f, 30f, 64f)
    cubicTo(20f, 54f, 18f, 38f, 28f, 24f)
    cubicTo(38f, 8f, 62f, 6f, 74f, 18f)
    cubicTo(80f, 24f, 81f, 34f, 80f, 42f)
    lineTo(90f, 56f)
    cubicTo(88f, 59f, 84f, 60f, 82f, 60f)
    cubicTo(83f, 64f, 81f, 66f, 80f, 68f)
    cubicTo(81f, 74f, 76f, 80f, 68f, 82f)
    cubicTo(64f, 83f, 62f, 86f, 62f, 92f)
    if (closed) close()
}

private fun brainFolds(): Path = Path().apply {
    moveTo(36f, 36f); quadraticBezierTo(42f, 28f, 48f, 35f); quadraticBezierTo(54f, 42f, 60f, 34f)
    moveTo(37f, 42f); quadraticBezierTo(45f, 48f, 53f, 43f); quadraticBezierTo(58f, 40f, 63f, 43f)
    moveTo(44f, 27f); quadraticBezierTo(50f, 22f, 56f, 27f)
    moveTo(50f, 24f); lineTo(50f, 46f)
}

@Composable
fun BrainProgress(tasks: List<Task>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val reduced = remember {
        try { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f } catch (e: Exception) { false }
    }
    val r = remember(tasks) { BrainProgressMath.compute(tasks, System.currentTimeMillis()) }
    val stage = BrainProgressMath.stage(r.percent, r.total)

    val ringMs = if (reduced) 0 else 900
    val progress by animateFloatAsState(r.percent / 100f, tween(ringMs, easing = FastOutSlowInEasing), label = "ring")
    val shown by animateIntAsState(r.percent, tween(ringMs, easing = FastOutSlowInEasing), label = "pct")

    val inf = rememberInfiniteTransition(label = "brain")
    val rot = inf.animateFloat(0f, 360f, infiniteRepeatable(tween(ORBIT_MS, easing = LinearEasing)), label = "rot")
    val breath = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val floatY = inf.animateFloat(-1f, 1f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")

    val kick = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(r.done, r.total) {
        if (first) {
            first = false
        } else if (!reduced) {
            kick.snapTo(1f)
            if (r.total > 0 && r.percent == 100) {
                burst.snapTo(1f)
                launch { burst.animateTo(0f, tween(1400)) }
            }
            kick.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val accent = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val headPath = remember { headPath(true) }
    val headLine = remember { headPath(false) }
    val folds = remember { brainFolds() }
    val desc = "تقدم مهام اليوم: " + r.percent + " بالمئة، " + r.done + " من " + r.total + ". " + stage.label

    Card(modifier = modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = desc }) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(128.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val s = size.minDimension
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val stroke = s * 0.065f
                    val inset = stroke / 2f + s * 0.03f
                    val arcSize = Size(s - 2f * inset, s - 2f * inset)
                    val arcTop = Offset(inset, inset)
                    val k = kick.value
                    val b = burst.value
                    val br = if (reduced) 0.5f else breath.value
                    val glow = (stage.glow * (0.6f + 0.4f * br) + 0.45f * k + 0.3f * b).coerceIn(0f, 1f)

                    drawArc(color = track, startAngle = -90f, sweepAngle = 360f, useCenter = false, topLeft = arcTop, size = arcSize, style = Stroke(stroke))
                    rotate(-90f, c) {
                        val sweep = 360f * progress
                        if (sweep > 0.5f) {
                            drawArc(brush = Brush.sweepGradient(listOf(primary, accent, primary), c), startAngle = 0f, sweepAngle = sweep, useCenter = false, topLeft = arcTop, size = arcSize, alpha = 0.22f * glow + 0.1f, style = Stroke(stroke * 2.3f, cap = StrokeCap.Round))
                            drawArc(brush = Brush.sweepGradient(listOf(primary, accent, primary), c), startAngle = 0f, sweepAngle = sweep, useCenter = false, topLeft = arcTop, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                    if (b > 0.01f) {
                        drawCircle(color = accent, radius = arcSize.width / 2f + s * 0.08f * (1f - b), center = c, alpha = 0.5f * b, style = Stroke(stroke * 0.5f))
                    }

                    // نقاط مدارية بطيئة (دوران 360°)
                    val orbitR = arcSize.width / 2f - stroke * 1.7f
                    val angle0 = if (reduced) 0f else rot.value
                    for (i in 0 until 3) {
                        val a = (angle0 + i * 120f) * (PI.toFloat() / 180f)
                        drawCircle(color = accent, radius = s * 0.012f, center = Offset(c.x + orbitR * cos(a), c.y + orbitR * sin(a)), alpha = 0.35f + 0.4f * glow)
                    }

                    // الرأس: حركة طفو + التفاف خفيف (يوهم الدوران) + نبضة عند التغيّر
                    val kk = s * HEAD_SCALE
                    val sway = 0.04f + 0.015f * stage.level
                    val angleRad = angle0 * (PI.toFloat() / 180f)
                    val sx = 1f - sway + sway * cos(angleRad)
                    val fy = if (reduced) 0f else floatY.value * s * 0.012f
                    withTransform({
                        translate(c.x - UNIT / 2f * kk, c.y - UNIT / 2f * kk + fy)
                        scale(kk, kk, Offset.Zero)
                        scale(sx, 1f + 0.05f * k + 0.04f * b, Offset(UNIT / 2f, UNIT / 2f))
                    }) {
                        drawPath(headPath, brush = Brush.verticalGradient(listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.02f)), startY = 8f, endY = 92f))
                        drawPath(headLine, brush = Brush.linearGradient(listOf(primary, accent), start = Offset(20f, 10f), end = Offset(90f, 92f)), style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        drawCircle(brush = Brush.radialGradient(listOf(accent.copy(alpha = glow), Color.Transparent), center = Offset(50f, 35f), radius = 26f), radius = 26f, center = Offset(50f, 35f))
                        drawOval(color = accent.copy(alpha = 0.55f + 0.45f * glow), topLeft = Offset(31f, 22f), size = Size(38f, 28f), style = Stroke(1.4f))
                        drawPath(folds, color = accent.copy(alpha = 0.5f + 0.5f * glow), style = Stroke(1.1f, cap = StrokeCap.Round))
                        drawCircle(color = Color.White.copy(alpha = 0.35f + 0.6f * glow), radius = 1.6f + 1.2f * (k + b).coerceAtMost(1f), center = Offset(50f, 35f))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(shown.toString() + "%", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("المهام المنجزة اليوم", fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
                Text(if (r.total == 0) "0 من 0" else r.done.toString() + " من " + r.total, fontSize = 13.sp)
                Text(stage.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
