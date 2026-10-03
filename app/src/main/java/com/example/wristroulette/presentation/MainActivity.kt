package com.example.wristroulette.presentation

import android.content.Context
import android.graphics.Paint as AndroidPaint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.example.wristroulette.data.AppPreferencesStore
import com.example.wristroulette.data.CompletedRound
import com.example.wristroulette.data.completeRound
import com.example.wristroulette.model.EUROPEAN_RED_NUMBERS
import com.example.wristroulette.model.EUROPEAN_WHEEL_ORDER
import com.example.wristroulette.model.BetSlip
import com.example.wristroulette.model.BallMotionPhase
import com.example.wristroulette.model.BallPhysicsState
import com.example.wristroulette.model.BallPhysicsTuning
import com.example.wristroulette.model.BallThrow
import com.example.wristroulette.model.BallThrowRejection
import com.example.wristroulette.model.BallThrowTracker
import com.example.wristroulette.model.WheelPhysicsState
import com.example.wristroulette.model.WheelPhysicsPhase
import com.example.wristroulette.model.advanceBallPhysics
import com.example.wristroulette.model.advanceWheelPhysics
import com.example.wristroulette.model.isInBallThrowZone
import com.example.wristroulette.model.normalizeWheelAngle
import com.example.wristroulette.model.pocketCenterRelativeAngleDegrees
import com.example.wristroulette.model.touchAngleRadians
import com.example.wristroulette.model.wheelVelocityFromSwing
import com.example.wristroulette.presentation.theme.WristRouletteTheme
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import androidx.compose.runtime.LaunchedEffect
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import android.os.VibrationEffect


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            WearApp()
        }
    }
}

data class GyroReading(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    val magnitude: Float
        get() = sqrt(x * x + y * y + z * z)
}

@Composable
fun rememberGyroscope(enabled: Boolean = true): GyroReading {

    val context = LocalContext.current

    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    val gyro = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    var reading by remember {
        mutableStateOf(GyroReading())
    }

    DisposableEffect(gyro, enabled) {

        if (gyro == null || !enabled) {
            onDispose { }
        } else {

            val listener = object : SensorEventListener {

                override fun onSensorChanged(event: SensorEvent) {
                    reading = GyroReading(
                        x = event.values[0],
                        y = event.values[1],
                        z = event.values[2]
                    )
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int
                ) {
                    // Nothing needed for our prototype.
                }
            }

            sensorManager.registerListener(
                listener,
                gyro,
                SensorManager.SENSOR_DELAY_GAME
            )

            onDispose {
                sensorManager.unregisterListener(listener)
            }
        }
    }

    return reading
}

@Composable
fun WearApp() {
    WristRouletteTheme {
        val context = LocalContext.current
        val preferencesStore = remember(context) {
            AppPreferencesStore(context.applicationContext)
        }

        var screen by remember {
            mutableStateOf(AppScreen.MAIN)
        }
        var persistedState by remember(preferencesStore) {
            mutableStateOf(preferencesStore.load())
        }
        var betSlip by remember { mutableStateOf(BetSlip()) }

        BackHandler(enabled = screen != AppScreen.MAIN) {
            screen = screen.parent() ?: AppScreen.MAIN
        }

        when (screen) {
            AppScreen.MAIN -> MainScreen(onSelect = { screen = it })
            AppScreen.OPTIONS -> OptionsScreen(
                settings = persistedState.settings,
                session = persistedState.session,
                onSettingsChange = { settings ->
                    preferencesStore.saveSettings(settings)
                    persistedState = persistedState.copy(settings = settings)
                },
                onResetSession = {
                    preferencesStore.resetSession()
                    persistedState = preferencesStore.load()
                    betSlip = BetSlip()
                },
                onBack = { screen = AppScreen.MAIN }
            )
            AppScreen.RNG -> RngScreen(
                settings = persistedState.settings,
                onBack = { screen = AppScreen.MAIN }
            )
            AppScreen.BET_RECORD -> BetRecordScreen(
                session = persistedState.session,
                records = persistedState.records,
                onBack = { screen = AppScreen.MAIN }
            )
            AppScreen.BET -> BetScreen(
                session = persistedState.session,
                betSlip = betSlip,
                onBetSlipChange = { betSlip = it },
                onPlay = { screen = AppScreen.PLAY }
            )
            AppScreen.PLAY -> RouletteScreen(
                buzzDurationMs = persistedState.settings.buzzDuration.durationMs,
                onResult = { winningNumber ->
                    val completedRound = completeRound(
                        session = persistedState.session,
                        records = persistedState.records,
                        betSlip = betSlip,
                        winningNumber = winningNumber
                    )
                    preferencesStore.saveSession(completedRound.session)
                    preferencesStore.saveRecords(completedRound.records)
                    persistedState = persistedState.copy(
                        session = completedRound.session,
                        records = completedRound.records
                    )
                    betSlip = betSlip.clearStakes()
                    completedRound
                }
            )
        }
    }
}
enum class SpinGestureState {
    WAITING,
    ARMED
}

