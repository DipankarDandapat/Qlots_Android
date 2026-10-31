package com.qlots.app.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qlots.app.data.EntryRecord
import java.util.Calendar
import java.util.Locale

private val EntryCategories = mapOf(
    "asset" to listOf("cash", "bank_balance", "fixed_deposit", "recurring_deposit", "stocks", "mutual_funds", "etfs", "property", "gold", "other"),
    "liability" to listOf("home_loan", "personal_loan", "car_loan", "credit_card", "education_loan", "other_debt"),
    "income" to listOf("salary", "business", "rental", "interest", "other"),
    "expense" to listOf("housing", "food", "transport", "health", "education", "utilities", "entertainment", "other"),
)

@Composable
fun EntryEditorDialog(
    initialKind: String,
    existing: EntryRecord?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (EntryRecord) -> Unit,
) {
    val kind = existing?.kind ?: initialKind
    val initialCategory = existing?.category ?: EntryCategories[kind]?.firstOrNull() ?: "other"
    var category by rememberSaveable(existing?.id, kind) { mutableStateOf(initialCategory) }
    var name by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.name.orEmpty()) }
    var amount by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.amount?.takeIf { it > 0 }?.toInput().orEmpty()) }
    var principal by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.principal?.takeIf { it > 0 }?.toInput().orEmpty()) }
    var annualRate by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.annualRate?.takeIf { it > 0 }?.toInput().orEmpty()) }
    var frequency by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.frequency ?: "monthly") }
    var emi by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.emi?.takeIf { it > 0 }?.toInput().orEmpty()) }
    var tenure by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.tenureMonths?.takeIf { it > 0 }?.toString().orEmpty()) }
    var startDate by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.startDate.orEmpty()) }
    var maturityDate by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.maturityDate.orEmpty()) }
    var growthRate by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.growthRate?.takeIf { it != 0.0 }?.toInput().orEmpty()) }
    var institution by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.institution.orEmpty()) }
    var notes by rememberSaveable(existing?.id, kind) { mutableStateOf(existing?.notes.orEmpty()) }

    val isFd = kind == "asset" && category == "fixed_deposit"
    val isRd = kind == "asset" && category == "recurring_deposit"
    val isDeposit = isFd || isRd
    val isLoan = kind == "liability"
    val height = (LocalConfiguration.current.screenHeightDp * 0.9f).dp
    val categories = EntryCategories[kind] ?: EntryCategories.getValue("asset")

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).heightIn(max = height),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            color = QlotsColors.Canvas,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("${if (existing == null) "NEW" else "EDIT"} ${labelize(kind).uppercase(Locale.getDefault())}", color = QlotsColors.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text(if (existing == null) "Add to your picture" else "Update record", color = QlotsColors.Deep, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = onDismiss) { Text("Close", color = QlotsColors.Green) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
                    Text("Category", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        categories.forEach { choice -> QPill(labelize(choice), category == choice, { category = choice }) }
                    }
                    QField("Name", name, { name = it }, placeholder = when (kind) { "asset" -> "e.g. Main savings"; "liability" -> "e.g. Home loan"; else -> "e.g. Salary" })
                    if (!isDeposit) {
                        QField(
                            label = when (kind) { "liability" -> "Outstanding balance · ₹"; "income", "expense" -> "Amount per period · ₹"; else -> "Current value · ₹" },
                            value = amount,
                            onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                            keyboardType = KeyboardType.Decimal,
                        )
                    }
                    if (isDeposit) {
                        QField(
                            "Original principal / instalment · ₹", principal,
                            { principal = it.filter { c -> c.isDigit() || c == '.' } },
                            keyboardType = KeyboardType.Decimal,
                        )
                        QField("Annual interest rate · %", annualRate, { annualRate = it.filter { c -> c.isDigit() || c == '.' } }, keyboardType = KeyboardType.Decimal)
                        Text("Compounding frequency", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
                        FrequencyChoices(listOf("monthly", "quarterly", "half_yearly", "yearly"), frequency) { frequency = it }
                        DatePickerField("Start date", startDate, { startDate = it })
                        DatePickerField("Maturity date (optional)", maturityDate, { maturityDate = it }, optional = true)
                        Text("Estimated compounding uses the selected frequency. Bank-specific payout rules, tax and premature-withdrawal terms may differ.", color = QlotsColors.Muted, fontSize = 10.sp, lineHeight = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
                    }
                    if (isLoan) {
                        QField("Annual interest rate · %", annualRate, { annualRate = it.filter { c -> c.isDigit() || c == '.' } }, keyboardType = KeyboardType.Decimal)
                        QField("Monthly EMI · ₹", emi, { emi = it.filter { c -> c.isDigit() || c == '.' } }, keyboardType = KeyboardType.Decimal)
                        QField("Loan tenure · months (optional)", tenure, { tenure = it.filter(Char::isDigit) }, keyboardType = KeyboardType.Number, hint = "Used to model when a loan ends in projections.")
                    }
                    if (kind == "asset" && !isDeposit) {
                        QField("Expected annual return · % (optional)", annualRate, { annualRate = it.filter { c -> c.isDigit() || c == '.' } }, keyboardType = KeyboardType.Decimal)
                        QField("Custom growth rate · % (optional)", growthRate, { growthRate = it.filter { c -> c.isDigit() || c == '.' } }, keyboardType = KeyboardType.Decimal, hint = "When set, this overrides the global equity return in projections.")
                    }
                    if (kind == "income" || kind == "expense") {
                        Text("Amount frequency", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 7.dp))
                        FrequencyChoices(listOf("monthly", "weekly", "yearly"), frequency) { frequency = it }
                    }
                    QField("Institution or provider · optional", institution, { institution = it }, placeholder = "Bank, employer, provider…")
                    QField("Notes · optional", notes, { notes = it }, placeholder = "Any extra details…", singleLine = false, hint = "Do not add PINs, passwords, card numbers or account-access credentials.")
                }
                QButton(
                    text = when { busy -> "Saving…"; existing == null -> "Save record"; else -> "Update record" },
                    onClick = {
                        onSave(
                            EntryRecord(
                                id = existing?.id ?: 0,
                                kind = kind,
                                category = category,
                                name = name.trim(),
                                institution = institution.trim(),
                                amount = amount.toDoubleOrNull() ?: 0.0,
                                principal = principal.toDoubleOrNull() ?: 0.0,
                                annualRate = annualRate.toDoubleOrNull() ?: 0.0,
                                frequency = frequency,
                                emi = emi.toDoubleOrNull() ?: 0.0,
                                tenureMonths = tenure.toIntOrNull() ?: 0,
                                startDate = startDate.takeIf { it.isNotBlank() },
                                maturityDate = maturityDate.takeIf { it.isNotBlank() },
                                growthRate = growthRate.toDoubleOrNull() ?: 0.0,
                                notes = notes,
                            ),
                        )
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun FrequencyChoices(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        options.forEach { value -> QPill(labelize(value), selected == value, { onSelect(value) }) }
    }
}

@Composable
private fun DatePickerField(label: String, value: String, onValueChange: (String) -> Unit, optional: Boolean = false) {
    val context = LocalContext.current
    val calendar = remember(value) {
        Calendar.getInstance().apply {
            if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
                runCatching {
                    val pieces = value.split('-')
                    set(pieces[0].toInt(), pieces[1].toInt() - 1, pieces[2].toInt())
                }
            }
        }
    }
    val showPicker = {
        DatePickerDialog(
            context,
            { _, year, month, day -> onValueChange(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)) },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.weight(1f).clickable { showPicker() }) {
            QField(
                label = label,
                value = value.ifBlank { if (optional) "Not set" else "Tap to select" },
                onValueChange = {},
                readOnly = true,
                enabled = false,
                trailing = { Text("▦", color = QlotsColors.Green, modifier = Modifier.padding(end = 11.dp)) },
            )
        }
        if (optional && value.isNotBlank()) {
            TextButton(onClick = { onValueChange("") }) { Text("Clear", color = QlotsColors.Red, fontSize = 11.sp) }
        }
    }
}

private fun Double.toInput(): String = if (this % 1.0 == 0.0) toLong().toString() else this.toString()
