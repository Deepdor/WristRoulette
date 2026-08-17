package com.example.wristroulette.presentation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.example.wristroulette.presentation.theme.WristRouletteTheme
import kotlinx.coroutines.launch
import kotlin.math.sqrt
import androidx.compose.runtime.LaunchedEffect
import android.os.SystemClock
import androidx.compose.runtime.mutableLongStateOf
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.annotation.RequiresApi


class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.S)
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
fun rememberGyroscope(): GyroReading {

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

    DisposableEffect(gyro) {

        if (gyro == null) {
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

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun WearApp() {
    WristRouletteTheme {
        RouletteScreen()
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
@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun RouletteScreen() {
    val context = LocalContext.current

    val vibratorManager = remember {
        context.getSystemService(VibratorManager::class.java)
    }

    val vibrator = remember(vibratorManager) {
        vibratorManager.defaultVibrator
    }

    var acceptLaunchAfterMs by remember {
        mutableLongStateOf(0L)
    }
    val wheelRotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    val gyro = rememberGyroscope()

    var gestureState by remember {
        mutableStateOf(SpinGestureState.WAITING)
    }

    var backswingAxis by remember {
        mutableStateOf(0)
    }

    var backswingSign by remember {
        mutableFloatStateOf(0f)
    }

    var backswingPeak by remember {
        mutableFloatStateOf(0f)
    }

    var requestedSpinStrength by remember {
        mutableFloatStateOf(0f)
    }

    var spinRequestId by remember {
        mutableStateOf(0)
    }


    var peakMagnitude by remember { mutableFloatStateOf(0f) }

    var minX by remember { mutableFloatStateOf(0f) }
    var maxX by remember { mutableFloatStateOf(0f) }

    var minY by remember { mutableFloatStateOf(0f) }
    var maxY by remember { mutableFloatStateOf(0f) }

    var minZ by remember { mutableFloatStateOf(0f) }
    var maxZ by remember { mutableFloatStateOf(0f) }

    if (gyro.magnitude > peakMagnitude) {
        peakMagnitude = gyro.magnitude
    }
    if (gyro.x < minX) minX = gyro.x
    if (gyro.x > maxX) maxX = gyro.x

    if (gyro.y < minY) minY = gyro.y
    if (gyro.y > maxY) maxY = gyro.y

    if (gyro.z < minZ) minZ = gyro.z
    if (gyro.z > maxZ) maxZ = gyro.z

    var armedAtMs by remember {
        mutableLongStateOf(0L)
    }

    var cooldownUntilMs by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(gyro.x, gyro.y, gyro.z) {

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
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(
                            50L,
                            VibrationEffect.DEFAULT_AMPLITUDE
                        )
                    )

                    // Don't let the vibration itself influence our gyro detector.
                    acceptLaunchAfterMs = nowMs + 120L

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

                    requestedSpinStrength =
                        kotlin.math.abs(swingValue)

                    spinRequestId++

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

        val clampedStrength =
            requestedSpinStrength.coerceIn(
                5f,
                20f
            )

        val normalized =
            (clampedStrength - 5f) / 15f

        val revolutions =
            2f + normalized * 6f

        wheelRotation.stop()

        wheelRotation.animateTo(
            targetValue =
                wheelRotation.value +
                        revolutions * 360f,
            animationSpec = tween(
                durationMillis =
                    (
                            2500 +
                                    normalized * 2500
                            ).toInt(),
                easing =
                    FastOutSlowInEasing
            )
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        RouletteWheel(
            modifier = Modifier
                .size(300.dp)
                .graphicsLayer {
                    rotationZ = wheelRotation.value
                }
        )

        // Temporary stationary ball.
        Canvas(
            modifier = Modifier.size(300.dp)
        ) {
            drawCircle(
                color = Color.White,
                radius = 7.dp.toPx(),
                center = center.copy(
                    y = 20.dp.toPx()
                )
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
            Text(
                text = when (gestureState) {
                    SpinGestureState.WAITING ->
                        "DRAW BACK"

                    SpinGestureState.ARMED ->
                        "SWING!"
                },
                fontSize = 11.sp
            )
        }

        //
        // Temporary spin button.
        //
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

                coroutineScope.launch {
                    wheelRotation.snapTo(
                        wheelRotation.value % 360f
                    )

                    wheelRotation.animateTo(
                        targetValue =
                            wheelRotation.value + 1440f,
                        animationSpec = tween(
                            durationMillis = 4000,
                            easing = FastOutSlowInEasing
                        )
                    )
                }
            }
        ) {
            Text("SPIN")
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

        val wheelNumbers = listOf(
            0,
            32, 15, 19, 4, 21, 2, 25, 17, 34,
            6, 27, 13, 36, 11, 30, 8, 23, 10,
            5, 24, 16, 33, 1, 20, 14, 31, 9,
            22, 18, 29, 7, 28, 12, 35, 3, 26
        )

        val redNumbers = setOf(
            1, 3, 5, 7, 9,
            12, 14, 16, 18,
            19, 21, 23, 25, 27,
            30, 32, 34, 36
        )

        wheelNumbers.forEachIndexed { index, number ->

            val color = when {
                number == 0 -> green
                number in redNumbers -> red
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

@RequiresApi(Build.VERSION_CODES.S)
@WearPreviewDevices
@Composable
fun DefaultPreview() {
    WearApp()
}