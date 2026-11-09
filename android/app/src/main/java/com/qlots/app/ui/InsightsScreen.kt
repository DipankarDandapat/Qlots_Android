package com.qlots.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.data.AssetAllocation
import com.qlots.app.data.DebtData
import com.qlots.app.data.GoalItem
import com.qlots.app.data.HistorySnapshot

enum class InsightSection(val title: String) {
    NETWORTH("Net worth"), PROJECTION("Projection"), CASHFLOW("Cash flow"), ALLOCATION("Allocation"), DEBT("Debt"), GOALS("Goals"),
}

@Composable
fun StatTile(label: String, value: String, color: Color = QlotsColors.Ink, modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFFF4F7F2), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 3.dp), maxLines = 2)
    }
}

@Composable
fun Badge(text: String, color: Color, background: Color, modifier: Modifier = Modifier) {
    Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = modifier.background(background, RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 4.dp), maxLines = 1)
}

@Composable
fun CardTitle(title: String, subtitle: String? = null) {
    Text(title, color = QlotsColors.Deep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    if (subtitle != null) Text(subtitle, color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 3.dp, bottom = 10.dp))
}

@Composable
fun Footnote(text: String, modifier: Modifier = Modifier) {
    Text(text, color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = modifier.padding(top = 8.dp))
}

private fun List<HistorySnapshot>.inRange(days: Int?): List<HistorySnapshot> {
    if (days == null || isEmpty()) return this
    val cutoff = epochDay(last().date) - days
    return filter { epochDay(it.date) >= cutoff }.let { if (it.size >= 2) it else takeLast(2) }
}