data class SpinGestureResult(
    val state: SpinGestureState,
    val launchStrength: Float? = null
)
@Composable
fun RouletteScreen(
    buzzDurationMs: Long = 50L,
    onResult: (Int) -> CompletedRound
) {
    val context = LocalContext.current

    @Suppress("DEPRECATION")
    val vibrator = remember(context) {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    DisposableEffect(vibrator) {
        onDispose { vibrator.cancel() }
    }

    var acceptLaunchAfterMs by remember {
        mutableLongStateOf(0L)
    }
    var wheelLaunched by remember { mutableStateOf(false) }
    var wheelAngle by remember { mutableFloatStateOf(0f) }
    var wheelAngularVelocity by remember { mutableFloatStateOf(0f) }
    var ballLaunched by remember { mutableStateOf(false) }
    var ballAngle by remember { mutableFloatStateOf(0f) }
    var ballAngularVelocity by remember { mutableFloatStateOf(0f) }
    var ballRadiusFraction by remember {
        mutableFloatStateOf(BallPhysicsTuning.OUTER_TRACK_RADIUS_FRACTION)
    }
    var ballMotionPhase by remember { mutableStateOf(BallMotionPhase.OUTER_TRACK) }
    var ballChaosSeed by remember { mutableIntStateOf(0) }
    var capturedPocketIndex by remember { mutableStateOf<Int?>(null) }
    var completedRound by remember { mutableStateOf<CompletedRound?>(null) }
    var ballThrowFeedback by remember { mutableStateOf("DRAG WHITE ARC") }
    var ballRequestId by remember { mutableIntStateOf(0) }

    val latestBallLaunched = rememberUpdatedState(ballLaunched)
    val latestWheelAngle = rememberUpdatedState(wheelAngle)
    val latestOnResult = rememberUpdatedState(onResult)
    val latestOnBallThrow = rememberUpdatedState<(BallThrow) -> Unit> { throwResult ->
        ballAngle = throwResult.launchAngleDegrees
        ballAngularVelocity = throwResult.angularVelocityDegreesPerSecond
        ballRadiusFraction = BallPhysicsTuning.OUTER_TRACK_RADIUS_FRACTION
        ballMotionPhase = BallMotionPhase.OUTER_TRACK
        ballChaosSeed = Random.nextInt()
        capturedPocketIndex = null
        completedRound = null
        ballLaunched = true
        ballRequestId++
    }
    val latestOnBallThrowFeedback = rememberUpdatedState<(String) -> Unit> { message ->
        ballThrowFeedback = message
    }

    val gyro = rememberGyroscope(enabled = !wheelLaunched)

    var gestureState by remember {
        mutableStateOf(SpinGestureState.WAITING)
    }

    var backswingAxis by remember {
        mutableIntStateOf(0)
    }

    var backswingSign by remember {
        mutableFloatStateOf(0f)
    }

    var requestedSpinStrength by remember {
        mutableFloatStateOf(0f)
    }

    var spinRequestId by remember {
        mutableIntStateOf(0)
    }


    var peakMagnitude by remember { mutableFloatStateOf(0f) }

    var minX by remember { mutableFloatStateOf(0f) }
    var maxX by remember { mutableFloatStateOf(0f) }

    var minY by remember { mutableFloatStateOf(0f) }
    var maxY by remember { mutableFloatStateOf(0f) }

    var minZ by remember { mutableFloatStateOf(0f) }
    var maxZ by remember { mutableFloatStateOf(0f) }

    var armedAtMs by remember {
        mutableLongStateOf(0L)
    }

    var cooldownUntilMs by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(gyro.x, gyro.y, gyro.z, wheelLaunched) {

        if (wheelLaunched) return@LaunchedEffect

        if (gyro.magnitude > peakMagnitude) peakMagnitude = gyro.magnitude
        if (gyro.x < minX) minX = gyro.x
        if (gyro.x > maxX) maxX = gyro.x
        if (gyro.y < minY) minY = gyro.y
        if (gyro.y > maxY) maxY = gyro.y
        if (gyro.z < minZ) minZ = gyro.z
        if (gyro.z > maxZ) maxZ = gyro.z

        val nowMs = SystemClock.elapsedRealtime()

        val values = floatArrayOf(
            gyro.x,
            gyro.y,
            gyro.z
        )

        val dominantAxis = values.indices.maxBy {
            kotlin.math.abs(values[it])
        }

        val dominantValue = values[dominantAxis]

        val armThreshold = 4.0f
        val launchThreshold = 5.0f

        val armTimeoutMs = 1200L
        val cooldownMs = 700L

        // Ignore follow-through immediately after a successful spin.
        if (nowMs < cooldownUntilMs) {
            gestureState = SpinGestureState.WAITING
            return@LaunchedEffect
        }

        when (gestureState) {

            SpinGestureState.WAITING -> {

                if (
                    kotlin.math.abs(dominantValue) >
                    armThreshold
                ) {
                    backswingAxis = dominantAxis

                    backswingSign =
                        kotlin.math.sign(dominantValue)

                    armedAtMs = nowMs

                    // Tiny tactile cue: "backswing accepted — throw now"
                    if (buzzDurationMs > 0L) {
                        vibrator.vibrate(
                            VibrationEffect.createOneShot(
                                buzzDurationMs,
                                VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        )
                    }

                    // Don't let the vibration itself influence our gyro detector.
                    acceptLaunchAfterMs = nowMs + if (buzzDurationMs > 0L) {
                        buzzDurationMs + 70L
                    } else {
                        0L
                    }

                    gestureState =
                        SpinGestureState.ARMED
                }
            }

            SpinGestureState.ARMED -> {
                if (nowMs < acceptLaunchAfterMs) {
                    return@LaunchedEffect
                }
                // Give up if the expected forward swing never arrives.
                if (nowMs - armedAtMs > armTimeoutMs) {

                    gestureState =
                        SpinGestureState.WAITING

                    return@LaunchedEffect
                }

                val swingValue =
                    values[backswingAxis]

                val oppositeDirection =
                    kotlin.math.sign(swingValue) !=
                            backswingSign

                if (
                    oppositeDirection &&
                    kotlin.math.abs(swingValue) >
                    launchThreshold
                ) {

                    requestedSpinStrength = swingValue

                    spinRequestId++
                    wheelLaunched = true

                    cooldownUntilMs =
                        nowMs + cooldownMs

                    gestureState =
                        SpinGestureState.WAITING
                }
            }
        }
    }

    LaunchedEffect(spinRequestId) {

        if (spinRequestId == 0) {
            return@LaunchedEffect
        }

        var physicsState = WheelPhysicsState(
            angleDegrees = wheelAngle,
            angularVelocityDegreesPerSecond = wheelVelocityFromSwing(
                requestedSpinStrength
            )
        )
        wheelAngularVelocity = physicsState.angularVelocityDegreesPerSecond
        var previousFrameNanos = withFrameNanos { it }

        while (physicsState.isSpinning) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
            previousFrameNanos = frameNanos
            physicsState = advanceWheelPhysics(
                state = physicsState,
                deltaSeconds = deltaSeconds,
                phase = if (latestBallLaunched.value) {
                    WheelPhysicsPhase.BALL_IN_PLAY
                } else {
                    WheelPhysicsPhase.WAITING_FOR_BALL
                }
            )
            wheelAngle = physicsState.angleDegrees
            wheelAngularVelocity = physicsState.angularVelocityDegreesPerSecond
        }
    }

    LaunchedEffect(ballRequestId) {
        if (ballRequestId == 0) return@LaunchedEffect

        var physicsState = BallPhysicsState(
            angleDegrees = ballAngle,
            angularVelocityDegreesPerSecond = ballAngularVelocity,
            chaosSeed = ballChaosSeed
        )
        var previousFrameNanos = withFrameNanos { it }

        while (physicsState.isMoving) {
            val frameNanos = withFrameNanos { it }
            val deltaSeconds = (frameNanos - previousFrameNanos) / 1_000_000_000f
            previousFrameNanos = frameNanos
            physicsState = advanceBallPhysics(
                state = physicsState,
                wheelAngleDegrees = latestWheelAngle.value,
                deltaSeconds = deltaSeconds
            )
            ballAngle = physicsState.angleDegrees
            ballAngularVelocity = physicsState.angularVelocityDegreesPerSecond
            ballRadiusFraction = physicsState.radiusFraction
            ballMotionPhase = physicsState.phase
            capturedPocketIndex = physicsState.capturedPocketIndex
        }
    }

    val winningNumber = capturedPocketIndex?.let(EUROPEAN_WHEEL_ORDER::get)
    LaunchedEffect(winningNumber) {
        if (winningNumber != null && completedRound == null) {
            completedRound = latestOnResult.value(winningNumber)
        }
    }
    val displayedBallAngle = capturedPocketIndex?.let { pocketIndex ->
        normalizeWheelAngle(
            wheelAngle + pocketCenterRelativeAngleDegrees(pocketIndex)
        )
    } ?: ballAngle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .ballThrowGesture(
                enabled = wheelLaunched && !ballLaunched,
                onThrow = latestOnBallThrow,
                onFeedback = latestOnBallThrowFeedback
            ),
        contentAlignment = Alignment.Center
    ) {

        RouletteWheel(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .graphicsLayer {
                    rotationZ = wheelAngle
                }
        )

        if (ballLaunched) {
            OuterTrackBall(
                angleDegrees = displayedBallAngle,
                radiusFraction = ballRadiusFraction,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else if (wheelLaunched) {
            BallThrowGuide(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        }

        //
        // LIVE GYROSCOPE DISPLAY
        //
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.70f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(
                    horizontal = 10.dp,
                    vertical = 5.dp
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            if (!wheelLaunched) {
                Text(
                    text = "X %+.1f / %+.1f".format(minX, maxX),
                    fontSize = 9.sp
                )

                Text(
                    text = "Y %+.1f / %+.1f".format(minY, maxY),
                    fontSize = 9.sp
                )

                Text(
                    text = "Z %+.1f / %+.1f".format(minZ, maxZ),
                    fontSize = 9.sp
                )

                Text(
                    text = "|ω| %.1f   PEAK %.1f".format(
                        gyro.magnitude,
                        peakMagnitude
                    ),
                    fontSize = 9.sp
                )
            } else {
                Text(
                    text = "WHEEL %+.0f°/s".format(wheelAngularVelocity),
                    fontSize = 9.sp
                )
                if (ballLaunched) {
                    Text(
                        text = "BALL %+.0f°/s".format(ballAngularVelocity),
                        fontSize = 9.sp
                    )
                }
            }
            Text(
                text = when {
                    capturedPocketIndex != null -> "RESULT $winningNumber"
                    ballMotionPhase == BallMotionPhase.DROPPING -> "BALL DROPPING"
                    ballLaunched -> "BALL ON OUTER TRACK"
                    wheelLaunched -> ballThrowFeedback
                    gestureState == SpinGestureState.ARMED -> "SWING!"
                    else -> "DRAW BACK"
                },
                fontSize = 11.sp
            )
        }

        if (winningNumber != null) {
            ResultDisplay(
                number = winningNumber,
                completedRound = completedRound,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        //
        // Temporary spin button.
        //
        if (!wheelLaunched) {
            Button(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),

                onClick = {

                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            500L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )

                    requestedSpinStrength = 12f
                    spinRequestId++
                    wheelLaunched = true
                    gestureState = SpinGestureState.WAITING
                }
            ) {
                Text("SPIN")
            }
        }
    }
}

private fun Modifier.ballThrowGesture(
    enabled: Boolean,
    onThrow: State<(BallThrow) -> Unit>,
    onFeedback: State<(String) -> Unit>
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput

    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Main
        )
        if (!isInBallThrowZone(
                x = down.position.x,
                y = down.position.y,
                width = size.width.toFloat(),
                height = size.height.toFloat()
            )
        ) {
            onFeedback.value("START ON WHITE ARC")
            return@awaitEachGesture
        }

        val tracker = BallThrowTracker()
        tracker.addSample(
            timeMillis = down.uptimeMillis,
            wrappedAngleRadians = touchAngleRadians(
                x = down.position.x,
                y = down.position.y,
                centerX = size.width / 2f,
                centerY = size.height / 2f
            )
        )
        down.consume()

        var pointerPressed = true
        while (pointerPressed) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            tracker.addSample(
                timeMillis = change.uptimeMillis,
                wrappedAngleRadians = touchAngleRadians(
                    x = change.position.x,
                    y = change.position.y,
                    centerX = size.width / 2f,
                    centerY = size.height / 2f
                )
            )
            change.consume()
            pointerPressed = change.pressed
        }

        val estimate = tracker.estimate()
        val throwResult = estimate.throwResult
        if (throwResult != null) {
            onFeedback.value("THROW ACCEPTED")
            onThrow.value(throwResult)
        } else {
            val detail = "${estimate.sampleCount} SAMPLES • ${estimate.durationMillis}ms"
            val reason = when (estimate.rejection) {
                BallThrowRejection.NOT_ENOUGH_SAMPLES -> "NEED MORE MOTION"
                BallThrowRejection.TOO_SHORT -> "SWIPE A LITTLE LONGER"
                BallThrowRejection.TOO_LITTLE_MOVEMENT -> "MOVE FARTHER"
                BallThrowRejection.TOO_SLOW -> "SWIPE FASTER"
                null -> "TRY AGAIN"
            }
            onFeedback.value("$reason • $detail")
        }
    }
}

