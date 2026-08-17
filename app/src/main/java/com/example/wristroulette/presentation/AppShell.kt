package com.example.wristroulette.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text

enum class AppScreen {
    MAIN,
    OPTIONS,
    RNG,
    BET_RECORD,
    BET,
    PLAY
}

fun AppScreen.parent(): AppScreen? = when (this) {
    AppScreen.MAIN -> null
    AppScreen.OPTIONS,
    AppScreen.RNG,
    AppScreen.BET_RECORD,
    AppScreen.BET -> AppScreen.MAIN
    AppScreen.PLAY -> AppScreen.BET
}

@Composable
fun MainScreen(
    onSelect: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color.Black)
    ) {
        MainRegion(
            label = "OPTIONS",
            color = Color(0xFF303030),
            onClick = { onSelect(AppScreen.OPTIONS) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
        ) {
            MainRegion(
                label = "RNG\nRED",
                color = Color(0xFFC62828),
                onClick = { onSelect(AppScreen.RNG) },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
            MainRegion(
                label = "PLAY\nBLACK",
                color = Color(0xFF111111),
                onClick = { onSelect(AppScreen.BET) },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }

        MainRegion(
            label = "BET RECORD",
            color = Color(0xFF303030),
            onClick = { onSelect(AppScreen.BET_RECORD) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.28f)
        )
    }
}

@Composable
private fun MainRegion(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
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
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            modifier = Modifier.padding(vertical = 12.dp),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        Button(onClick = onBack) {
            Text("BACK")
        }
    }
}

@Composable
fun BetScreen(
    onPlay: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "BET",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "BANK  100",
            modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            color = Color(0xFFD7B56D),
            fontSize = 14.sp
        )
        Button(onClick = onPlay) {
            Text("PLAY")
        }
        Button(onClick = onBack) {
            Text("BACK")
        }
    }
}
