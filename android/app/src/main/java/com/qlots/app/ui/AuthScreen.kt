package com.qlots.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AuthScreen(busy: Boolean, onSubmit: (Boolean, String, String, String) -> Unit) {
    var isRegister by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(QlotsColors.Canvas)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            BrandMark(size = 76.dp)
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 10.dp)) {
                Text("Qlots", color = QlotsColors.Deep, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.5).sp)
                Text(".", color = QlotsColors.Green, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold)
            }
            Text(
                "Your financial picture, together.",
                modifier = Modifier.padding(top = 12.dp),
                color = QlotsColors.Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                "Track what you own, what you owe, and how your plans could grow.",
                modifier = Modifier.padding(top = 7.dp, bottom = 20.dp),
                color = QlotsColors.Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
            )
            QCard {
                if (isRegister) {
                    QField("Your name", name, { name = it }, placeholder = "e.g. Priya Sharma")
                }
                QField("Email address", email, { email = it.trimStart() }, placeholder = "name@example.com", keyboardType = KeyboardType.Email)
                QField(
                    label = "Password",
                    value = password,
                    onValueChange = { password = it },
                    placeholder = if (isRegister) "At least 10 characters" else "Your password",
                    password = true,
                    hint = if (isRegister) "Use at least 10 characters." else null,
                )
                QButton(
                    text = when {
                        busy -> "Please wait…"
                        isRegister -> "Create my account"
                        else -> "Sign in"
                    },
                    enabled = !busy,
                    onClick = { onSubmit(isRegister, name, email, password) },
                )
                androidx.compose.material3.TextButton(
                    onClick = {
                        isRegister = !isRegister
                        password = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (isRegister) "Already have an account? Sign in" else "New to Qlots? Create an account",
                        color = QlotsColors.Green,
                    )
                }
            }
            Text(
                "Your financial information is sensitive. Use a unique password. Qlots is a planning tool, not financial advice.",
                modifier = Modifier.padding(top = 18.dp, bottom = 20.dp),
                color = QlotsColors.Muted,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
