package com.example.wristroulette.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.wristroulette.data.BetRecords
import com.example.wristroulette.data.SessionState

@Composable
fun BetRecordScreen(
    session: SessionState,
    records: BetRecords,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Text(
            text = "BET RECORD",
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        RecordValue("CURRENT BANK", session.bank)
        RecordValue("SESSION SPINS", session.spinCount)
        RecordValue("WINS", records.wins)
        RecordValue("BIGGEST WIN", records.biggestWin)
        RecordValue("HIGHEST BANK", records.highestBank)
        RecordValue("BEST SESSION SPINS", records.highestSessionSpins)
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }
    }
}

@Composable
private fun RecordValue(
    label: String,
    value: Int
) {
    Text(
        text = "$label  $value",
        color = Color(0xFFD7B56D),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
}