@Composable
fun InsightsScreen(
    state: QlotsUiState,
    onRecordSnapshot: () -> Unit,
    onDeleteSnapshot: (Int) -> Unit,
    onSelectPlan: (Int) -> Unit,
    onSavePlan: () -> Unit,
    onDeletePlan: (Int) -> Unit,
    onAddGoal: () -> Unit,
    onEditGoal: (GoalItem) -> Unit,
    onProgress: (GoalItem) -> Unit,
    onDeleteGoal: (GoalItem) -> Unit,
    onConsumeSection: () -> Unit,
) {
    var section by rememberSaveable { mutableStateOf(InsightSection.NETWORTH.name) }
    LaunchedEffect(state.sectionRequest) {
        val requested = state.sectionRequest
        if (requested != null && InsightSection.entries.any { it.name == requested }) section = requested
        if (requested != null) onConsumeSection()
    }
    val current = InsightSection.valueOf(section)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
            Text("Insights", color = QlotsColors.Deep, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
            Text("Charts built from your records and saved snapshots.", color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InsightSection.entries.forEach { QPill(it.title, current == it, { section = it.name }) }
        }
        when (current) {
            InsightSection.NETWORTH -> NetWorthSection(state, onRecordSnapshot, onDeleteSnapshot)
            InsightSection.PROJECTION -> ProjectionSection(state, onSelectPlan, onSavePlan, onDeletePlan)
            InsightSection.CASHFLOW -> CashflowSection(state)
            InsightSection.ALLOCATION -> AllocationSection(state)
            InsightSection.DEBT -> DebtSection(state)
            InsightSection.GOALS -> GoalsSection(state, onAddGoal, onEditGoal, onProgress, onDeleteGoal)
        }
        Text("Charts use the figures you entered and snapshots saved in your account. Projections are estimates, not guarantees.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 16.dp))
    }
}

// ------------------------------------------------------------------------------------------ net worth
@Composable
private fun NetWorthSection(state: QlotsUiState, onRecord: () -> Unit, onDelete: (Int) -> Unit) {
    var range by rememberSaveable { mutableStateOf("1Y") }
    val days = when (range) { "3M" -> 92; "6M" -> 183; "1Y" -> 366; else -> null }
    val history = state.history.inRange(days)
    var pendingDelete by remember { mutableStateOf<HistorySnapshot?>(null) }

    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("3M", "6M", "1Y", "All").forEach { QPill(it, range == it, { range = it }) }
    }
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Net-worth timeline", "Assets minus liabilities on each saved snapshot.")
        if (history.size < 2) {
            EmptyState("Your timeline starts here", "Record a snapshot today and another later to see your progress.")
        } else {
            val first = history.first(); val last = history.last()
            val delta = last.netWorth - first.netWorth
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Now", money(last.netWorth, true), QlotsColors.Deep, Modifier.weight(1f))
                StatTile("Change · ${range}", (if (delta >= 0) "+" else "") + money(delta, true), if (delta >= 0) QlotsColors.Green else QlotsColors.Red, Modifier.weight(1f))
                StatTile("Growth", if (first.netWorth != 0.0) pct(delta / kotlin.math.abs(first.netWorth) * 100) else "—", if (delta >= 0) QlotsColors.Green else QlotsColors.Red, Modifier.weight(1f))
            }
            LineChart(
                listOf(LineSeries("Net worth", QlotsColors.Green, history.map { epochDay(it.date) to it.netWorth }, fill = true)),
                height = 190.dp,
            )
        }
        QButton("＋  Record a snapshot", onRecord, secondary = true, modifier = Modifier.padding(top = 12.dp))
        Footnote("Net worth is refreshed automatically every night at 12:00 AM. Manual snapshots let you add past dates.")
    }

    val withBreakdown = history.filter { it.assets != null && it.liabilities != null }
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Assets, liabilities & net worth", "Is wealth growing, is debt rising — or both?")
        if (withBreakdown.size < 2) {
            EmptyState("Not enough detail yet", "Snapshots saved from this version onward record assets and liabilities separately.")
        } else {
            val a = withBreakdown.first(); val b = withBreakdown.last()
            val assetsDelta = b.assets!! - a.assets!!
            val debtDelta = b.liabilities!! - a.liabilities!!
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Assets", (if (assetsDelta >= 0) "+" else "") + money(assetsDelta, true), if (assetsDelta >= 0) QlotsColors.Green else QlotsColors.Red, Modifier.weight(1f))
                StatTile("Liabilities", (if (debtDelta > 0) "+" else "") + money(debtDelta, true), if (debtDelta <= 0) QlotsColors.Green else QlotsColors.Red, Modifier.weight(1f))
                StatTile("Net worth", (if (b.netWorth - a.netWorth >= 0) "+" else "") + money(b.netWorth - a.netWorth, true), QlotsColors.Deep, Modifier.weight(1f))
            }
            LineChart(
                listOf(
                    LineSeries("Assets", QlotsColors.Green, withBreakdown.map { epochDay(it.date) to it.assets!! }),
                    LineSeries("Liabilities", QlotsColors.Red, withBreakdown.map { epochDay(it.date) to it.liabilities!! }),
                    LineSeries("Net worth", QlotsColors.Deep, withBreakdown.map { epochDay(it.date) to it.netWorth }, strokeDp = 3f),
                ),
                height = 200.dp,
            )
            val verdict = when {
                assetsDelta >= 0 && debtDelta <= 0 -> "Assets are up and debt is down over this period."
                assetsDelta >= 0 && debtDelta > 0 -> "Assets are growing, and so is debt."
                assetsDelta < 0 && debtDelta <= 0 -> "Debt is falling, but assets have dipped."
                else -> "Assets are down and debt is up over this period."
            }
            Footnote(verdict)
        }
    }

    QSection("Snapshots")
    val rows = state.history.asReversed().take(14)
    if (rows.isEmpty()) {
        QCard { EmptyState("No snapshots yet", "A snapshot is saved when Qlots loads your dashboard and every night at 12:00 AM.") }
    } else {
        QCard {
            rows.forEachIndexed { index, row ->
                val previous = rows.getOrNull(index + 1)
                Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(prettyDate(row.date), color = QlotsColors.Muted, fontSize = 11.sp)
                            if (row.source == "manual") Badge("Recorded", QlotsColors.Green, QlotsColors.Mint)
                        }
                        Text(money(row.netWorth), color = QlotsColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
                        if (row.note.isNotBlank()) Text(row.note, color = QlotsColors.Muted, fontSize = 10.sp)
                    }
                    if (previous != null) {
                        val delta = row.netWorth - previous.netWorth
                        Text("${if (delta >= 0) "+" else ""}${money(delta, true)}", color = if (delta >= 0) QlotsColors.Green else QlotsColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    if (row.source == "manual" && row.id != 0) TextButton(onClick = { pendingDelete = row }) { Text("✕", color = QlotsColors.Red) }
                }
                if (index < rows.lastIndex) HorizontalDivider(color = QlotsColors.Border)
            }
        }
    }
    pendingDelete?.let { snap ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this snapshot?") },
            text = { Text("The ${prettyDate(snap.date)} snapshot will be removed from your timeline.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(snap.id) }) { Text("Remove", color = QlotsColors.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep it") } },
            containerColor = Color.White,
        )
    }
}

