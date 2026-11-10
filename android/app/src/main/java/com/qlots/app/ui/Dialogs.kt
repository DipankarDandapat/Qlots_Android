package com.qlots.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qlots.app.data.GoalItem
import java.util.Locale

fun decimalOnly(text: String): String = text.filter { it.isDigit() || it == '.' }

fun numText(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

@Composable
fun FormDialog(
    eyebrow: String,
    title: String,
    confirmLabel: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(0.94f).heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = QlotsColors.Canvas) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(eyebrow.uppercase(Locale.getDefault()), color = QlotsColors.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                        Text(title, color = QlotsColors.Deep, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    TextButton(onClick = onDismiss) { Text("Close", color = QlotsColors.Green) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(top = 10.dp)) { content() }
                QButton(if (busy) "Saving…" else confirmLabel, onConfirm, enabled = !busy, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
fun SnapshotDialog(busy: Boolean, currentAssets: Double, currentLiabilities: Double, onDismiss: () -> Unit, onSave: (String?, Double?, Double?, String) -> Unit) {
    var date by rememberSaveable { mutableStateOf(todayIso()) }
    var assets by rememberSaveable { mutableStateOf("") }
    var liabilities by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    FormDialog("Net-worth timeline", "Record a snapshot", "Save snapshot", busy, onDismiss, {
        onSave(date, assets.toDoubleOrNull(), liabilities.toDoubleOrNull(), note)
    }) {
        Text("Save today's position, or enter the figures for an earlier date to fill the timeline. Leave amounts blank to use your current records.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        DatePickerField("Date", date, { date = it })
        QField("Total assets · ₹", assets, { assets = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "Now ${money(currentAssets)}")
        QField("Total liabilities · ₹", liabilities, { liabilities = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, placeholder = "Now ${money(currentLiabilities)}")
        QField("Note · optional", note, { note = it }, placeholder = "e.g. After bonus")
    }
}

private val GoalKinds = listOf("emergency_fund" to "Emergency fund", "home_deposit" to "Home deposit", "education" to "Education fund", "custom" to "Other goal")

data class GoalSubmission(
    val id: Int?, val name: String, val kind: String, val target: Double, val current: Double, val monthly: Double,
    val expectedReturn: Double, val targetDate: String?, val autoLink: Boolean,
)

@Composable
fun GoalDialog(existing: GoalItem?, busy: Boolean, suggestedTarget: Double?, onDismiss: () -> Unit, onSave: (GoalSubmission) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: "emergency_fund") }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "Emergency fund") }
    var target by rememberSaveable { mutableStateOf(existing?.target?.let { numText(it) } ?: suggestedTarget?.let { numText(it) }.orEmpty()) }
    var current by rememberSaveable { mutableStateOf(existing?.current?.takeIf { it > 0 }?.let { numText(it) }.orEmpty()) }
    var monthly by rememberSaveable { mutableStateOf(existing?.monthly?.takeIf { it > 0 }?.let { numText(it) }.orEmpty()) }
    var returnPct by rememberSaveable { mutableStateOf(existing?.expectedReturn?.takeIf { it != 0.0 }?.let { numText(it) }.orEmpty()) }
    var date by rememberSaveable { mutableStateOf(existing?.targetDate.orEmpty()) }
    var autoLink by rememberSaveable { mutableStateOf(existing?.autoLink ?: false) }
    FormDialog(if (existing == null) "New goal" else "Edit goal", if (existing == null) "What are you saving for?" else existing.name, "Save goal", busy, onDismiss, {
        onSave(GoalSubmission(existing?.id, name, kind, target.toDoubleOrNull() ?: 0.0, current.toDoubleOrNull() ?: 0.0, monthly.toDoubleOrNull() ?: 0.0, returnPct.toDoubleOrNull() ?: 0.0, date.takeIf { it.isNotBlank() }, autoLink))
    }) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            GoalKinds.forEach { (value, label) ->
                QPill(label, kind == value, {
                    kind = value
                    if (name.isBlank() || GoalKinds.any { it.second == name }) name = label
                })
            }
        }
        QField("Goal name", name, { name = it })
        QField("Target amount · ₹", target, { target = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, hint = if (kind == "emergency_fund" && suggestedTarget != null) "Suggested from your expenses: ${money(suggestedTarget)} (6 months)" else null)
        if (kind == "emergency_fund") {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Track from my cash balances", color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Progress follows your cash and bank records automatically.", color = QlotsColors.Muted, fontSize = 10.sp)
                }
                Switch(checked = autoLink, onCheckedChange = { autoLink = it }, colors = SwitchDefaults.colors(checkedTrackColor = QlotsColors.Green))
            }
        }
        if (!(kind == "emergency_fund" && autoLink)) QField("Saved so far · ₹", current, { current = decimalOnly(it) }, keyboardType = KeyboardType.Decimal)
        QField("Monthly contribution · ₹", monthly, { monthly = decimalOnly(it) }, keyboardType = KeyboardType.Decimal)
        QField("Expected return · % a year (optional)", returnPct, { returnPct = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, hint = "Leave blank if the money sits in savings.")
        DatePickerField("Target date (optional)", date, { date = it }, optional = true)
    }
}

@Composable
fun ProgressDialog(goal: GoalItem, busy: Boolean, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var amount by rememberSaveable { mutableStateOf(numText(goal.current)) }
    FormDialog("Update progress", goal.name, "Save progress", busy, onDismiss, { onSave(amount.toDoubleOrNull() ?: goal.current) }) {
        Text("Enter the total amount you have set aside for this goal today. Qlots adds it to the goal's progress chart.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        QField("Saved so far · ₹", amount, { amount = decimalOnly(it) }, keyboardType = KeyboardType.Decimal, hint = "Target ${money(goal.target)}")
    }
}

@Composable
fun SavePlanDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var label by rememberSaveable { mutableStateOf("") }
    FormDialog("Projection tracking", "Save today's projection", "Save projection", busy, onDismiss, { onSave(label) }) {
        Text("Saves the base, conservative and optimistic 5-year paths from your records and assumptions today. As you record snapshots, Qlots compares them with this plan.", color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(bottom = 10.dp))
        QField("Label (optional)", label, { label = it }, placeholder = "e.g. After salary raise")
    }
}
