package com.smartworkalarm.alarm

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValueAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal const val ALARM_TITLE_TAG = "alarm_title"
internal const val SNOOZE_HOLD_TAG = "snooze_hold"
internal const val DISMISS_HANDLE_TAG = "dismiss_handle"
internal const val ALARM_ACTION_ERROR_TAG = "alarm_action_error"

@Composable
internal fun AlarmRingingScreen(
    presentation: AlarmPresentation,
    actionsEnabled: Boolean = true,
    persistentError: String? = null,
    animationsDisabledOverride: Boolean? = null,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    val animationsDisabled = animationsDisabledOverride ?: animationsDisabled()
    val density = LocalDensity.current
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.surface, colors.background)))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        val dismissThresholdPx = with(density) {
            dismissThresholdPx(
                safeWidthPx = maxWidth.toPx(),
                safeHeightPx = maxHeight.toPx(),
                density = density.density,
            )
        }
        key(
            presentation.sessionKey,
            presentation.alarmId,
            presentation.scheduledOccurrenceAt,
        ) {
            val compact = maxHeight < 620.dp || density.fontScale > 1.3f
            val actions: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
                persistentError?.let { message ->
                    Text(
                        text = message,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag(ALARM_ACTION_ERROR_TAG),
                    )
                }
                if (presentation.snoozeEnabled) {
                    HoldToSnoozeZone(
                        snoozeMinutes = presentation.snoozeMinutes,
                        actionsEnabled = actionsEnabled,
                        onSnooze = onSnooze,
                        modifier = Modifier.height(
                            when {
                                density.fontScale > 1.3f -> 120.dp
                                compact -> 92.dp
                                else -> 108.dp
                            },
                        ),
                    )
                }
                DragToDismissZone(
                    animationsDisabled = animationsDisabled,
                    actionsEnabled = actionsEnabled,
                    dismissFailed = persistentError != null,
                    thresholdPx = dismissThresholdPx,
                    onDismiss = onDismiss,
                    modifier = Modifier.weight(1f),
                )
                }
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    AlarmIdentityZone(presentation, compact = true, modifier = Modifier.weight(1f))
                    actions(Modifier.weight(1f).fillMaxSize().padding(start = 16.dp))
                }
            } else {
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AlarmIdentityZone(presentation, compact, Modifier.weight(if (density.fontScale > 1.3f) 0.75f else 1f))
                    actions(Modifier.weight(if (presentation.snoozeEnabled) 1.65f else 1.25f))
                }
            }
        }
    }
}

