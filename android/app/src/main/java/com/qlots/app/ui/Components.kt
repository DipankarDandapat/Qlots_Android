package com.qlots.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.R
import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Image(
        painter = painterResource(R.mipmap.ic_launcher_round),
        contentDescription = "Qlots",
        modifier = modifier.size(size).clip(CircleShape),
    )
}

@Composable
fun QCard(
    modifier: Modifier = Modifier,
    containerColor: Color = QlotsColors.Card,
    contentColor: Color = QlotsColors.Ink,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (containerColor == QlotsColors.Card) QlotsColors.Border else containerColor),
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun QButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp),
        shape = RoundedCornerShape(15.dp),
        colors = if (secondary) ButtonDefaults.buttonColors(
            containerColor = QlotsColors.Mint,
            contentColor = QlotsColors.Deep,
            disabledContainerColor = QlotsColors.Mint.copy(alpha = 0.6f),
        ) else ButtonDefaults.buttonColors(
            containerColor = QlotsColors.Green,
            contentColor = Color.White,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun QSection(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, color = QlotsColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = QlotsColors.Green, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
        }
    }
}

@Composable
fun QPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) QlotsColors.Deep else QlotsColors.Card,
        contentColor = if (selected) Color.White else QlotsColors.Muted,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, QlotsColors.Border),
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun QField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(label, color = QlotsColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            readOnly = readOnly,
            enabled = enabled,
            placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder, color = QlotsColors.Muted.copy(alpha = 0.65f)) }) else null,
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType),
            trailingIcon = trailing,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = QlotsColors.Green,
                unfocusedBorderColor = QlotsColors.Border,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                disabledBorderColor = QlotsColors.Border,
                disabledContainerColor = Color.White,
                disabledTextColor = QlotsColors.Ink,
                disabledTrailingIconColor = QlotsColors.Green,
                cursorColor = QlotsColors.Green,
            ),
        )
        if (!hint.isNullOrBlank()) {
            Text(hint, color = QlotsColors.Muted, fontSize = 11.sp, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
        }
        Spacer(Modifier.height(9.dp))
    }
}

@Composable
fun InlineMetric(label: String, value: String, valueColor: Color = QlotsColors.Ink, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, color = QlotsColors.Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun EntryKindIcon(kind: String, modifier: Modifier = Modifier) {
    val isNegative = kind == "liability" || kind == "expense"
    val icon = when (kind) {
        "liability" -> Icons.Rounded.AccountBalance
        "expense"  -> Icons.Rounded.ArrowDownward
        "income"   -> Icons.Rounded.ArrowUpward
        else       -> Icons.Rounded.Add
    }
    Box(
        modifier.size(37.dp).background(
            if (isNegative) QlotsColors.RedBg else QlotsColors.Mint, CircleShape
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = kind,
            tint = if (isNegative) QlotsColors.Red else QlotsColors.Green,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_round),
            contentDescription = "Qlots",
            modifier = Modifier.size(48.dp).clip(CircleShape),
        )
        Text(title, modifier = Modifier.padding(top = 12.dp), color = QlotsColors.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(body, modifier = Modifier.padding(top = 5.dp), color = QlotsColors.Muted, fontSize = 12.sp, lineHeight = 18.sp)
    }
}

fun money(value: Double, compact: Boolean = false): String {
    if (!value.isFinite()) return "₹0"
    if (compact) {
        val absolute = kotlin.math.abs(value)
        val formatter = DecimalFormat("0.##")
        return when {
            absolute >= 10_000_000 -> "₹${formatter.format(value / 10_000_000)} Cr"
            absolute >= 100_000 -> "₹${formatter.format(value / 100_000)} L"
            absolute >= 1_000 -> "₹${formatter.format(value / 1_000)} K"
            else -> "₹${NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN")).format(value.roundToWhole())}"
        }
    }
    val fmt = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
    fmt.maximumFractionDigits = 0
    return "₹${fmt.format(value.roundToWhole())}"
}

private fun Double.roundToWhole(): Long = kotlin.math.round(this).toLong()

fun labelize(raw: String): String = raw.replace('_', ' ').split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
    when (word.lowercase()) {
        "emi" -> "EMI"
        "fd" -> "FD"
        "rd" -> "RD"
        else -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