// ------------------------------------------------------------------------------------------ projection vs actual
@Composable
private fun ProjectionSection(state: QlotsUiState, onSelect: (Int) -> Unit, onSave: () -> Unit, onDelete: (Int) -> Unit) {
    val comparison = state.comparison
    var confirmDelete by remember { mutableStateOf(false) }
    if (state.plans.isEmpty() || comparison == null) {
        QCard(Modifier.padding(top = 14.dp)) {
            CardTitle("Projection vs actual", "Save a projection, then record snapshots over time to see how your plan is tracking.")
            EmptyState("No saved projection yet", "Add some assets or loans first, or save today's projection now.")
            QButton("Save today's projection", onSave)
        }
        return
    }
    Row(Modifier.padding(top = 14.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.plans.take(6).forEach { plan -> QPill("${plan.label.take(22)} · ${shortDate(plan.createdOn)}", comparison.plan.id == plan.id, { onSelect(plan.id) }) }
    }
    val summary = comparison.summary
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Projection vs actual", "Plan saved ${prettyDate(comparison.plan.createdOn)} · started at ${money(comparison.plan.startingNetWorth, true)}")
        var zoom by rememberSaveable { mutableStateOf(true) }
        Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QPill("Next 12 months", zoom, { zoom = true })
            QPill("Full plan", !zoom, { zoom = false })
        }
        val limit = if (zoom) maxOf(epochDay(todayIso()), comparison.actual.maxOfOrNull { epochDay(it.date) } ?: 0.0) + 366 else Double.MAX_VALUE
        val projected = comparison.projected.filter { epochDay(it.date) <= limit }.ifEmpty { comparison.projected }
        LineChart(
            listOf(
                LineSeries("Optimistic", QlotsColors.Blue, projected.map { epochDay(it.date) to it.optimistic }, dashed = true, strokeDp = 1.6f),
                LineSeries("Base plan", QlotsColors.Gold, projected.map { epochDay(it.date) to it.base }, dashed = true, strokeDp = 2.2f),
                LineSeries("Conservative", QlotsColors.Purple, projected.map { epochDay(it.date) to it.conservative }, dashed = true, strokeDp = 1.6f),
                LineSeries("Actual", QlotsColors.Green, comparison.actual.map { epochDay(it.date) to it.netWorth }, strokeDp = 3f, dots = comparison.actual.size < 20),
            ),
            height = 210.dp,
            markerX = epochDay(todayIso()),
        )
        if (summary != null) {
            val (label, fg, bg) = when (summary.status) {
                "ahead" -> Triple("Ahead of plan", QlotsColors.Green, QlotsColors.Mint)
                "behind" -> Triple("Behind plan", QlotsColors.Red, QlotsColors.RedBg)
                else -> Triple("On track", Color(0xFF8A6A12), Color(0xFFFBF1D8))
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Badge(label, fg, bg)
                Text("as of ${prettyDate(summary.asOf)}", color = QlotsColors.Muted, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Actual", money(summary.actual, true), QlotsColors.Deep, Modifier.weight(1f))
                StatTile("Projected", money(summary.projectedBase, true), QlotsColors.Ink, Modifier.weight(1f))
                StatTile("Difference (" + (if (summary.variancePct >= 0) "+" else "") + pct(summary.variancePct) + ")", (if (summary.variance >= 0) "+" else "") + money(summary.variance, true), if (summary.variance >= 0) QlotsColors.Green else QlotsColors.Red, Modifier.weight(1f))
            }
            Text(
                (if (summary.withinRange) "Within the conservative-to-optimistic range (${money(summary.low, true)} – ${money(summary.high, true)}). " else "Outside the conservative-to-optimistic range (${money(summary.low, true)} – ${money(summary.high, true)}). ") + summary.hint,
                color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            Footnote("Record a snapshot after today (or let the nightly refresh run) and your actual line will appear here.")
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QButton("Save today's plan", onSave, secondary = true, modifier = Modifier.weight(1f))
            TextButton(onClick = { confirmDelete = true }) { Text("Delete plan", color = QlotsColors.Red, fontSize = 12.sp) }
        }
        Footnote("Update your assumptions in Plan, then save a new projection to refine how you track.")
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this saved projection?") },
            text = { Text("Your snapshots are not affected.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete(comparison.plan.id) }) { Text("Delete", color = QlotsColors.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep it") } },
            containerColor = Color.White,
        )
    }
}

