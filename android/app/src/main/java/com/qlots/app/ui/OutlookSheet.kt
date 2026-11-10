package com.qlots.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.data.BreakdownData
import com.qlots.app.data.BreakdownRow

/** "View more" for the 1 / 2 / 3 / 5-year outlook: end date, per-asset values with their formulas, assumptions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlookDetailSheet(
    request: OutlookRequest,
    breakdown: BreakdownData?,
    onScenario: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var scenario by rememberSaveable { mutableStateOf("base") }
    LaunchedEffect(request, scenario) { onScenario(scenario) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = QlotsColors.Canvas) {
        val minHeight = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.9f).dp
        Column(Modifier.defaultMinSize(minHeight = minHeight).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            val data = breakdown?.takeIf { it.years.toInt() == request.years && it.scenario == scenario }
            val yearsLabel = if (request.years == 1) "1-YEAR OUTLOOK" else "${request.years}-YEAR OUTLOOK"
            Text(yearsLabel, color = QlotsColors.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            if (data == null) {
                Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = QlotsColors.Green, modifier = Modifier.size(30.dp))
                }
                return@Column
            }
            Text("By ${prettyDate(data.endDate)}", color = QlotsColors.Deep, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
            Text("${data.financialYear} ends ${prettyDate(data.financialYearEnd)} · calculated from ${prettyDate(data.asOf)}", color = QlotsColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("conservative", "base", "optimistic").forEach { QPill(labelize(it), scenario == it, { scenario = it }) }
            }
            QCard(Modifier.padding(top = 12.dp), containerColor = QlotsColors.Deep, contentColor = Color.White) {
                Text("PROJECTED NET WORTH", color = Color(0xFF7BA898), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(money(data.netWorth), color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    SheetMetric("Assets", money(data.totalAssets, true), Color.White)
                    SheetMetric("Liabilities", money(data.totalLiabilities, true), Color(0xFFF4A89A))
                    SheetMetric("Health score", "${data.healthScore}/100", QlotsColors.Lime)
                }
            }
            QCard(Modifier.padding(top = 12.dp)) {
                CardTitle("In today's purchasing power", "Future rupees buy less. Discounting by inflation gives a value that is easier to compare with today.")
                Text(money(data.realNetWorth), color = QlotsColors.Deep, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                FormulaBox(data.realFormula)
            }

            QSection("How each asset is projected")
            data.assetRows.forEach { RowCard(it, positive = true) }
            val total = data.totalAssets
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total assets", color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(money(total), color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            val flows = listOf("income" to "Income earned", "expenses" to "Living expenses", "emi" to "Loan EMIs paid", "extra_loan" to "Extra loan repayment", "sip" to "Monthly investment (SIP)", "rd" to "Recurring-deposit instalments", "purchases" to "Planned purchases")
                .filter { (key, _) -> (data.cashFlow[key] ?: 0.0) != 0.0 }
            if (flows.isNotEmpty()) {
                QSection("Cash flow over the period")
                QCard {
                    flows.forEachIndexed { i, (key, label) ->
                        val value = data.cashFlow[key] ?: 0.0
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = QlotsColors.Muted, fontSize = 12.sp)
                            Text((if (key == "income") "+ " else "− ") + money(value, true), color = if (key == "income") QlotsColors.Green else QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (i < flows.lastIndex) HorizontalDivider(color = QlotsColors.Border)
                    }
                    Footnote("What is left after spending, EMIs and investments is added to cash. Income grows with salary growth and expenses with inflation, once a year.")
                }
            }

            if (data.liabilityRows.isNotEmpty()) {
                QSection("Liabilities")
                data.liabilityRows.forEach { RowCard(it, positive = false) }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total liabilities", color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(money(data.totalLiabilities), color = QlotsColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            QSection("Assumptions used")
            QCard {
                data.assumptions.forEachIndexed { i, a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(a.label, color = QlotsColors.Muted, fontSize = 12.sp)
                        Text(a.value, color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (i < data.assumptions.lastIndex) HorizontalDivider(color = QlotsColors.Border)
                }
            }
            Text(data.disclaimer, color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) { Text("Close", color = QlotsColors.Green, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun SheetMetric(label: String, value: String, color: Color) {
    Column {
        Text(label.uppercase(), color = Color(0xFF9AB4A5), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp))
    }
}

@Composable
private fun FormulaBox(text: String) {
    Text(
        text, color = QlotsColors.Deep, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).background(Color(0xFFF1F6EE), RoundedCornerShape(10.dp)).padding(10.dp),
    )
}

@Composable
private fun RowCard(row: BreakdownRow, positive: Boolean) {
    QCard(Modifier.padding(bottom = 9.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(row.name, color = QlotsColors.Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(labelize(row.category) + if (row.ratePct != 0.0) " · ${numText(row.ratePct)}%" else "", color = QlotsColors.Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(row.projected, true), color = if (positive) QlotsColors.Deep else QlotsColors.Red, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text("from ${money(row.today, true)} today", color = QlotsColors.Muted, fontSize = 9.sp)
            }
        }
        FormulaBox(row.formula)
        if (row.note.isNotBlank()) Text(row.note, color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 6.dp))
    }
}