@Composable
private fun AlarmIdentityZone(
    presentation: AlarmPresentation,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
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
            color = colors.primary.copy(alpha = 0.10f),
            shape = CircleShape,
        ) {
            Text(
                text = if (presentation.isShiftAlarm) "근무 알람" else "CALARM",
                color = colors.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp),
            )
        }
        Spacer(Modifier.height(if (compact) 4.dp else 20.dp))
        Text(
            text = timeText,
            color = colors.onSurface,
            fontSize = if (compact) 48.sp else 82.sp,
            lineHeight = if (compact) 52.sp else 88.sp,
            letterSpacing = (-3).sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 16.dp))
        Text(
            text = presentation.alarmName,
            color = colors.onSurface,
            fontSize = if (compact) 22.sp else 30.sp,
            lineHeight = if (compact) 28.sp else 36.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = presentation.alarmName }
                .testTag(ALARM_TITLE_TAG),
        )
        if (!compact || presentation.calendarWarning) Text(
            text = if (presentation.calendarWarning) {
                "캘린더 일정을 확인해 주세요"
            } else {
                "좋은 하루를 시작할 시간이에요"
            },
            color = if (presentation.calendarWarning) colors.error else colors.onSurfaceVariant,
            fontSize = 13.sp,
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
    actionsEnabled: Boolean,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val currentOnSnooze by rememberUpdatedState(onSnooze)
    var isHolding by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableFloatStateOf(0f) }
    val clampedMinutes = snoozeMinutes.coerceIn(5, 30)
    val accessibilityLabel = "${clampedMinutes}분 후 다시 울림, 3초간 길게 누르기"

    LaunchedEffect(isHolding, actionsEnabled) {
        if (!isHolding || !actionsEnabled) {
            holdProgress = 0f
            return@LaunchedEffect
        }
        val startedAtNanos = withFrameNanos { it }
        while (isHolding && actionsEnabled) {
            val elapsedMillis = (withFrameNanos { it } - startedAtNanos)
                .coerceAtLeast(0L) / 1_000_000L
            holdProgress = snoozeHoldProgress(elapsedMillis)
            if (elapsedMillis >= SNOOZE_HOLD_MILLIS) {
                isHolding = false
                holdProgress = 1f
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                currentOnSnooze()
                break
            }
        }
    }

    Surface(
        color = colors.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, colors.outlineVariant),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .alpha(if (actionsEnabled) 1f else 0.55f)
            .testTag(SNOOZE_HOLD_TAG)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
                if (!actionsEnabled) disabled()
            }
            .then(
                if (actionsEnabled) {
                    Modifier.pointerInput(actionsEnabled) {
                        detectTapGestures(
                            onPress = {
                                isHolding = true
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    isHolding = false
                                    holdProgress = 0f
                                }
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    modifier = Modifier.size(46.dp),
                    color = colors.primary,
                    trackColor = colors.onSurface.copy(alpha = 0.12f),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text(
                    text = "${(holdProgress * 3f).roundToInt().coerceIn(0, 3)}",
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${clampedMinutes}분 후 다시 울림",
                    color = colors.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "3초간 길게 누르기",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun DragToDismissZone(
    animationsDisabled: Boolean,
    actionsEnabled: Boolean,
    dismissFailed: Boolean,
    thresholdPx: Float,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    var dragTarget by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var dismissCommitted by remember { mutableStateOf(false) }
    var awaitingDismissResult by remember { mutableStateOf(false) }
    val dragOffset by animateValueAsState(
        targetValue = dragTarget,
        typeConverter = Offset.VectorConverter,
        animationSpec = when {
            isDragging || (dismissCommitted && animationsDisabled) -> snap()
            dismissCommitted -> tween(durationMillis = 220)
            else -> spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "dismiss drag offset",
    )

    LaunchedEffect(actionsEnabled, dismissFailed) {
        when {
            actionsEnabled && dismissFailed && dismissCommitted -> {
                awaitingDismissResult = false
                dismissCommitted = false
                dragTarget = Offset.Zero
            }
            !actionsEnabled && dismissCommitted -> awaitingDismissResult = true
            actionsEnabled && awaitingDismissResult -> {
                awaitingDismissResult = false
                dismissCommitted = false
                dragTarget = Offset.Zero
            }
        }
    }
    val animatedPulse = if (animationsDisabled) {
        0.32f
    } else {
        val infiniteTransition = rememberInfiniteTransition(label = "alarm pulse")
        val pulse by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2_100),
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse phase",
        )
        pulse
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val progress = dismissProgress(dragOffset.getDistance(), thresholdPx)
        val pulse = animatedPulse
        val ringColor = lerp(colors.primary, colors.onSurface, progress * 0.55f)
        val orbitSize = minOf(310.dp, maxWidth, (maxHeight - 36.dp).coerceAtLeast(88.dp))
        val handleSize = minOf(172.dp, orbitSize * 0.66f)

        Box(
            modifier = Modifier
                .padding(bottom = 24.dp)
                .size(orbitSize)
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
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = dragOffset.x.roundToInt(),
                            y = dragOffset.y.roundToInt(),
                        )
                    }
                    .size(handleSize)
                    .alpha(if (actionsEnabled) 1f else 0.55f)
                    .background(
                        ringColor,
                        CircleShape,
                    )
                    .testTag(DISMISS_HANDLE_TAG)
                    .semantics {
                        contentDescription = "알람 해제"
                        role = Role.Button
                        if (actionsEnabled) {
                            onClick(label = "알람 해제") {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentOnDismiss()
                                true
                            }
                        } else {
                            disabled()
                        }
                    }
                    .then(
                        if (actionsEnabled) {
                            Modifier.pointerInput(thresholdPx, actionsEnabled) {
                                try {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        isDragging = true
                                        dismissCommitted = false
                                        dragTarget = Offset.Zero
                                        var completed = false

                                        while (!completed) {
                                            val event = awaitPointerEvent()
                                            if (event.changes.size != 1) break
                                            val change = event.changes.firstOrNull { it.id == down.id }
                                                ?: break
                                            val sample = resolveDragSample(
                                                downPosition = down.position,
                                                currentPosition = change.position,
                                                thresholdPx = thresholdPx,
                                                cancelled = false,
                                            )
                                            dragTarget = sample.offset
                                            change.consume()
                                            if (sample.dismiss) {
                                                completed = true
                                                isDragging = false
                                                dismissCommitted = true
                                                dragTarget = dismissReleaseTarget(
                                                    sample = sample,
                                                    exitDistancePx = thresholdPx * 3f,
                                                )
                                                haptic.performHapticFeedback(
                                                    HapticFeedbackType.LongPress,
                                                )
                                                currentOnDismiss()
                                            } else if (!change.pressed) {
                                                break
                                            }
                                        }

                                        isDragging = false
                                        if (!dismissCommitted) dragTarget = Offset.Zero
                                    }
                                } finally {
                                    isDragging = false
                                    if (!dismissCommitted) dragTarget = Offset.Zero
                                }
                            }
                        } else {
                            Modifier
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (handleSize < 130.dp) "해제" else "알람 해제",
                        color = colors.onPrimary,
                        fontSize = if (handleSize < 130.dp) 18.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    if (handleSize >= 100.dp) Text(
                        text = if (progress > 0f) "${(progress * 100f).roundToInt()}%" else "드래그",
                        color = colors.onPrimary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Text(
            text = if (actionsEnabled) "원을 원하는 방향으로 밀어 해제" else "알람을 처리하고 있어요",
            color = colors.onSurfaceVariant,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
        )
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
