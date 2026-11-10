package com.qlots.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.data.AssetAllocation
import com.qlots.app.data.HistorySnapshot

private val ChartColors = listOf(
    QlotsColors.Green, QlotsColors.Blue, QlotsColors.Gold, QlotsColors.Purple,
    Color(0xFFE07B54), Color(0xFF5BA4A4), Color(0xFFC8A96E), Color(0xFF7B9E6B),
    Color(0xFFA67B5B), Color(0xFF6B8FA6),
)

@Composable
fun HealthGauge(score: Int, modifier: Modifier = Modifier) {
    val safeScore = score.coerceIn(0, 100)
    Box(modifier = modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(84.dp)) {
            val stroke = 7.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Color(0xFF345E4A),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = QlotsColors.Lime,
                startAngle = 135f,
                sweepAngle = 270f * safeScore / 100f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$safeScore", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            Text("/100", color = Color(0xFFB9D3C4), fontSize = 9.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun DonutChart(data: List<AssetAllocation>, modifier: Modifier = Modifier, sizeDp: androidx.compose.ui.unit.Dp = 126.dp, centerTop: String = "TOTAL", centerMain: String = "${data.size}", centerBottom: String = if (data.size == 1) "asset type" else "asset types") {
    Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp)) {
            val total = data.sumOf { it.value.coerceAtLeast(0.0) }
            if (total <= 0.0) return@Canvas
            var angle = -90f
            data.forEachIndexed { index, item ->
                val sweep = (item.value.coerceAtLeast(0.0) / total * 360.0).toFloat()
                if (sweep > 0.25f) {
                    drawArc(
                        color = ChartColors[index % ChartColors.size],
                        startAngle = angle,
                        sweepAngle = (sweep - 2.2f).coerceAtLeast(0.4f),
                        useCenter = true,
                    )
                }
                angle += sweep
            }
            drawCircle(color = QlotsColors.Card, radius = size.minDimension * 0.33f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = QlotsColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(centerMain, color = QlotsColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(centerBottom, color = QlotsColors.Muted, fontSize = 8.sp)
        }
    }
}

@Composable
fun DonutLegendDot(index: Int, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(8.dp).background(ChartColors[index % ChartColors.size], RoundedCornerShape(50)))
}

@Composable
fun Sparkline(data: List<HistorySnapshot>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(52.dp)) {
        if (data.size < 2) return@Canvas
        val values = data.map { it.netWorth }
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        val range = (max - min).takeIf { it > 0.0 } ?: 1.0
        val pad = 5.dp.toPx()
        val usableHeight = size.height - 2 * pad
        val points = values.mapIndexed { index, value ->
            Offset(
                x = if (values.size == 1) size.width / 2 else size.width * index / (values.size - 1),
                y = size.height - pad - (((value - min) / range).toFloat() * usableHeight),
            )
        }
        val path = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, QlotsColors.Lime, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        points.lastOrNull()?.let { drawCircle(QlotsColors.Lime, radius = 4.dp.toPx(), center = it) }
    }
}

@Composable
fun CashFlowBar(income: Double, expenses: Double, modifier: Modifier = Modifier) {
    val denominator = (income.coerceAtLeast(0.0) + expenses.coerceAtLeast(0.0)).takeIf { it > 0.0 } ?: 1.0
    Row(
        modifier = modifier.fillMaxWidth().height(9.dp).background(Color(0xFFEEF1EB), RoundedCornerShape(50)),
        horizontalArrangement = Arrangement.Start,
    ) {
        Box(Modifier.fillMaxWidth((income.coerceAtLeast(0.0) / denominator).toFloat().coerceIn(0.02f, 1f)).height(9.dp).background(QlotsColors.Green, RoundedCornerShape(50)))
    }
}

@Composable
fun SmallProgressBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(7.dp).background(Color(0xFFEEF1EB), RoundedCornerShape(50))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(7.dp).background(color, RoundedCornerShape(50)))
    }
}