@Composable
private fun BallThrowGuide(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension *
            BallPhysicsTuning.OUTER_TRACK_RADIUS_FRACTION * 2f
        val topLeft = Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        drawArc(
            color = Color.White.copy(alpha = 0.72f),
            startAngle = BallPhysicsTuning.THROW_ZONE_START_DEGREES - 90f,
            sweepAngle = BallPhysicsTuning.THROW_ZONE_SWEEP_DEGREES,
            useCenter = false,
            topLeft = topLeft,
            size = Size(diameter, diameter),
            style = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
private fun OuterTrackBall(
    angleDegrees: Float,
    radiusFraction: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * radiusFraction
        val radians = Math.toRadians((angleDegrees - 90f).toDouble())
        val center = Offset(
            x = size.width / 2f + cos(radians).toFloat() * radius,
            y = size.height / 2f + sin(radians).toFloat() * radius
        )
        drawCircle(
            color = Color.Black.copy(alpha = 0.65f),
            radius = 6.dp.toPx(),
            center = center + Offset(1.5.dp.toPx(), 1.5.dp.toPx())
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun ResultDisplay(
    number: Int,
    completedRound: CompletedRound?,
    modifier: Modifier = Modifier
) {
    val resultColor = when {
        number == 0 -> Color(0xFF20A647)
        number in EUROPEAN_RED_NUMBERS -> Color(0xFFE53935)
        else -> Color.White
    }
    val colorName = when {
        number == 0 -> "GREEN"
        number in EUROPEAN_RED_NUMBERS -> "RED"
        else -> "BLACK"
    }

    Column(
        modifier = modifier
            .background(
                color = Color.Black.copy(alpha = 0.88f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "RESULT", fontSize = 8.sp, color = Color.LightGray)
        Text(text = number.toString(), fontSize = 25.sp, color = resultColor)
        Text(text = colorName, fontSize = 9.sp, color = resultColor)
        completedRound?.let { result ->
            val settlement = result.settlement
            val outcomeText = when {
                settlement.totalStake == 0 -> "NO BET"
                settlement.netCredits > 0 -> "WIN +${settlement.netCredits}"
                settlement.netCredits < 0 -> "LOSS ${settlement.netCredits}"
                else -> "PUSH 0"
            }
            val outcomeColor = when {
                settlement.netCredits > 0 -> Color(0xFF66BB6A)
                settlement.netCredits < 0 -> Color(0xFFEF5350)
                else -> Color.LightGray
            }
            Text(
                text = outcomeText,
                fontSize = 10.sp,
                color = outcomeColor,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Text(
                text = "BANK ${result.session.bank}",
                fontSize = 9.sp,
                color = Color(0xFFD7B56D)
            )
            if (result.sessionEnded) {
                Text(
                    text = "SESSION OVER",
                    fontSize = 8.sp,
                    color = Color(0xFFEF5350)
                )
            }
        }
    }
}

@Composable
fun RouletteWheel(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {

        val pocketCount = 37
        val sectorAngle = 360f / pocketCount

        val red = Color(0xFFC62828)
        val black = Color(0xFF151515)
        val green = Color(0xFF087F23)

        EUROPEAN_WHEEL_ORDER.forEachIndexed { index, number ->

            val color = when {
                number == 0 -> green
                number in EUROPEAN_RED_NUMBERS -> red
                else -> black
            }

            drawArc(
                color = color,
                startAngle =
                    -90f + index * sectorAngle,
                sweepAngle = sectorAngle,
                useCenter = true
            )
        }

        val labelPaint = AndroidPaint().apply {
            color = Color.White.toArgb()
            textAlign = AndroidPaint.Align.CENTER
            textSize = 6.dp.toPx()
            isAntiAlias = true
            isFakeBoldText = true
        }
        val labelRadius = size.minDimension * 0.415f
        val baselineOffset = -(labelPaint.ascent() + labelPaint.descent()) / 2f
        EUROPEAN_WHEEL_ORDER.forEachIndexed { index, number ->
            val centerAngle = -90f + (index + 0.5f) * sectorAngle
            val radians = Math.toRadians(centerAngle.toDouble())
            val x = center.x + cos(radians).toFloat() * labelRadius
            val y = center.y + sin(radians).toFloat() * labelRadius
            drawContext.canvas.nativeCanvas.drawText(
                number.toString(),
                x,
                y + baselineOffset,
                labelPaint
            )
        }

        drawCircle(
            color = Color(0xFF5A3A20),
            radius = size.minDimension * 0.30f
        )

        drawCircle(
            color = Color(0xFFD7B56D),
            radius = size.minDimension * 0.20f
        )

        drawCircle(
            color = Color(0xFF292929),
            radius = size.minDimension * 0.07f
        )
    }
}

@WearPreviewDevices
@Composable
fun DefaultPreview() {
    WearApp()
}
