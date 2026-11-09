package com.qlots.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.qlots.app.data.DepositRow

enum class PlanSection(val title: String) {
    OUTLOOK("Outlook"), WHATIF("What-if"), EMERGENCY("Emergency fund"), SAVINGS("Savings rate"),
    GOALCALC("Goal calculator"), LOANS("Loan payoff"), DEPOSITS("Deposits"), ASSISTANT("Assistant"),
}

class PlanActions(
    val onSaveAssumptions: (Assumptions) -> Unit,
    val onScenario: (String) -> Unit,
    val onAskHelper: (String) -> Unit,
    val onCreatePdf: () -> Unit,
    val onRunOutlook: (years: Int, extraSave: Double, extraLoan: Double, equity: Double?) -> Unit,
    val onViewCalculation: (OutlookRequest) -> Unit,
    val onWhatIf: (years: Int, extraSave: Double, extraLoan: Double, purchase: Double, purchaseIn: Int, delay: Int) -> Unit,
    val onEmergency: (months: Int, includeEmi: Boolean, buildIn: Int) -> Unit,
    val onCreateEmergencyGoal: (Double) -> Unit,
    val onGoalCalc: (target: Double, current: Double, ret: Double, months: Int) -> Unit,
    val onLoanPayoff: (extra: Double, target: Int?) -> Unit,
    val onOpenWealth: () -> Unit,
    val onConsumeSection: () -> Unit,
)

@Composable
fun PlanScreen(state: QlotsUiState, actions: PlanActions) {
    var section by rememberSaveable { mutableStateOf(PlanSection.OUTLOOK.name) }
    LaunchedEffect(state.sectionRequest) {
        val requested = state.sectionRequest
        if (requested != null && PlanSection.entries.any { it.name == requested }) section = requested
        if (requested != null) actions.onConsumeSection()
    }
    val current = PlanSection.valueOf(section)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(bottom = 26.dp)) {
        Column(Modifier.padding(top = 10.dp, bottom = 12.dp)) {
            Text("Plan", color = QlotsColors.Deep, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
            Text("Calculators and what-ifs built on your own numbers.", color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlanSection.entries.forEach { QPill(it.title, current == it, { section = it.name }) }
        }
        Column(Modifier.padding(top = 14.dp)) {
            when (current) {
                PlanSection.OUTLOOK -> OutlookPlanner(state, actions)
                PlanSection.WHATIF -> WhatIfPlanner(state, actions)
                PlanSection.EMERGENCY -> EmergencyPlanner(state, actions)
                PlanSection.SAVINGS -> SavingsRatePanel(state)
                PlanSection.GOALCALC -> GoalCalculator(state, actions)
                PlanSection.LOANS -> LoanPayoffPlanner(state, actions)
                PlanSection.DEPOSITS -> DepositPlanner(state, actions)
                PlanSection.ASSISTANT -> AssistantPanel(state, actions.onAskHelper, actions.onCreatePdf)
            }
        }
        Text("Estimates are based on the information you entered and the assumptions shown. They are not predictions or financial advice.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(horizontal = 3.dp, vertical = 16.dp))
    }
}

@Composable
private fun PillRow(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { (value, label) -> QPill(label, selected == value, { onSelect(value) }) }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp, top = 2.dp))
}

