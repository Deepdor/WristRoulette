package com.example.wristroulette.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.wristroulette.data.SessionState
import com.example.wristroulette.model.BetSlip
import com.example.wristroulette.model.BetType
import com.example.wristroulette.model.canStartStraightNumberSelection
import com.example.wristroulette.model.stakeIncrementForPress
import com.example.wristroulette.model.straightNumberForPosition
import kotlin.math.sqrt

@Composable
fun BetScreen(
    session: SessionState,
    betSlip: BetSlip,
    onBetSlipChange: (BetSlip) -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val availableCredits = betSlip.availableCredits(session.bank)
    val currentNumberSelection = rememberUpdatedState<(Int) -> Unit> { number ->
        onBetSlipChange(betSlip.selectStraightNumber(number))
    }
    val addStake: (BetType, Int) -> Unit = { type, requestedCredits ->
        onBetSlipChange(
            betSlip.addStake(
                type = type,
                requestedCredits = requestedCredits,
                bank = session.bank
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .straightNumberEdgeSelector(currentNumberSelection)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(31.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "BANK ${session.bank}  •  AVAILABLE $availableCredits",
                color = Color(0xFFD7B56D),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "STAKED ${betSlip.totalStake}  •  TAP +1  HOLD +5/+20",
                color = Color.LightGray,
                fontSize = 8.sp
            )
        }

        StakeRow(
            leftType = BetType.RED,
            rightType = BetType.BLACK,
            betSlip = betSlip,
            enabled = availableCredits > 0,
            onStake = addStake,
            modifier = Modifier.weight(1f)
        )
        StakeRow(
            leftType = BetType.ODD,
            rightType = BetType.EVEN,
            betSlip = betSlip,
            enabled = availableCredits > 0,
            onStake = addStake,
            modifier = Modifier.weight(1f)
        )
        StakeRow(
            leftType = BetType.LOW,
            rightType = BetType.HIGH,
            betSlip = betSlip,
            enabled = availableCredits > 0,
            onStake = addStake,
            modifier = Modifier.weight(1f)
        )

        StakeTarget(
            label = "STRAIGHT ${betSlip.straightNumber}",
            stake = betSlip.stakeFor(BetType.STRAIGHT),
            color = Color(0xFF315B78),
            enabled = availableCredits > 0,
            onStake = { credits -> addStake(BetType.STRAIGHT, credits) },
            supportingText = "TRACE DISPLAY EDGE TO SELECT",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.08f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.02f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            NavigationTarget(
                label = "PLAY",
                enabled = session.bank > 0,
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
            NavigationTarget(
                label = "BET 0",
                onClick = {
                    onBetSlipChange(betSlip.clearStakes())
                },
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun StakeRow(
    leftType: BetType,
    rightType: BetType,
    betSlip: BetSlip,
    enabled: Boolean,
    onStake: (BetType, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        StakeTarget(
            label = leftType.displayName,
            stake = betSlip.stakeFor(leftType),
            color = colorForBetType(leftType),
            enabled = enabled,
            onStake = { credits -> onStake(leftType, credits) },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
        StakeTarget(
            label = rightType.displayName,
            stake = betSlip.stakeFor(rightType),
            color = colorForBetType(rightType),
            enabled = enabled,
            onStake = { credits -> onStake(rightType, credits) },
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        )
    }
}

@Composable
private fun StakeTarget(
    label: String,
    stake: Int,
    color: Color,
    enabled: Boolean,
    onStake: (Int) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnStake = rememberUpdatedState(onStake)
    val displayedColor = when {
        !enabled -> color.copy(alpha = 0.42f)
        pressed -> color.copy(alpha = 0.72f)
        else -> color
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(displayedColor)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val up = try {
                        waitForUpOrCancellation()
                    } finally {
                        pressed = false
                    }
                    if (up != null) {
                        currentOnStake.value(
                            stakeIncrementForPress(up.uptimeMillis - down.uptimeMillis)
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$label  •  $stake",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 7.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NavigationTarget(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (enabled) Color(0xFF424242) else Color(0xFF202020)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun colorForBetType(type: BetType): Color = when (type) {
    BetType.RED -> Color(0xFFC62828)
    BetType.BLACK -> Color(0xFF151515)
    BetType.ODD,
    BetType.LOW -> Color(0xFF5A3A20)
    BetType.EVEN,
    BetType.HIGH -> Color(0xFF303030)
    BetType.STRAIGHT -> Color(0xFF315B78)
}

private fun Modifier.straightNumberEdgeSelector(
    onNumberSelected: State<(Int) -> Unit>
): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Initial
        )
        val startsOnEdge = canStartStraightNumberSelection(
            x = down.position.x,
            y = down.position.y,
            width = size.width.toFloat(),
            height = size.height.toFloat()
        )
        var selecting = false
        var pressed = true

        while (pressed) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            val distance = sqrt(dx * dx + dy * dy)

            if (startsOnEdge && !selecting && distance >= viewConfiguration.touchSlop) {
                selecting = true
            }
            if (selecting) {
                onNumberSelected.value(
                    straightNumberForPosition(
                        x = change.position.x,
                        y = change.position.y,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
                )
                change.consume()
            }
            pressed = change.pressed
        }
    }
}
