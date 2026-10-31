package com.qlots.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qlots.app.data.UserProfile

@Composable
fun ProfileDialog(
    user: UserProfile?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDeleteData: () -> Unit,
) {
    var name by remember(user?.id) { mutableStateOf(user?.name.orEmpty()) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your profile", color = QlotsColors.Deep, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text("Signed in as ${user?.email.orEmpty()}", color = QlotsColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 13.dp))
                QField("Display name", name, { name = it }, placeholder = "Your name")
                QField(
                    "New password (optional)", password, { password = it },
                    placeholder = "Leave blank to keep your password",
                    password = true,
                    hint = "Minimum 10 characters if you change it.",
                )
                QButton("Save changes", { onSave(name, password) }, enabled = !busy)
                QButton("Delete all financial records…", onDeleteData, modifier = Modifier.padding(top = 8.dp), secondary = true, enabled = !busy)
                QButton("Sign out", onSignOut, modifier = Modifier.padding(top = 8.dp), secondary = true, enabled = !busy)
                TextButton(onClick = onDeleteAccount, modifier = Modifier.fillMaxWidth().padding(top = 2.dp), enabled = !busy) {
                    Text("Delete account and all data…", color = QlotsColors.Red, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        containerColor = QlotsColors.Card,
    )
}
