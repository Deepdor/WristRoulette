package com.example.wristroulette.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.wristroulette.data.AppSettings
import com.example.wristroulette.data.SessionState

@Composable
fun OptionsScreen(
    settings: AppSettings,
    session: SessionState,
    onSettingsChange: (AppSettings) -> Unit,
    onResetSession: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmingReset by remember { mutableStateOf(false) }
    var resetCompleted by remember { mutableStateOf(false) }

    BackHandler(enabled = confirmingReset) {
        confirmingReset = false
    }

    if (confirmingReset) {
        ResetSessionConfirmation(
            onConfirm = {
                onResetSession()
                confirmingReset = false
                resetCompleted = true
            },
            onCancel = { confirmingReset = false },
            modifier = modifier
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "OPTIONS",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        OptionButton(
            title = "ARMING BUZZ",
            value = if (settings.buzzDuration.durationMs == 0L) {
                settings.buzzDuration.displayName
            } else {
                "${settings.buzzDuration.displayName}  ${settings.buzzDuration.durationMs} ms"
            },
            onClick = {
                resetCompleted = false
                onSettingsChange(
                    settings.copy(buzzDuration = settings.buzzDuration.next())
                )
            }
        )

        OptionButton(
            title = "RNG ROLL",
            value = "${settings.rngRollLength.displayName}  ${settings.rngRollLength.durationMs / 1_000} s",
            onClick = {
                resetCompleted = false
                onSettingsChange(
                    settings.copy(rngRollLength = settings.rngRollLength.next())
                )
            }
        )

        OptionButton(
            title = "RNG SPAN",
            value = settings.rngSpan.displayName,
            onClick = {
                resetCompleted = false
                onSettingsChange(settings.copy(rngSpan = settings.rngSpan.next()))
            }
        )

        Text(
            text = "BANK ${session.bank}  •  SPINS ${session.spinCount}",
            color = Color(0xFFD7B56D),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        if (resetCompleted) {
            Text(
                text = "SESSION RESET",
                color = Color(0xFF66BB6A),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Button(
            onClick = {
                resetCompleted = false
                confirmingReset = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("RESET BANK / SESSION", textAlign = TextAlign.Center)
        }

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }

        Spacer(modifier = Modifier.height(18.dp))
    }
}

@Composable
private fun OptionButton(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResetSessionConfirmation(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "RESET SESSION?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = "Bank returns to 100 and the current spin count is cleared.",
            modifier = Modifier.padding(vertical = 10.dp),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Button(onClick = onConfirm) {
            Text("CONFIRM")
        }
        Button(onClick = onCancel) {
            Text("CANCEL")
        }
    }
}
