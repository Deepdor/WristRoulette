package com.example.wristroulette.presentation

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.withRotation
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.example.wristroulette.data.AppSettings
import com.example.wristroulette.model.EUROPEAN_RED_NUMBERS
import com.example.wristroulette.model.RngEngine
import com.example.wristroulette.model.RngSelection
import com.example.wristroulette.model.RngSpan
import com.example.wristroulette.model.normalizeDegrees
import com.example.wristroulette.model.restingRotationForSector
import com.example.wristroulette.model.spinTargetRotation
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RngScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val span = settings.rngSpan
    val sectorValues = remember(span) { span.visualSectorValues() }
    val rngEngine = remember { RngEngine() }
    val wheelRotation = remember(span) {
        Animatable(
            restingRotationForSector(
                sectorIndex = 0,
                sectorCount = sectorValues.size
            )
        )
    }

    var pendingSelection by remember(span) {
        mutableStateOf<RngSelection?>(null)
    }
    var displayedResult by remember(span) {
        mutableStateOf<Int?>(null)
    }
    var isRolling by remember(span) { mutableStateOf(false) }
    var rollRequestId by remember(span) { mutableIntStateOf(0) }

    LaunchedEffect(rollRequestId) {
        if (rollRequestId == 0) return@LaunchedEffect
        val selection = pendingSelection ?: return@LaunchedEffect
        val durationMs = settings.rngRollLength.durationMs
        val fullTurns = maxOf(3, durationMs / 750)

        wheelRotation.animateTo(
            targetValue = spinTargetRotation(
                currentRotation = wheelRotation.value,
                sectorIndex = selection.sectorIndex,
                sectorCount = sectorValues.size,
                fullTurns = fullTurns
            ),
            animationSpec = tween(
                durationMillis = durationMs,
                easing = FastOutSlowInEasing
            )
        )
        wheelRotation.snapTo(normalizeDegrees(wheelRotation.value))
        displayedResult = selection.result
        isRolling = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        RngRotor(
            span = span,
            sectorValues = sectorValues,
            modifier = Modifier
                .fillMaxSize()
                .padding(9.dp)
                .graphicsLayer { rotationZ = wheelRotation.value }
                .clickable(enabled = !isRolling) {
                    pendingSelection = rngEngine.roll(span)
                    displayedResult = null
                    isRolling = true
                    rollRequestId++
                }
        )

        FixedSelectorArrow(modifier = Modifier.fillMaxSize())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (span == RngSpan.EUROPEAN_ROULETTE) "ROULETTE" else span.name,
                color = Color(0xFFD7B56D),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = when {
                    isRolling -> "…"
                    displayedResult != null -> displayedResult.toString()
                    else -> "TAP"
                },
                color = Color.White,
                fontSize = if (displayedResult == null && !isRolling) 16.sp else 28.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (!isRolling && displayedResult == null) {
                Text(
                    text = "TO ROLL",
                    color = Color.LightGray,
                    fontSize = 8.sp
                )
            }
        }

        Button(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 5.dp)
                .size(width = 68.dp, height = 34.dp)
        ) {
            Text("BACK", fontSize = 9.sp)
        }
    }
}

@Composable
private fun RngRotor(
    span: RngSpan,
    sectorValues: List<Int>,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Canvas(modifier = modifier) {
        val sectorAngle = 360f / sectorValues.size
        val wheelRadius = size.minDimension / 2f
        val labelRadius = wheelRadius * 0.83f
        labelPaint.textSize = if (sectorValues.size <= 21) {
            10.sp.toPx()
        } else {
            8.sp.toPx()
        }

        sectorValues.forEachIndexed { index, value ->
            val sectorColor = when {
                value == 0 -> Color(0xFF087F23)
                span == RngSpan.EUROPEAN_ROULETTE && value in EUROPEAN_RED_NUMBERS ->
                    Color(0xFFC62828)
                span == RngSpan.EUROPEAN_ROULETTE -> Color(0xFF151515)
                index % 2 == 1 -> Color(0xFFC62828)
                else -> Color(0xFF151515)
            }
            val startAngle = -90f + index * sectorAngle
            val centerAngle = startAngle + sectorAngle / 2f

            drawArc(
                color = sectorColor,
                startAngle = startAngle,
                sweepAngle = sectorAngle,
                useCenter = true
            )

            val radians = Math.toRadians(centerAngle.toDouble())
            val labelCenter = Offset(
                x = center.x + cos(radians).toFloat() * labelRadius,
                y = center.y + sin(radians).toFloat() * labelRadius
            )

            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                nativeCanvas.withRotation(
                    degrees = centerAngle + 90f,
                    pivotX = labelCenter.x,
                    pivotY = labelCenter.y
                ) {
                    drawText(
                        value.toString(),
                        labelCenter.x,
                        labelCenter.y - (labelPaint.ascent() + labelPaint.descent()) / 2f,
                        labelPaint
                    )
                }
            }
        }

        drawCircle(
            color = Color(0xFF5A3A20),
            radius = wheelRadius * 0.31f
        )
        drawCircle(
            color = Color(0xFFD7B56D),
            radius = wheelRadius * 0.27f
        )
        drawCircle(
            color = Color(0xFF111111),
            radius = wheelRadius * 0.23f
        )
    }
}

@Composable
private fun FixedSelectorArrow(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val halfWidth = 10.dp.toPx()
        val top = 2.dp.toPx()
        val tip = 28.dp.toPx()
        val arrow = Path().apply {
            moveTo(center.x - halfWidth, top)
            lineTo(center.x + halfWidth, top)
            lineTo(center.x, tip)
            close()
        }
        drawPath(path = arrow, color = Color.White)
    }
}
