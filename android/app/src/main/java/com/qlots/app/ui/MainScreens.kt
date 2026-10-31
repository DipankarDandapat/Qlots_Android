package com.qlots.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.data.Assumptions
import com.qlots.app.data.EntryRecord
import com.qlots.app.data.FinancialDashboard
import com.qlots.app.data.HistorySnapshot
import com.qlots.app.data.MonthlyTotals
import com.qlots.app.data.NetWorthProjection
import com.qlots.app.data.ProjectionResponse
import com.qlots.app.ui.QlotsColors.Blue
import java.text.DecimalFormat
import java.util.Locale

@Composable
fun OverviewScreen(
    state: QlotsUiState,
    onOpenWealth: () -> Unit,
    onOpenCashflow: () -> Unit,
    onOpenPlan: () -> Unit,
) {
    val dashboard = state.dashboard ?: FinancialDashboard()
    val monthly = dashboard.monthly
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 26.dp),
    ) {
        Text("Good day, ${state.user?.name?.substringBefore(' ')?.ifBlank { "there" } ?: "there"}", color = QlotsColors.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp, bottom = 10.dp))
        QCard(containerColor = QlotsColors.Deep, contentColor = Color.White) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.align(Alignment.CenterStart).padding(end = 94.dp)) {
                    Text("NET WORTH", color = Color(0xFF7BA898), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(money(dashboard.netWorth), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, modifier = Modifier.padding(top = 5.dp))
                    Text("as of ${dashboard.asOf.ifBlank { "—" }}", color = Color(0xFF9AB4A5), fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
                }
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.CenterHorizontally) {
                    HealthGauge(dashboard.health.score)
                    Text(dashboard.health.label, color = QlotsColors.Lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (state.history.size > 1) {
                Sparkline(state.history, Modifier.padding(top = 15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(state.history.first().date, color = Color(0xFF7BA898), fontSize = 9.sp)
                    Text(state.history.last().date, color = Color(0xFF7BA898), fontSize = 9.sp)
                }
            }
            HorizontalDivider(color = Color(0xFF365744), modifier = Modifier.padding(top = 16.dp, bottom = 13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HeroMetric("ASSETS", money(dashboard.totalAssets, true), Color.White)
                HeroMetric("LIABILITIES", money(dashboard.totalLiabilities, true), Color(0xFFF4A89A))
                HeroMetric("SURPLUS / MO", money(monthly.surplus, true), if (monthly.surplus >= 0) QlotsColors.Lime else Color(0xFFF4A89A))
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickStat("Monthly income", money(monthly.income, true), QlotsColors.Green, QlotsColors.Mint)
            QuickStat("Monthly expenses", money(monthly.expenses, true), QlotsColors.Red, QlotsColors.RedBg)
            QuickStat("EMI / month", money(monthly.emi, true), Blue, Color(0xFFEAF0F5))
            val savingsRate = if (monthly.income > 0) (monthly.surplus.coerceAtLeast(0.0) / monthly.income * 100).toInt() else null
            QuickStat("Savings rate", savingsRate?.let { "$it%" } ?: "—", QlotsColors.Purple, Color(0xFFF3EEF7))
        }

        QSection("Cash flow", "Details", onOpenCashflow)
        QCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column { Text("Income / month", color = QlotsColors.Muted, fontSize = 11.sp); Text(money(monthly.income, true), color = QlotsColors.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
                Text(if (monthly.surplus >= 0) "↗" else "↘", color = if (monthly.surplus >= 0) QlotsColors.Green else QlotsColors.Red, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
                Column(horizontalAlignment = Alignment.End) { Text("Expenses / month", color = QlotsColors.Muted, fontSize = 11.sp); Text(money(monthly.expenses, true), color = QlotsColors.Red, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp)) }
            }
            CashFlowBar(monthly.income, monthly.expenses, Modifier.padding(top = 15.dp))
            HorizontalDivider(color = QlotsColors.Border, modifier = Modifier.padding(top = 13.dp, bottom = 11.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Monthly surplus after EMIs", color = QlotsColors.Muted, fontSize = 12.sp)
                Text(money(monthly.surplus, true), color = if (monthly.surplus >= 0) QlotsColors.Green else QlotsColors.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        QSection("Where your money is", "Manage", onOpenWealth)
        QCard {
            if (dashboard.assetBreakdown.isEmpty()) {
                EmptyState("Start with one asset", "Add cash, an FD, investments or property to see your allocation.")
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    DonutChart(dashboard.assetBreakdown, sizeDp = 118.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.weight(1f)) {
                        dashboard.assetBreakdown.take(5).forEachIndexed { index, item ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                DonutLegendDot(index)
                                Column(Modifier.weight(1f)) {
                                    Text(labelize(item.category), color = QlotsColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val percent = if (dashboard.totalAssets > 0) (item.value / dashboard.totalAssets * 100).toInt() else 0
                                    Text("$percent% · ${money(item.value, true)}", color = QlotsColors.Muted, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        QSection("Your outlook", "Explore", onOpenPlan)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProjectionTile("1 YEAR · PROJECTED", dashboard.projections.oneYear, false, Modifier.weight(1f))
            ProjectionTile("5 YEARS · PROJECTED", dashboard.projections.fiveYear, true, Modifier.weight(1f))
        }
        Text("Model-based estimates, not guaranteed returns.", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(start = 3.dp, top = 8.dp))

        QSection("Financial health", "View insights", onOpenPlan)
        HealthDetail(dashboard)
        Text("Qlots uses the values you enter. It does not connect to banks or brokers. Estimates are for planning only.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 15.dp))
    }
}

@Composable
private fun HeroMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(92.dp)) {
        Text(label, color = Color(0xFF9AB4A5), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, maxLines = 1)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun QuickStat(label: String, value: String, color: Color, background: Color) {
    Column(Modifier.width(132.dp).background(background, RoundedCornerShape(16.dp)).padding(horizontal = 13.dp, vertical = 12.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
    }
}

@Composable
private fun ProjectionTile(eyebrow: String, projection: NetWorthProjection, dark: Boolean, modifier: Modifier = Modifier) {
    val background = if (dark) QlotsColors.Deep else Color.White
    val primary = if (dark) Color.White else QlotsColors.Ink
    val secondary = if (dark) Color(0xFF9DBAA9) else QlotsColors.Muted
    Column(modifier.background(background, RoundedCornerShape(19.dp)).padding(14.dp)) {
        Text(eyebrow, color = secondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text("Year ${projection.year}", color = secondary, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
        Text(money(projection.netWorth, true), color = primary, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("assumption-based", color = secondary, fontSize = 9.sp, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun HealthDetail(dashboard: FinancialDashboard) {
    var showScoreInfo by remember { mutableStateOf(false) }
    val health = dashboard.health
    val metrics = health.metrics
    val nonCashTypes = dashboard.assetBreakdown.count { it.category !in setOf("cash", "bank_balance", "savings") }
    val savingsPoints = (metrics.savingsRatePct.coerceAtLeast(0.0) * 0.5).coerceAtMost(25.0).toInt()
    val debtPoints = if (dashboard.monthly.income > 0) {
        (25.0 - (metrics.debtToIncomePct * 0.6).coerceAtMost(25.0)).coerceAtLeast(0.0).toInt()
    } else 0
    val emergencyPoints = (metrics.emergencyFundMonths * 5).coerceAtMost(20.0).toInt()
    val diversityPoints = (nonCashTypes * 5).coerceAtMost(15)
    val surplusPoints = if (metrics.monthlySurplus > 0) 15 else if (metrics.monthlySurplus == 0.0 && dashboard.monthly.income > 0) 7 else 0
    QCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(78.dp)) {
                Text("${health.score}", color = QlotsColors.Deep, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                Text("/100", color = QlotsColors.Muted, fontSize = 12.sp)
                Text(health.label, color = QlotsColors.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HealthMetric("Savings rate", "${oneDecimal(metrics.savingsRatePct)}%")
                HealthMetric("Debt-to-income", "${oneDecimal(metrics.debtToIncomePct)}%")
                HealthMetric("Emergency fund", "${oneDecimal(metrics.emergencyFundMonths)} mo")
            }
        }
        HealthBar("Savings rate", (metrics.savingsRatePct / 50).toFloat(), QlotsColors.Green, savingsPoints, 25)
        HealthBar("Low debt ratio", (1 - metrics.debtToIncomePct / 50).toFloat(), Blue, debtPoints, 25)
        HealthBar("Emergency fund", (metrics.emergencyFundMonths / 6).toFloat(), QlotsColors.Gold, emergencyPoints, 20)
        HealthBar("Asset diversity", (nonCashTypes / 3f), QlotsColors.Purple, diversityPoints, 15)
        HealthBar("Monthly surplus", if (metrics.monthlySurplus > 0) 1f else 0.47f, Color(0xFFE07B54), surplusPoints, 15)
        TextButton(onClick = { showScoreInfo = true }, modifier = Modifier.align(Alignment.Start)) {
            Text("How is this score calculated?", color = QlotsColors.Green, fontSize = 10.sp)
        }
    }
    if (showScoreInfo) {
        AlertDialog(
            onDismissRequest = { showScoreInfo = false },
            title = { Text("Financial health score") },
            text = {
                Text(
                    "Savings rate: surplus ÷ income; each 2% earns 1 point, up to 25. Low debt ratio: EMI ÷ income; starts at 25 and loses about 1 point per 1.67% DTI. Emergency fund: liquid cash ÷ monthly expenses; 5 points per month of cover, up to 6 months (20 points). Asset diversity: 5 points for each non-cash asset category, up to 15. Monthly surplus: 15 points if positive, 7 for zero with income, and 0 for a deficit. This is an educational indicator based on your entries, not financial advice."
                )
            },
            confirmButton = { TextButton(onClick = { showScoreInfo = false }) { Text("Got it") } },
            containerColor = Color.White,
        )
    }
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = QlotsColors.Muted, fontSize = 10.sp)
        Text(value, color = QlotsColors.Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HealthBar(label: String, value: Float, color: Color, points: Int, max: Int) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = QlotsColors.Muted, fontSize = 10.sp)
            Text("$points/$max pts", color = QlotsColors.Muted, fontSize = 10.sp)
        }
        SmallProgressBar(value, color, Modifier.padding(top = 4.dp))
    }
}

@Composable
fun WealthScreen(
    entries: List<EntryRecord>,
    onAdd: (String) -> Unit,
    onEdit: (EntryRecord) -> Unit,
    onDelete: (EntryRecord) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf("asset") }
    val records = entries.filter { it.kind == selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        PageHeading("Your records", "Add the numbers you want to keep in view.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("asset", "liability").forEach { kind -> QPill(labelize(kind), selected == kind, { selected = kind }) }
        }
        QButton("＋  Add ${if (selected == "asset") "asset" else "liability"}", { onAdd(selected) }, modifier = Modifier.padding(top = 13.dp, bottom = 7.dp))
        if (records.isEmpty()) {
            QCard { EmptyState("No ${if (selected == "asset") "assets" else "liabilities"} yet", "Your records stay private to your Qlots account. Add one when you are ready.") }
        } else {
            records.forEach { entry -> EntryRow(entry, onEdit, onDelete) }
        }
        Text("Values are entered by you. Qlots does not connect to banks or brokers in this MVP.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 13.dp))
    }
}

@Composable
fun CashflowScreen(
    entries: List<EntryRecord>,
    monthly: MonthlyTotals?,
    onAdd: (String) -> Unit,
    onEdit: (EntryRecord) -> Unit,
    onDelete: (EntryRecord) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf("income") }
    val values = monthly ?: MonthlyTotals()
    val records = entries.filter { it.kind == selected }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        PageHeading("Cash flow", "Track recurring income and expenses.")
        QCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InlineMetric("Income / month", money(values.income), QlotsColors.Green, Modifier.weight(1f))
                InlineMetric("Expenses / month", money(values.expenses), QlotsColors.Ink, Modifier.weight(1.25f))
                InlineMetric("Loan EMI / month", money(values.emi), Blue, Modifier.weight(1.1f))
            }
            HorizontalDivider(color = QlotsColors.Border, modifier = Modifier.padding(vertical = 13.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Monthly surplus after EMIs", color = QlotsColors.Muted, fontSize = 11.sp)
                Text(money(values.surplus), color = if (values.surplus >= 0) QlotsColors.Green else QlotsColors.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Row(Modifier.padding(top = 15.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("income", "expense").forEach { kind -> QPill(labelize(kind), selected == kind, { selected = kind }) }
        }
        QButton("＋  Add ${labelize(selected)}", { onAdd(selected) }, modifier = Modifier.padding(top = 12.dp, bottom = 7.dp))
        if (records.isEmpty()) {
            QCard { EmptyState("No ${labelize(selected).lowercase(Locale.getDefault())} records yet", "Add recurring items to see your monthly cash-flow estimate.") }
        } else {
            records.forEach { EntryRow(it, onEdit, onDelete) }
        }
        Text("Irregular and unentered items are not included in the monthly estimate.", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 13.dp))
    }
}

@Composable
fun HistoryScreen(history: List<HistorySnapshot>) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 24.dp)) {
        PageHeading("Net-worth history", "Daily snapshots based on your saved records.")
        QCard {
            if (history.size < 2) {
                EmptyState("Your timeline starts here", "Save or update your finances on different days to build a history.")
            } else {
                Sparkline(history, Modifier.height(115.dp))
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(history.first().date, color = QlotsColors.Muted, fontSize = 10.sp)
                    Text(history.last().date, color = QlotsColors.Muted, fontSize = 10.sp)
                }
            }
        }
        QSection("Snapshots")
        if (history.isEmpty()) {
            QCard { EmptyState("No snapshots yet", "A snapshot is saved when Qlots loads your dashboard.") }
        } else {
            history.asReversed().forEachIndexed { index, row ->
                val previous = history.asReversed().getOrNull(index + 1)
                QCard(modifier = Modifier.padding(bottom = 9.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(row.date, color = QlotsColors.Muted, fontSize = 11.sp)
                            Text(money(row.netWorth), color = QlotsColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (previous != null) {
                            val delta = row.netWorth - previous.netWorth
                            Text("${if (delta >= 0) "+" else ""}${money(delta)}", color = if (delta >= 0) QlotsColors.Green else QlotsColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Text("History is based on the figures entered and saved in your account.", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
    }
}

@Composable
fun PlanScreen(
    state: QlotsUiState,
    onSaveAssumptions: (Assumptions) -> Unit,
    onScenario: (String) -> Unit,
    onAskHelper: (String) -> Unit,
    onCreatePdf: () -> Unit,
) {
    val assumptions = state.assumptions
    var salaryGrowth by remember(assumptions) { mutableStateOf(assumptions.salaryGrowth.toInput()) }
    var equityReturn by remember(assumptions) { mutableStateOf(assumptions.equityReturn.toInput()) }
    var propertyGrowth by remember(assumptions) { mutableStateOf(assumptions.propertyGrowth.toInput()) }
    var inflation by remember(assumptions) { mutableStateOf(assumptions.inflation.toInput()) }
    var monthlyInvestment by remember(assumptions) { mutableStateOf(assumptions.monthlyInvestment.toInput()) }
    var question by rememberSaveable { mutableStateOf("") }
    val forecast = state.forecast
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 25.dp)) {
        PageHeading("Plan & insights", "Explore assumptions; estimates are not predictions.")
        QCard {
            Text("Projection assumptions", color = QlotsColors.Deep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Adjust rates to see how a five-year model changes. Actual outcomes may differ.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
            QField("Salary growth · % / year", salaryGrowth, { salaryGrowth = it }, keyboardType = KeyboardType.Decimal)
            QField("Equity return · % / year", equityReturn, { equityReturn = it }, keyboardType = KeyboardType.Decimal)
            QField("Property growth · % / year", propertyGrowth, { propertyGrowth = it }, keyboardType = KeyboardType.Decimal)
            QField("Inflation · % / year", inflation, { inflation = it }, keyboardType = KeyboardType.Decimal)
            QField("Monthly investment · ₹", monthlyInvestment, { monthlyInvestment = it }, keyboardType = KeyboardType.Decimal)
            QButton(
                if (state.busy) "Updating…" else "Save assumptions & calculate",
                {
                    val parsed = listOf(salaryGrowth, equityReturn, propertyGrowth, inflation, monthlyInvestment).map { it.toDoubleOrNull() }
                    if (parsed.any { it == null }) return@QButton
                    onSaveAssumptions(Assumptions(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!, parsed[4]!!))
                },
                enabled = !state.busy,
            )
        }
        QSection("Compare scenarios")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("conservative", "base", "optimistic").forEach { scenario ->
                QPill(labelize(scenario), state.scenario == scenario, { onScenario(scenario) })
            }
        }
        if (forecast != null) ForecastCard(forecast)
        else QCard(modifier = Modifier.padding(top = 11.dp)) { EmptyState("No projection yet", "Save assumptions to calculate your current scenario.") }
        QCard(modifier = Modifier.padding(top = 13.dp)) {
            Text("Calculation-backed helper", color = QlotsColors.Deep, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Ask about the records you entered. Answers use server-side calculations and are not an AI financial adviser.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 5.dp, bottom = 12.dp))
            QField("Your question", question, { question = it }, placeholder = "e.g. What is my current net worth?", singleLine = false)
            QButton(if (state.busy) "Working…" else "Ask about my numbers", { onAskHelper(question) }, enabled = !state.busy)
            if (state.helperAnswer.isNotBlank()) {
                Text(state.helperAnswer, color = QlotsColors.Ink, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 14.dp))
            }
            Text("For informational and planning purposes only—not investment, tax or legal advice. Projections are not guaranteed.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp))
        }
        QSection("Export")
        QCard {
            Text("Create a PDF snapshot of your current finances, projections and assumptions.", color = QlotsColors.Muted, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.padding(bottom = 12.dp))
            QButton(if (state.busy) "Preparing report…" else "Create and share PDF report", onCreatePdf, enabled = !state.busy)
        }
        QCard(modifier = Modifier.padding(top = 13.dp)) {
            Text("How the health score works", color = QlotsColors.Deep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Savings rate (25 points), monthly debt-to-income (25), liquid emergency savings (20), asset-category diversity (15), and positive monthly surplus (15). The score is educational and depends on the completeness of your entries.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 7.dp))
        }
    }
}

@Composable
private fun ForecastCard(forecast: ProjectionResponse) {
    QCard(modifier = Modifier.padding(top = 11.dp)) {
        Text("${labelize(forecast.scenario)} scenario", color = QlotsColors.Deep, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Year", color = QlotsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Assets", color = QlotsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Liabilities", color = QlotsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("Net worth", color = QlotsColors.Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = QlotsColors.Border)
        forecast.projections.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${row.year}", color = QlotsColors.Ink, fontSize = 11.sp)
                Text(money(row.assets, true), color = QlotsColors.Ink, fontSize = 10.sp, modifier = Modifier.width(67.dp))
                Text(money(row.liabilities, true), color = QlotsColors.Red, fontSize = 10.sp, modifier = Modifier.width(67.dp))
                Text(money(row.netWorth, true), color = QlotsColors.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp))
            }
            HorizontalDivider(color = QlotsColors.Border)
        }
        Text("Assumption-based model, not a prediction or guarantee.", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
    }
}

@Composable
private fun EntryRow(entry: EntryRecord, onEdit: (EntryRecord) -> Unit, onDelete: (EntryRecord) -> Unit) {
    QCard(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            EntryKindIcon(entry.kind)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 5.dp)) {
                Text(entry.name, color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(labelize(entry.category) + if (entry.institution.isNotBlank()) " · ${entry.institution}" else "", color = QlotsColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                val helper = when {
                    entry.isDeposit -> "Principal ${money(entry.principal)} · ${oneDecimal(entry.annualRate)}% p.a. · ${labelize(entry.frequency)}"
                    entry.kind == "liability" && entry.emi > 0 -> "EMI ${money(entry.emi)}/month · ${oneDecimal(entry.annualRate)}% p.a."
                    entry.kind == "income" || entry.kind == "expense" -> labelize(entry.frequency)
                    else -> ""
                }
                if (helper.isNotBlank()) Text(helper, color = QlotsColors.Muted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                Row(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { onEdit(entry) }, modifier = Modifier.height(28.dp).padding(end = 3.dp)) { Text("Edit", color = QlotsColors.Green, fontSize = 10.sp) }
                    TextButton(onClick = { onDelete(entry) }, modifier = Modifier.height(28.dp)) { Text("Remove", color = QlotsColors.Red, fontSize = 10.sp) }
                }
            }
            Text(money(entry.amount, true), color = if (entry.kind == "liability") QlotsColors.Red else QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PageHeading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 10.dp, bottom = 15.dp)) {
        Text(title, color = QlotsColors.Deep, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
        Text(subtitle, color = QlotsColors.Muted, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun Double.toInput(): String = if (this % 1.0 == 0.0) toLong().toString() else String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

private fun oneDecimal(value: Double): String = DecimalFormat("0.#").format(value)