// ------------------------------------------------------------------------------------------ cash flow
@Composable
private fun CashflowSection(state: QlotsUiState) {
    val data = state.insights?.cashflow
    if (data == null || (data.averageIncome == 0.0 && data.averageExpenses == 0.0)) {
        QCard(Modifier.padding(top = 14.dp)) { EmptyState("No income or expenses yet", "Add recurring income and expenses in the Cash flow tab to see your monthly picture.") }
        return
    }
    QCard(Modifier.padding(top = 14.dp)) {
        CardTitle("Monthly income vs expenses", "Twelve months built from your recurring schedules. Tap a month for details.")
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Avg income", money(data.averageIncome, true), QlotsColors.Green, Modifier.weight(1f))
            StatTile("Avg expenses", money(data.averageExpenses, true), QlotsColors.Red, Modifier.weight(1f))
            val surplus = data.averageIncome - data.averageExpenses - data.emi
            StatTile("Avg surplus", money(surplus, true), if (surplus >= 0) QlotsColors.Blue else QlotsColors.Red, Modifier.weight(1f))
        }
        CashflowBarChart(data.months, height = 200.dp)
        Footnote(data.note)
    }
    if (data.highlights.isNotEmpty()) {
        QCard(Modifier.padding(top = 12.dp)) {
            CardTitle("Seasonal & recurring changes", "Months where lumpy bills make spending higher than your average.")
            data.highlights.forEach { line ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 5.dp).width(8.dp).background(Color(0xFFE0A33A), RoundedCornerShape(50)).padding(4.dp))
                    Text(line, color = QlotsColors.Ink, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ allocation
@Composable
private fun AllocationSection(state: QlotsUiState) {
    val data = state.insights
    if (data == null || data.allocation.isEmpty()) {
        QCard(Modifier.padding(top = 14.dp)) { EmptyState("Start with one asset", "Add cash, deposits, investments or property to see your allocation.") }
        return
    }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    QCard(Modifier.padding(top = 14.dp)) {
        CardTitle("Asset allocation", "Where your recorded assets sit today.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            DonutChart(
                data.allocation.map { AssetAllocation(it.group, it.value) }, sizeDp = 132.dp,
                centerTop = "ASSETS", centerMain = money(data.allocationTotal, true), centerBottom = "${data.allocation.size} groups",
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                data.allocation.forEachIndexed { index, g ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        DonutLegendDot(index)
                        Text(g.group, color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${g.pct.toInt()}%", color = QlotsColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    QSection("Breakdown")
    QCard {
        data.allocation.forEachIndexed { index, g ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().clickableNoRipple { expanded = if (expanded == g.group) null else g.group }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DonutLegendDot(index)
                    Text(g.group, color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(money(g.value, true), color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(if (expanded == g.group) "▴" else "▾", color = QlotsColors.Muted, fontSize = 12.sp)
                }
                SmallProgressBar((g.pct / 100).toFloat(), chartColor(index), Modifier.padding(top = 7.dp))
                if (expanded == g.group) {
                    g.categories.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(labelize(c.category), color = QlotsColors.Muted, fontSize = 11.sp)
                            Text(money(c.value, true), color = QlotsColors.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
            if (index < data.allocation.lastIndex) HorizontalDivider(color = QlotsColors.Border)
        }
        Footnote("Cash = cash & bank balances · Deposits = FDs and RDs at today's accrued value · Investments = stocks, mutual funds and ETFs.")
    }
}

// ------------------------------------------------------------------------------------------ debt
@Composable
private fun DebtSection(state: QlotsUiState) {
    val debt: DebtData? = state.insights?.debt
    if (debt == null || debt.loans.isEmpty()) {
        QCard(Modifier.padding(top = 14.dp)) { EmptyState("No loans recorded", "Add a loan in Wealth to track repayment progress toward becoming debt-free.") }
        return
    }
    QCard(Modifier.padding(top = 14.dp), containerColor = QlotsColors.Deep, contentColor = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing((debt.paidPct / 100).toFloat().coerceIn(0f, 1f), sizeDp = 78.dp, color = QlotsColors.Lime, label = "${debt.paidPct.toInt()}%", trackColor = Color(0xFF365744), labelColor = Color.White)
            Column(Modifier.weight(1f)) {
                Text("OUTSTANDING DEBT", color = Color(0xFF7BA898), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(money(debt.totalBalance), color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (debt.debtFreeDate != null) "Debt-free by ${prettyDate(debt.debtFreeDate)}" else "Debt-free date needs an EMI on every loan",
                    color = Color(0xFFB9D3C4), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        Text("${debt.paidPct.toInt()}% repaid of the peak ${money(debt.peakBalance, true)} · EMIs ${money(debt.monthlyEmi, true)}/month", color = Color(0xFF9AB4A5), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
    }
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Debt balance over time", "Recorded balance (solid) and the repayment path from your EMIs (dashed).")
        val series = buildList {
            if (debt.history.size >= 2) add(LineSeries("Recorded", QlotsColors.Red, debt.history.map { epochDay(it.date) to it.value }, strokeDp = 2.8f))
            if (debt.projection.size >= 2) add(LineSeries("Projected", Color(0xFFD08A7E), debt.projection.map { epochDay(it.date) to it.value }, dashed = true, fill = debt.history.size < 2))
        }
        if (series.isEmpty()) EmptyState("Chart appears with more history", "Come back after a few nightly snapshots.")
        else LineChart(series, height = 190.dp, markerX = epochDay(todayIso()))
        Footnote(debt.note)
    }
    QSection("Loans")
    debt.loans.forEach { loan ->
        QCard(Modifier.padding(bottom = 9.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(loan.name, color = QlotsColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${labelize(loan.category)} · ${numText(loan.rate)}% p.a. · EMI ${money(loan.emi, true)}", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Text(money(loan.balance, true), color = QlotsColors.Red, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            SmallProgressBar((loan.paidPct / 100).toFloat(), QlotsColors.Green, Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().padding(top = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${loan.paidPct.toInt()}% repaid", color = QlotsColors.Muted, fontSize = 10.sp)
                Text(if (loan.payoffDate != null) "Clear by ${prettyDate(loan.payoffDate)} (${durationLabel(loan.monthsLeft)})" else "Add an EMI to estimate payoff", color = QlotsColors.Ink, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            }
            if (loan.interestRemaining != null) Text("Interest still to pay: ${money(loan.interestRemaining, true)}", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------ goals
@Composable
private fun GoalsSection(state: QlotsUiState, onAdd: () -> Unit, onEdit: (GoalItem) -> Unit, onProgress: (GoalItem) -> Unit, onDelete: (GoalItem) -> Unit) {
    var pendingDelete by remember { mutableStateOf<GoalItem?>(null) }
    QButton("＋  Add a goal", onAdd, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    if (state.goals.isEmpty()) {
        QCard(Modifier.padding(top = 8.dp)) { EmptyState("Set your first goal", "An emergency fund, a home deposit or an education fund — Qlots shows progress and what to set aside each month.") }
    }
    state.goals.forEach { goal ->
        QCard(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ProgressRing((goal.progressPct / 100).toFloat(), sizeDp = 68.dp, color = if (goal.onTrack == false) QlotsColors.Gold else QlotsColors.Green, label = "${goal.progressPct.toInt()}%")
                Column(Modifier.weight(1f)) {
                    Text(goal.name, color = QlotsColors.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${money(goal.current, true)} of ${money(goal.target, true)}", color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        when (goal.onTrack) {
                            true -> Badge(if (goal.progressPct >= 100) "Goal reached" else "On track", QlotsColors.Green, QlotsColors.Mint)
                            false -> Badge("Needs attention", Color(0xFF8A6A12), Color(0xFFFBF1D8))
                            null -> {}
                        }
                        if (goal.autoLink) Badge("Linked to cash", QlotsColors.Blue, Color(0xFFEAF0F5))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Still to save", money(goal.remaining, true), QlotsColors.Ink, Modifier.weight(1f))
                StatTile(if (goal.targetDate != null) "Needed / month" else "Saving / month", money(goal.monthlyRequired ?: goal.monthly, true), QlotsColors.Deep, Modifier.weight(1f))
                StatTile("Projected finish", goal.projectedCompletion?.let { monthYear(it) } ?: "—", QlotsColors.Ink, Modifier.weight(1f))
            }
            if (goal.targetDate != null) Text("Target date ${prettyDate(goal.targetDate)}" + (goal.monthlyRequired?.let { " · needs ${money(it)} a month at ${numText(goal.expectedReturn)}% return" } ?: ""), color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
            val series = buildList {
                if (goal.history.size >= 2) add(LineSeries("Saved", QlotsColors.Green, goal.history.map { epochDay(it.date) to it.value }, fill = true, strokeDp = 2.8f))
                if (goal.projection.size >= 2) add(LineSeries("Projected", QlotsColors.Gold, goal.projection.map { epochDay(it.date) to it.value }, dashed = true))
                val xs = (goal.history.map { epochDay(it.date) } + goal.projection.map { epochDay(it.date) })
                if (xs.size >= 2) add(LineSeries("Target", Color(0xFFB8C2B8), listOf(xs.min() to goal.target, xs.max() to goal.target), dashed = true, strokeDp = 1.4f))
            }
            if (series.any { it.name == "Saved" || it.name == "Projected" }) {
                LineChart(series, height = 150.dp, modifier = Modifier.padding(top = 10.dp), markerX = epochDay(todayIso()))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!goal.autoLink) TextButton(onClick = { onProgress(goal) }) { Text("Update progress", color = QlotsColors.Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { onEdit(goal) }) { Text("Edit", color = QlotsColors.Green, fontSize = 12.sp) }
                TextButton(onClick = { pendingDelete = goal }) { Text("Remove", color = QlotsColors.Red, fontSize = 12.sp) }
            }
        }
    }
    pendingDelete?.let { goal ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove this goal?") },
            text = { Text("“${goal.name}” and its progress history will be removed.") },
            confirmButton = { TextButton(onClick = { pendingDelete = null; onDelete(goal) }) { Text("Remove", color = QlotsColors.Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep it") } },
            containerColor = Color.White,
        )
    }
}
