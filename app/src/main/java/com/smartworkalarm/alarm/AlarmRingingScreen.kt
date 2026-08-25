package com.smartworkalarm.alarm

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal const val ALARM_TITLE_TAG = "alarm_title"
internal const val SNOOZE_HOLD_TAG = "snooze_hold"
internal const val DISMISS_HANDLE_TAG = "dismiss_handle"

private val NightTop = Color(0xFF102846)
private val NightMiddle = Color(0xFF07111F)
private val NightBottom = Color(0xFF050A12)
private val AlarmBlue = Color(0xFF4DA3FF)
private val AlarmMint = Color(0xFF65E0C2)
private val SecondaryText = Color(0xFFB7C8DD)
private val WarningText = Color(0xFFFFC76B)

@Composable
internal fun AlarmRingingScreen(
    presentation: AlarmPresentation,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val animationsDisabled = animationsDisabled()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(NightTop, NightMiddle, NightBottom)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AlarmIdentityZone(
                presentation = presentation,
                modifier = Modifier.weight(if (presentation.snoozeEnabled) 1f else 1.15f),
            )
            if (presentation.snoozeEnabled) {
                HoldToSnoozeZone(
                    snoozeMinutes = presentation.snoozeMinutes,
                    onSnooze = onSnooze,
                    modifier = Modifier.weight(1f),
                )
            }
            DragToDismissZone(
                animationsDisabled = animationsDisabled,
                onDismiss = onDismiss,
                modifier = Modifier.weight(if (presentation.snoozeEnabled) 1.15f else 1.85f),
            )
        }
    }
}

@Composable
private fun AlarmIdentityZone(
    presentation: AlarmPresentation,
    modifier: Modifier = Modifier,
) {
    val timeText = remember {
        LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            color = AlarmBlue.copy(alpha = 0.16f),
            shape = CircleShape,
        ) {
            Text(
                text = if (presentation.isShiftAlarm) "근무 알람" else "WORK ALARM",
                color = if (presentation.isShiftAlarm) AlarmMint else AlarmBlue,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = presentation.alarmName,
            color = Color.White,
            fontSize = alarmTitleFontSize(presentation.alarmName.length),
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = presentation.alarmName }
                .testTag(ALARM_TITLE_TAG),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = timeText,
            color = SecondaryText,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = if (presentation.calendarWarning) {
                "캘린더 일정을 확인해 주세요"
            } else {
                "좋은 하루를 시작할 시간이에요"
            },
            color = if (presentation.calendarWarning) WarningText else SecondaryText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HoldToSnoozeZone(
    snoozeMinutes: Int,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val currentOnSnooze by rememberUpdatedState(onSnooze)
    var isHolding by remember { mutableStateOf(false) }
    val holdProgress by animateFloatAsState(
        targetValue = if (isHolding) 1f else 0f,
        animationSpec = tween(durationMillis = if (isHolding) SNOOZE_HOLD_MILLIS.toInt() else 180),
        label = "snooze hold progress",
    )
    val clampedMinutes = snoozeMinutes.coerceIn(5, 30)
    val accessibilityLabel = "${clampedMinutes}분 후 다시 울림, 3초간 길게 누르기"

    LaunchedEffect(isHolding) {
        if (isHolding) {
            delay(SNOOZE_HOLD_MILLIS)
            if (isHolding) {
                isHolding = false
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSnooze()
            }
        }
    }

    Surface(
        color = Color.White.copy(alpha = 0.07f),
        shape = RoundedCornerShape(28.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .testTag(SNOOZE_HOLD_TAG)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isHolding = true
                        tryAwaitRelease()
                        isHolding = false
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.size(72.dp),
                    color = AlarmMint,
                    trackColor = Color.White.copy(alpha = 0.12f),
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "${(holdProgress * 3f).roundToInt().coerceIn(0, 3)}",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.padding(start = 20.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${clampedMinutes}분 후 다시 울림",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "3초간 길게 누르기",
                    color = SecondaryText,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DragToDismissZone(
    animationsDisabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val infiniteTransition = rememberInfiniteTransition(label = "alarm pulse")
    val animatedPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_100),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse phase",
    )

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val thresholdPx = with(density) {
            (minOf(maxWidth, maxHeight).toPx() * 0.28f).coerceAtLeast(96.dp.toPx())
        }
        val progress = dismissProgress(dragOffset.value.getDistance(), thresholdPx)
        val pulse = if (animationsDisabled) 0.32f else animatedPulse
        val ringColor = lerp(AlarmBlue, AlarmMint, progress)

        Box(
            modifier = Modifier
                .size(260.dp)
                .drawBehind {
                    repeat(3) { index ->
                        val phase = if (animationsDisabled) {
                            0.18f + index * 0.22f
                        } else {
                            (pulse + index / 3f) % 1f
                        }
                        val radius = size.minDimension * (0.27f + phase * 0.22f)
                        val alpha = if (animationsDisabled) {
                            0.13f + progress * 0.24f
                        } else {
                            (1f - phase) * (0.18f + progress * 0.32f)
                        }
                        drawCircle(
                            color = ringColor.copy(alpha = alpha.coerceIn(0f, 1f)),
                            radius = radius,
                            style = Stroke(width = 3.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = dragOffset.value.x.roundToInt(),
                            y = dragOffset.value.y.roundToInt(),
                        )
                    }
                    .size(156.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(ringColor, AlarmBlue.copy(alpha = 0.88f)),
                        ),
                        CircleShape,
                    )
                    .testTag(DISMISS_HANDLE_TAG)
                    .semantics {
                        contentDescription = "알람 해제"
                        role = Role.Button
                        onClick(label = "알람 해제") {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            currentOnDismiss()
                            true
                        }
                    }
                    .pointerInput(thresholdPx) {
                        coroutineScope {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                launch {
                                    dragOffset.stop()
                                    dragOffset.snapTo(Offset.Zero)
                                }
                                var totalDrag = Offset.Zero
                                var completed = false
                                var cancelled = false

                                while (!completed && !cancelled) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.size != 1) {
                                        cancelled = true
                                        continue
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null) {
                                        cancelled = true
                                        continue
                                    }
                                    if (!change.pressed) break

                                    totalDrag += change.positionChange()
                                    change.consume()
                                    val dragAtEvent = totalDrag
                                    launch { dragOffset.snapTo(dragAtEvent) }
                                    if (shouldDismiss(totalDrag.getDistance(), thresholdPx)) {
                                        completed = true
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        currentOnDismiss()
                                    }
                                }

                                if (!completed) {
                                    launch {
                                        dragOffset.animateTo(
                                            targetValue = Offset.Zero,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "알람 해제",
                        color = Color(0xFF06101C),
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${(progress * 100f).roundToInt()}%",
                        color = Color(0xB806101C),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun animationsDisabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}