// ------------------------------------------------------------------------------------------ 1. outlook
@Composable
private fun OutlookPlanner(state: QlotsUiState, actions: PlanActions) {
    var years by rememberSaveable { mutableStateOf("3") }
    var extraSave by rememberSaveable { mutableStateOf("") }
    var extraLoan by rememberSaveable { mutableStateOf("") }
    var equity by rememberSaveable { mutableStateOf("") }
    var real by rememberSaveable { mutableStateOf(false) }
    fun run() = actions.onRunOutlook(years.toInt(), extraSave.toDoubleOrNull() ?: 0.0, extraLoan.toDoubleOrNull() ?: 0.0, equity.toDoubleOrNull())
    LaunchedEffect(Unit) { if (state.outlook == null) run() }
    val outlook = state.outlook

    QCard {
        CardTitle("Net-worth outlook", "Conservative, base and optimistic paths. Change the levers to see how they move.")
        PillRow(listOf("2" to "2 years", "3" to "3 years", "5" to "5 years"), years, { years = it; run() })
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today's purchasing power", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Show values discounted by inflation (${numText(state.assumptions.inflation)}% a year).", color = QlotsColors.Muted, fontSize = 10.sp)
            }
            Switch(checked = real, onCheckedChange = { real = it }, colors = SwitchDefaults.colors(checkedTrackColor = QlotsColors.Green))
        }
        if (outlook == null) {
            EmptyState("Calculating…", "Building your projection.")
        } else {
            val pick: (com.qlots.app.data.OutlookPoint) -> Double = { if (real) it.realNetWorth else it.netWorth }
            LineChart(
                listOf(
                    LineSeries("Optimistic", QlotsColors.Blue, outlook.scenarios["optimistic"].orEmpty().map { epochDay(it.date) to pick(it) }, strokeDp = 2f),
                    LineSeries("Base", QlotsColors.Green, outlook.scenarios["base"].orEmpty().map { epochDay(it.date) to pick(it) }, fill = true, strokeDp = 3f),
                    LineSeries("Conservative", QlotsColors.Purple, outlook.scenarios["conservative"].orEmpty().map { epochDay(it.date) to pick(it) }, strokeDp = 2f),
                ),
                modifier = Modifier.padding(top = 10.dp), height = 200.dp,
            )
            Text("Projected to ${prettyDate(outlook.endDate)}", color = QlotsColors.Deep, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            listOf("optimistic", "base", "conservative").forEachIndexed { i, name ->
                val last = outlook.scenarios[name].orEmpty().lastOrNull() ?: return@forEachIndexed
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(labelize(name), color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(money(last.netWorth), color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("≈ ${money(last.realNetWorth, true)} in today's money", color = QlotsColors.Muted, fontSize = 9.sp)
                    }
                }
                if (i < 2) HorizontalDivider(color = QlotsColors.Border)
            }
        }
    }

    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Adjust the plan", "Leave a field empty to use your saved assumptions.")
        QField("Extra monthly saving · ₹", extraSave, { extraSave = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "0", hint = "Invested every month at the investment growth rate below.")
        QField("Investment growth · % a year", equity, { equity = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "Saved: ${numText(state.assumptions.equityReturn)}")
        QField("Extra loan repayment · ₹ a month", extraLoan, { extraLoan = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "0", hint = "Goes to your highest-interest loan first.")
        QButton(if (state.busy) "Updating…" else "Update outlook", { run() }, enabled = !state.busy)
        QButton("View the calculation", {
            actions.onViewCalculation(OutlookRequest(years.toInt(), OutlookAdjust(extraSave.toDoubleOrNull() ?: 0.0, extraLoan.toDoubleOrNull() ?: 0.0, equity.toDoubleOrNull())))
        }, secondary = true, modifier = Modifier.padding(top = 8.dp))
    }

    QSection("Saved assumptions")
    AssumptionsPanel(state, actions.onSaveAssumptions, actions.onScenario)
}

// ------------------------------------------------------------------------------------------ 2. what-if
@Composable
private fun WhatIfPlanner(state: QlotsUiState, actions: PlanActions) {
    var years by rememberSaveable { mutableStateOf("5") }
    var extraSave by rememberSaveable { mutableStateOf("5000") }
    var extraLoan by rememberSaveable { mutableStateOf("") }
    var purchase by rememberSaveable { mutableStateOf("") }
    var purchaseIn by rememberSaveable { mutableStateOf("3") }
    var delay by rememberSaveable { mutableStateOf("") }
    val result = state.whatIf
    QCard {
        CardTitle("What-if scenario builder", "Compare saving more, paying a loan off sooner or delaying a large purchase against your current plan.")
        FieldLabel("Compare over")
        PillRow(listOf("3" to "3 years", "5" to "5 years", "10" to "10 years"), years, { years = it }, Modifier.padding(bottom = 10.dp))
        QField("Save extra · ₹ a month", extraSave, { extraSave = decimalOnly(it) }, keyboardType = KeyboardType.Decimal)
        QField("Pay extra on loans · ₹ a month", extraLoan, { extraLoan = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "0")
        QField("Large purchase · ₹", purchase, { purchase = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "0", hint = "e.g. a car, a holiday or an appliance.")
        if ((purchase.toDoubleOrNull() ?: 0.0) > 0) {
            QField("Planned in · months from now", purchaseIn, { purchaseIn = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
            QField("Delay it by · months", delay, { delay = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number)
        }
        QButton(if (state.busy) "Comparing…" else "Compare scenarios", {
            actions.onWhatIf(years.toInt(), extraSave.toDoubleOrNull() ?: 0.0, extraLoan.toDoubleOrNull() ?: 0.0, purchase.toDoubleOrNull() ?: 0.0, purchaseIn.toIntOrNull()?.coerceAtLeast(1) ?: 1, delay.toIntOrNull() ?: 0)
        }, enabled = !state.busy)
    }
    if (result != null) {
        val colors = listOf(QlotsColors.Muted, QlotsColors.Green, QlotsColors.Blue, QlotsColors.Gold, QlotsColors.Purple, QlotsColors.Deep)
        QCard(Modifier.padding(top = 12.dp)) {
            CardTitle("Net worth by year", "Up to ${prettyDate(result.endDate)} · ${labelize(state.scenario)} returns")
            LineChart(
                result.runs.mapIndexed { i, run -> LineSeries(run.label.take(24), colors[i % colors.size], run.yearly.map { it.year.toDouble() to it.netWorth }, dashed = run.key == "baseline", strokeDp = if (run.key == "combined") 3.2f else 2.2f) },
                height = 200.dp, xFormatter = { "Year ${it.toInt()}" }, tooltipDate = { "Year ${it.toInt()}" },
            )
        }
        val best = result.runs.drop(1).maxByOrNull { it.delta }
        result.runs.forEachIndexed { i, run ->
            QCard(Modifier.padding(top = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(run.label, color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                            if (run === best && run.delta > 0) Badge("Best", QlotsColors.Green, QlotsColors.Mint)
                        }
                        Text(run.description, color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 14.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
                        Text(money(run.finalNetWorth, true), color = QlotsColors.Deep, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                        if (i > 0) Text((if (run.delta >= 0) "+" else "") + money(run.delta, true), color = if (run.delta >= 0) QlotsColors.Green else QlotsColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Debt left: ${money(run.finalLiabilities, true)} · ≈ ${money(run.yearly.last().realNetWorth, true)} in today's money", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------ 3. emergency fund
@Composable
private fun EmergencyPlanner(state: QlotsUiState, actions: PlanActions) {
    var months by rememberSaveable { mutableStateOf("6") }
    var includeEmi by rememberSaveable { mutableStateOf(true) }
    var buildIn by rememberSaveable { mutableStateOf("12") }
    fun load() = actions.onEmergency(months.toInt(), includeEmi, buildIn.toInt())
    LaunchedEffect(Unit) { load() }
    val plan = state.emergency
    QCard {
        CardTitle("Emergency-fund planner", "A reserve sized from your own monthly spending.")
        FieldLabel("Months of cover")
        PillRow(listOf("3" to "3", "6" to "6", "9" to "9", "12" to "12"), months, { months = it; load() })
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Include loan EMIs", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("EMIs still fall due if income stops.", color = QlotsColors.Muted, fontSize = 10.sp)
            }
            Switch(checked = includeEmi, onCheckedChange = { includeEmi = it; load() }, colors = SwitchDefaults.colors(checkedTrackColor = QlotsColors.Green))
        }
        FieldLabel("Build it within")
        PillRow(listOf("6" to "6 months", "12" to "12 months", "18" to "18 months", "24" to "24 months"), buildIn, { buildIn = it; load() })
    }
    if (plan == null) return
    QCard(Modifier.padding(top = 12.dp), containerColor = QlotsColors.Deep, contentColor = Color.White) {
        Text("SUGGESTED RESERVE", color = Color(0xFF7BA898), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(money(plan.target), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
        Text("${plan.months} months × ${money(plan.monthlyEssential)} a month", color = Color(0xFFB9D3C4), fontSize = 12.sp)
        SmallProgressBar((plan.progressPct / 100).toFloat(), QlotsColors.Lime, Modifier.padding(top = 14.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${money(plan.currentLiquid, true)} saved · ${numText(plan.coveredMonths)} months covered", color = Color(0xFF9AB4A5), fontSize = 11.sp)
            Text("${plan.progressPct.toInt()}%", color = QlotsColors.Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Your numbers")
        ResultRow("Monthly expenses", money(plan.monthlyExpenses))
        ResultRow("Loan EMIs", if (plan.includeEmi) money(plan.monthlyEmi) else "Not included")
        ResultRow("Cash & bank balances", money(plan.currentLiquid))
        ResultRow("Gap to fill", money(plan.gap), if (plan.gap > 0) QlotsColors.Red else QlotsColors.Green)
        if (plan.gap > 0) ResultRow("Set aside each month for ${plan.buildInMonths} months", money(plan.monthlyNeeded), QlotsColors.Deep, bold = true)
        else Text("You already have this much in cash — nicely covered.", color = QlotsColors.Green, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        HorizontalDivider(color = QlotsColors.Border, modifier = Modifier.padding(vertical = 10.dp))
        Text("Other cover levels", color = QlotsColors.Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        plan.options.forEach { ResultRow("${it.months} months", "${money(it.target, true)} · gap ${money(it.gap, true)}") }
        Footnote(plan.note)
        QButton("Create an emergency-fund goal", { actions.onCreateEmergencyGoal(plan.target) }, secondary = true, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color = QlotsColors.Ink, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, color = color, fontSize = 13.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------------------------------ 4. savings rate
@Composable
private fun SavingsRatePanel(state: QlotsUiState) {
    val data = state.insights?.savings
    if (data == null || data.monthlyIncome <= 0) {
        QCard { EmptyState("Add your income first", "Savings rate needs your monthly income and expenses.") }
        return
    }
    QCard(containerColor = QlotsColors.Deep, contentColor = Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ProgressRing(((data.currentRate ?: 0.0) / 100).toFloat().coerceIn(0f, 1f), sizeDp = 84.dp, color = QlotsColors.Lime, label = "${(data.currentRate ?: 0.0).toInt()}%", trackColor = Color(0xFF365744), labelColor = Color.White)
            Column(Modifier.weight(1f)) {
                Text("SAVINGS RATE", color = Color(0xFF7BA898), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("${money(data.monthlySaved, true)} a month", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("of ${money(data.monthlyIncome, true)} income saved or invested", color = Color(0xFFB9D3C4), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        data.trendChange?.let {
            Text((if (it >= 0) "▲ " else "▼ ") + "${numText(kotlin.math.abs(it))} points since ${data.points.first().label}", color = if (it >= 0) QlotsColors.Lime else Color(0xFFF4A89A), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
        }
    }
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Trend", "Month-end savings rate from your saved snapshots. The dashed line is a ${data.benchmark.toInt()}% rule of thumb.")
        if (data.points.size < 2) EmptyState("Your trend starts here", "Qlots keeps a monthly point from your nightly snapshots — check back next month.")
        else RateBars(data.points, data.benchmark, height = 140.dp)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Monthly investment (SIP)", money(data.monthlyInvestment, true), QlotsColors.Blue, Modifier.weight(1f))
            StatTile("SIP as share of income", data.investedShare?.let { pct(it) } ?: "—", QlotsColors.Purple, Modifier.weight(1f))
        }
        Footnote(data.note)
    }
}

// ------------------------------------------------------------------------------------------ 5. goal calculator
@Composable
private fun GoalCalculator(state: QlotsUiState, actions: PlanActions) {
    var target by rememberSaveable { mutableStateOf("") }
    var current by rememberSaveable { mutableStateOf("") }
    var ret by rememberSaveable { mutableStateOf("7") }
    var months by rememberSaveable { mutableStateOf("36") }
    val result = state.goalCalc
    QCard {
        CardTitle("Goal contribution calculator", "How much to set aside each month to reach a target by a chosen date.")
        QField("Target amount · ₹", target, { target = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "e.g. 1000000")
        QField("Already saved · ₹", current, { current = decimalOnly(it) }, keyboardType = KeyboardType.Decimal)
        QField("Expected return · % a year", ret, { ret = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, hint = "Use 0 for money kept in cash. Returns are an assumption, not a promise.")
        FieldLabel("Time to reach it")
        PillRow(listOf("12" to "1 yr", "24" to "2 yrs", "36" to "3 yrs", "60" to "5 yrs", "120" to "10 yrs"), months, { months = it })
        QField("…or enter months", months, { months = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number, modifier = Modifier.padding(top = 8.dp))
        QButton(if (state.busy) "Calculating…" else "Calculate", {
            val t = target.toDoubleOrNull() ?: return@QButton
            actions.onGoalCalc(t, current.toDoubleOrNull() ?: 0.0, ret.toDoubleOrNull() ?: 0.0, months.toIntOrNull()?.coerceIn(1, 600) ?: 12)
        }, enabled = !state.busy && (target.toDoubleOrNull() ?: 0.0) > 0)
    }
    if (result != null) {
        QCard(Modifier.padding(top = 12.dp), containerColor = QlotsColors.Deep, contentColor = Color.White) {
            Text("SET ASIDE EACH MONTH", color = Color(0xFF7BA898), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text(if (result.alreadyCovered) "₹0" else money(result.monthlyRequired), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Text(if (result.alreadyCovered) "Your savings and growth already cover this target." else "to reach ${money(result.target, true)} by ${prettyDate(result.targetDate)} (${durationLabel(result.months)})", color = Color(0xFFB9D3C4), fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
        }
        QCard(Modifier.padding(top = 12.dp)) {
            CardTitle("How it adds up")
            ResultRow("Already saved", money(result.current))
            ResultRow("Your contributions", money(result.totalContributions))
            ResultRow("Growth on the money", money(result.growth))
            ResultRow("Target", money(result.target), QlotsColors.Deep, bold = true)
            LineChart(
                listOf(
                    LineSeries("Savings", QlotsColors.Green, result.path.map { epochDay(it.date) to it.value }, fill = true),
                    LineSeries("Target", Color(0xFFB8C2B8), listOf(epochDay(result.path.first().date) to result.target, epochDay(result.path.last().date) to result.target), dashed = true, strokeDp = 1.4f),
                ),
                modifier = Modifier.padding(top = 8.dp), height = 170.dp,
            )
            Text(result.formula, color = QlotsColors.Deep, fontSize = 10.sp, lineHeight = 15.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFFF1F6EE), RoundedCornerShape(10.dp)).padding(10.dp))
            Footnote(result.note)
        }
    }
}

// ------------------------------------------------------------------------------------------ 6. loan payoff
@Composable
private fun LoanPayoffPlanner(state: QlotsUiState, actions: PlanActions) {
    var extra by rememberSaveable { mutableStateOf("5000") }
    var target by rememberSaveable { mutableStateOf("") }
    fun run() = actions.onLoanPayoff(extra.toDoubleOrNull() ?: 0.0, target.toIntOrNull())
    LaunchedEffect(Unit) { if (state.payoff == null) run() }
    val result = state.payoff
    val loans = state.entries.filter { it.kind == "liability" && it.amount > 0 }
    if (loans.isEmpty()) {
        QCard { EmptyState("No loans recorded", "Add a loan in Wealth to compare repayment approaches.") }
        return
    }
    QCard {
        CardTitle("Loan payoff comparison", "See how an extra monthly payment changes your debt-free date and total interest.")
        QField("Extra payment · ₹ a month", extra, { extra = decimalOnly(it) }, keyboardType = KeyboardType.Decimal)
        if (loans.size > 1) {
            FieldLabel("Put the extra toward one loan")
            PillRow(listOf("" to "None") + loans.map { it.id.toString() to it.name.take(18) }, target, { target = it })
        }
        QButton(if (state.busy) "Comparing…" else "Compare approaches", { run() }, enabled = !state.busy, modifier = Modifier.padding(top = 10.dp))
    }
    if (result == null || result.strategies.isEmpty()) return
    val colors = mapOf("minimum" to QlotsColors.Muted, "avalanche" to QlotsColors.Green, "snowball" to QlotsColors.Blue, "chosen" to QlotsColors.Gold)
    QCard(Modifier.padding(top = 12.dp)) {
        CardTitle("Balance over time", "Total outstanding across your loans under each approach.")
        LineChart(
            result.strategies.map { s -> LineSeries(s.label.take(22), colors[s.key] ?: QlotsColors.Purple, s.series.map { it.first.toDouble() to it.second }, dashed = s.key == "minimum", strokeDp = if (s.key == "minimum") 2f else 2.8f) },
            height = 190.dp, xFormatter = { "${(it / 12).toInt()}y" }, tooltipDate = { "Month ${it.toInt()}" },
        )
    }
    val best = result.strategies.filter { it.debtFree && it.key != "minimum" }.minByOrNull { it.totalInterest }
    result.strategies.forEach { s ->
        QCard(Modifier.padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.label, color = QlotsColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (s === best) Badge("Lowest interest", QlotsColors.Green, QlotsColors.Mint)
            }
            Text(s.description, color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
            ResultRow("Debt-free", if (s.debtFree) "${prettyDate(s.debtFreeDate)} (${durationLabel(s.months)})" else "Not within 50 years")
            ResultRow("Total interest", money(s.totalInterest))
            if (s.key != "minimum") {
                if (s.interestSaved != null) ResultRow("Interest saved vs minimum", money(s.interestSaved), QlotsColors.Green, bold = true)
                if (s.monthsSaved != null) ResultRow("Time saved", durationLabel(s.monthsSaved), QlotsColors.Green)
            }
            if (s.order.isNotEmpty()) Text("Order: " + s.order.joinToString(" → ") { "${it.first} (${monthYear(it.second)})" }, color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Footnote(result.note)
}

// ------------------------------------------------------------------------------------------ 7. deposits
@Composable
private fun DepositPlanner(state: QlotsUiState, actions: PlanActions) {
    val data = state.insights?.deposits
    if (data == null || data.rows.isEmpty()) {
        QCard {
            EmptyState("No deposits yet", "Add a fixed or recurring deposit with its start date, rate and maturity date to track it here.")
            QButton("Add in Wealth", actions.onOpenWealth, secondary = true)
        }
        return
    }
    QCard {
        CardTitle("Deposit maturity planner", "Fixed and recurring deposits, sorted by maturity date.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Value today", money(data.currentValue, true), QlotsColors.Deep, Modifier.weight(1f))
            StatTile("At maturity", money(data.maturityValue, true), QlotsColors.Green, Modifier.weight(1f))
            StatTile("Next maturity", data.nextMaturity?.let { shortDate(it) + " " + it.take(4).takeLast(2) } ?: "—", QlotsColors.Ink, Modifier.weight(1f))
        }
    }
    data.rows.forEach { DepositCard(it) }
    Footnote(data.note)
}

@Composable
private fun DepositCard(d: DepositRow) {
    val (statusLabel, fg, bg) = when (d.status) {
        "matured" -> Triple("Matured", QlotsColors.Blue, Color(0xFFEAF0F5))
        "maturing_soon" -> Triple("Matures in ${d.daysToMaturity} days", Color(0xFF8A6A12), Color(0xFFFBF1D8))
        "active" -> Triple("${(d.daysToMaturity ?: 0) / 30} months to go", QlotsColors.Green, QlotsColors.Mint)
        else -> Triple("No maturity date", QlotsColors.Muted, Color(0xFFF1F3EF))
    }
    QCard(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Badge(d.type, QlotsColors.Deep, QlotsColors.Lime)
            Column(Modifier.weight(1f)) {
                Text(d.name, color = QlotsColors.Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${numText(d.annualRate)}% p.a. · compounded ${labelize(d.compounding).lowercase()}" + if (d.institution.isNotBlank()) " · ${d.institution}" else "", color = QlotsColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Badge(statusLabel, fg, bg)
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(if (d.type == "FD") "Principal" else "Paid in so far", money(if (d.type == "FD") d.principal else d.investedSoFar, true), QlotsColors.Ink, Modifier.weight(1f))
            StatTile("Value today", money(d.currentValue, true), QlotsColors.Deep, Modifier.weight(1f))
            StatTile("At maturity", d.maturityValue?.let { money(it, true) } ?: "—", QlotsColors.Green, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${prettyDate(d.startDate)} → ${prettyDate(d.maturityDate)}", color = QlotsColors.Muted, fontSize = 10.sp)
            d.interestAtMaturity?.let { Text("Interest ${money(it, true)}", color = QlotsColors.Green, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
        }
        Text(d.formula, color = QlotsColors.Muted, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
    }
}