// ---------------------------------------------------------------------------------------------
// Line chart with touch selection, used for every timeline / projection graph.
// ---------------------------------------------------------------------------------------------
data class LineSeries(
    val name: String,
    val color: Color,
    val points: List<Pair<Double, Double>>,
    val dashed: Boolean = false,
    val fill: Boolean = false,
    val strokeDp: Float = 2.5f,
    val dots: Boolean = false,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 190.dp,
    xFormatter: (Double) -> String = ::axisDate,
    tooltipDate: (Double) -> String = ::axisDateFull,
    yFormatter: (Double) -> String = ::axisMoney,
    markerX: Double? = null,
) {
    val visible = series.filter { it.points.isNotEmpty() }
    val all = visible.flatMap { it.points }
    if (all.size < 2) return
    val minX = all.minOf { it.first }
    val maxX = all.maxOf { it.first }.let { if (it == minX) minX + 1 else it }
    var minY = all.minOf { it.second }
    var maxY = all.maxOf { it.second }
    if (minY > 0) minY = (minY - (maxY - minY) * 0.12).coerceAtLeast(0.0)
    if (maxY == minY) maxY = minY + 1
    val padY = (maxY - minY) * 0.06
    maxY += padY
    if (minY < 0) minY -= padY
    var selected by remember { mutableStateOf<Double?>(null) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = QlotsColors.Muted, fontSize = 9.sp)

    Column(modifier) {
        val sel = selected
        if (sel == null) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                visible.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(width = 14.dp, height = 3.dp).background(s.color, RoundedCornerShape(2.dp)))
                        Text(s.name, color = QlotsColors.Muted, fontSize = 10.sp)
                    }
                }
            }
            Text("Touch the chart to read values", color = QlotsColors.Muted.copy(alpha = 0.7f), fontSize = 9.sp, modifier = Modifier.padding(top = 2.dp))
        } else {
            Text(tooltipDate(sel), color = QlotsColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                visible.forEach { s ->
                    val nearest = s.points.minByOrNull { kotlin.math.abs(it.first - sel) }
                    if (nearest != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.size(7.dp).background(s.color, RoundedCornerShape(50)))
                            Text("${s.name} ${yFormatter(nearest.second)}", color = QlotsColors.Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        val leftPad = with(androidx.compose.ui.platform.LocalDensity.current) { 46.dp.toPx() }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(top = 6.dp)
                .pointerInput(minX, maxX) {
                    detectTapGestures(onTap = { o ->
                        val w = size.width - leftPad
                        selected = (minX + ((o.x - leftPad) / w).coerceIn(0f, 1f) * (maxX - minX))
                    })
                }
                .pointerInput(minX, maxX) {
                    detectHorizontalDragGestures(
                        onDragStart = { o ->
                            val w = size.width - leftPad
                            selected = (minX + ((o.x - leftPad) / w).coerceIn(0f, 1f) * (maxX - minX))
                        },
                        onHorizontalDrag = { change, _ ->
                            val w = size.width - leftPad
                            selected = (minX + ((change.position.x - leftPad) / w).coerceIn(0f, 1f) * (maxX - minX))
                        },
                    )
                },
        ) {
            val bottomPad = 16.dp.toPx()
            val plotW = size.width - leftPad
            val plotH = size.height - bottomPad
            fun px(x: Double) = leftPad + ((x - minX) / (maxX - minX)).toFloat() * plotW
            fun py(y: Double) = plotH - ((y - minY) / (maxY - minY)).toFloat() * plotH
            // grid + y labels
            for (i in 0..3) {
                val value = minY + (maxY - minY) * i / 3
                val y = py(value)
                drawLine(Color(0xFFE9EEE7), Offset(leftPad, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(yFormatter(value), labelStyle)
                drawText(layout, topLeft = Offset(0f, y - layout.size.height / 2f))
            }
            if (minY < 0 && maxY > 0) drawLine(Color(0xFFB8C2B8), Offset(leftPad, py(0.0)), Offset(size.width, py(0.0)), strokeWidth = 1.2.dp.toPx())
            // x labels
            val ticks = 4
            for (i in 0..ticks) {
                val xv = minX + (maxX - minX) * i / ticks
                val layout = measurer.measure(xFormatter(xv), labelStyle)
                val x = (px(xv) - layout.size.width / 2f).coerceIn(leftPad - 8.dp.toPx(), size.width - layout.size.width.toFloat())
                drawText(layout, topLeft = Offset(x, plotH + 3.dp.toPx()))
            }
            markerX?.let { mx ->
                if (mx in minX..maxX) drawLine(Color(0xFFB8C2B8), Offset(px(mx), 0f), Offset(px(mx), plotH), strokeWidth = 1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            visible.forEach { s ->
                val pts = s.points.sortedBy { it.first }.map { Offset(px(it.first), py(it.second)) }
                if (pts.size >= 2) {
                    val path = Path().apply { moveTo(pts.first().x, pts.first().y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                    if (s.fill) {
                        val area = Path().apply { addPath(path); lineTo(pts.last().x, plotH); lineTo(pts.first().x, plotH); close() }
                        drawPath(area, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(s.color.copy(alpha = 0.22f), s.color.copy(alpha = 0.02f)), startY = 0f, endY = plotH))
                    }
                    drawPath(
                        path, s.color,
                        style = Stroke(
                            width = s.strokeDp.dp.toPx(), cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round,
                            pathEffect = if (s.dashed) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)) else null,
                        ),
                    )
                } else if (pts.size == 1) drawCircle(s.color, 4.dp.toPx(), pts.first())
                if (s.dots) pts.forEach { drawCircle(s.color, 3.dp.toPx(), it) }
            }
            selected?.let { sel ->
                val x = px(sel.coerceIn(minX, maxX))
                drawLine(Color(0xFF728078), Offset(x, 0f), Offset(x, plotH), strokeWidth = 1.dp.toPx())
                visible.forEach { s ->
                    s.points.minByOrNull { kotlin.math.abs(it.first - sel) }?.let { p ->
                        drawCircle(Color.White, 5.5.dp.toPx(), Offset(px(p.first), py(p.second)))
                        drawCircle(s.color, 4.dp.toPx(), Offset(px(p.first), py(p.second)))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Ring used for goal / debt progress.
// ---------------------------------------------------------------------------------------------
@Composable
fun ProgressRing(progress: Float, modifier: Modifier = Modifier, sizeDp: androidx.compose.ui.unit.Dp = 64.dp, color: Color = QlotsColors.Green, label: String? = null, trackColor: Color = Color(0xFFE6ECE4), labelColor: Color = QlotsColors.Ink) {
    Box(modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(sizeDp)) {
            val stroke = 7.dp.toPx()
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(trackColor, -90f, 360f, false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress.coerceIn(0f, 1f), false, Offset(stroke / 2, stroke / 2), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(label ?: "${(progress * 100).toInt()}%", color = labelColor, fontSize = (sizeDp.value / 4.6f).sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ---------------------------------------------------------------------------------------------
// Monthly income vs expenses bars with a surplus line; spikes (seasonal / annual bills) are amber.
// ---------------------------------------------------------------------------------------------
@Composable
fun CashflowBarChart(months: List<com.qlots.app.data.CashflowMonth>, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 190.dp) {
    if (months.isEmpty()) return
    var selected by remember { mutableStateOf<Int?>(months.indexOfFirst { it.isCurrent }.takeIf { it >= 0 }) }
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = QlotsColors.Muted, fontSize = 9.sp)
    val maxValue = months.maxOf { maxOf(it.income, it.expenses + it.emi) }.coerceAtLeast(1.0) * 1.12
    val minValue = minOf(0.0, months.minOf { it.surplus }) * 1.1
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LegendDot(QlotsColors.Green, "Income"); LegendDot(QlotsColors.Red, "Expenses"); LegendDot(Color(0xFFE0A33A), "Seasonal peak"); LegendDot(QlotsColors.Blue, "Surplus")
        }
        val leftPad = with(androidx.compose.ui.platform.LocalDensity.current) { 42.dp.toPx() }
        Canvas(
            Modifier.fillMaxWidth().height(height).padding(top = 8.dp).pointerInput(months) {
                detectTapGestures { o ->
                    val slot = (size.width - leftPad) / months.size
                    selected = ((o.x - leftPad) / slot).toInt().coerceIn(0, months.size - 1)
                }
            },
        ) {
            val bottomPad = 16.dp.toPx()
            val plotH = size.height - bottomPad
            val range = maxValue - minValue
            fun py(v: Double) = plotH - ((v - minValue) / range).toFloat() * plotH
            for (i in 0..3) {
                val value = minValue + range * i / 3
                drawLine(Color(0xFFE9EEE7), Offset(leftPad, py(value)), Offset(size.width, py(value)), strokeWidth = 1.dp.toPx())
                val layout = measurer.measure(axisMoney(value), labelStyle)
                drawText(layout, topLeft = Offset(0f, py(value) - layout.size.height / 2f))
            }
            val slot = (size.width - leftPad) / months.size
            val barW = slot * 0.3f
            val surplusPts = mutableListOf<Offset>()
            months.forEachIndexed { i, m ->
                val cx = leftPad + slot * i + slot / 2
                val alpha = if (m.isFuture) 0.55f else 1f
                if (selected == i) drawRoundRect(Color(0xFFEFF4EC), Offset(leftPad + slot * i + 1, 0f), Size(slot - 2, plotH), androidx.compose.ui.geometry.CornerRadius(8f))
                drawRoundRect(QlotsColors.Green.copy(alpha = alpha), Offset(cx - barW - 1, py(m.income)), Size(barW, plotH - py(m.income)), androidx.compose.ui.geometry.CornerRadius(5f))
                val expenseColor = if (m.spike) Color(0xFFE0A33A) else QlotsColors.Red
                val expenseTop = py(m.expenses + m.emi)
                drawRoundRect(expenseColor.copy(alpha = alpha), Offset(cx + 1, expenseTop), Size(barW, plotH - expenseTop), androidx.compose.ui.geometry.CornerRadius(5f))
                surplusPts += Offset(cx, py(m.surplus))
                val layout = measurer.measure(m.label, labelStyle.copy(fontWeight = if (m.isCurrent) FontWeight.Bold else FontWeight.Normal, color = if (m.isCurrent) QlotsColors.Ink else QlotsColors.Muted))
                drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, plotH + 3.dp.toPx()))
            }
            if (surplusPts.size > 1) {
                val path = Path().apply { moveTo(surplusPts.first().x, surplusPts.first().y); surplusPts.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, QlotsColors.Blue, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
            surplusPts.forEach { drawCircle(Color.White, 4.5.dp.toPx(), it); drawCircle(QlotsColors.Blue, 3.dp.toPx(), it) }
        }
        selected?.let { idx ->
            val m = months[idx]
            Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFF4F7F2), RoundedCornerShape(12.dp)).padding(11.dp)) {
                Text("${m.label} ${m.year}${if (m.isFuture) " · scheduled" else if (m.isCurrent) " · this month" else ""}", color = QlotsColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Income ${money(m.income, true)}", color = QlotsColors.Green, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("Expenses ${money(m.expenses, true)}", color = QlotsColors.Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    Text("EMI ${money(m.emi, true)}", color = QlotsColors.Muted, fontSize = 10.sp)
                    Text("Left ${money(m.surplus, true)}", color = if (m.surplus >= 0) QlotsColors.Blue else QlotsColors.Red, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
                if (m.seasonal.isNotEmpty()) Text("Includes: ${m.seasonal.joinToString()}", color = Color(0xFF9A6B14), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
        Text(text, color = QlotsColors.Muted, fontSize = 10.sp)
    }
}

/** Compact column chart used for the savings-rate trend. */
@Composable
fun RateBars(points: List<com.qlots.app.data.SavingsPoint>, benchmark: Double, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 120.dp) {
    if (points.isEmpty()) return
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val labelStyle = androidx.compose.ui.text.TextStyle(color = QlotsColors.Muted, fontSize = 9.sp)
    val top = maxOf(points.maxOf { it.rate }, benchmark, 10.0) * 1.15
    val bottom = minOf(0.0, points.minOf { it.rate })
    Canvas(modifier.fillMaxWidth().height(height)) {
        val bottomPad = 16.dp.toPx()
        val plotH = size.height - bottomPad
        fun py(v: Double) = plotH - ((v - bottom) / (top - bottom)).toFloat() * plotH
        val slot = size.width / points.size
        val barW = (slot * 0.5f).coerceAtMost(26.dp.toPx())
        drawLine(Color(0xFFB8C2B8), Offset(0f, py(benchmark)), Offset(size.width, py(benchmark)), 1.2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        points.forEachIndexed { i, p ->
            val cx = slot * i + slot / 2
            val y = py(p.rate.coerceAtLeast(0.0))
            drawRoundRect(if (p.rate >= benchmark) QlotsColors.Green else QlotsColors.Blue, Offset(cx - barW / 2, y), Size(barW, (py(0.0) - y).coerceAtLeast(2f)), androidx.compose.ui.geometry.CornerRadius(6f))
            val v = measurer.measure("${p.rate.toInt()}%", labelStyle.copy(color = QlotsColors.Ink, fontWeight = FontWeight.Bold))
            drawText(v, topLeft = Offset(cx - v.size.width / 2f, y - v.size.height - 1.dp.toPx()))
            val l = measurer.measure(p.label.substringBefore(' '), labelStyle)
            drawText(l, topLeft = Offset(cx - l.size.width / 2f, plotH + 3.dp.toPx()))
        }
    }
}

fun chartColor(index: Int): Color = ChartColors[index % ChartColors.size]
