package com.qlots.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun DonutChart(data: List<AssetAllocation>, modifier: Modifier = Modifier, sizeDp: androidx.compose.ui.unit.Dp = 126.dp) {
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
            drawCircle(color = QlotsColors.Card, radius = size.minDimension * 0.28f)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TOTAL", color = QlotsColors.Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${data.size}", color = QlotsColors.Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(if (data.size == 1) "asset type" else "asset types", color = QlotsColors.Muted, fontSize = 8.sp)
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
