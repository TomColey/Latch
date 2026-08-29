package com.nathanb.lock.ui.screens.home

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.LockApplication
import com.nathanb.lock.R
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LONG_PRESS_DURATION_MS = 1000L

@Composable
internal fun ManualLockButton(
    onLock: () -> Unit,
    fillProgress: Animatable<Float, *>,
    modifier: Modifier = Modifier,
    showSubtitle: Boolean = true,
    accentColor: Color? = null,
) {
    // onLock is retained temporarily so the inherited Home call sites do not need to change.
    // Latch-native manual activation now always goes through the Mode picker below.
    @Suppress("UNUSED_VARIABLE") val inheritedOnLock = onLock

    val colors = LockTheme.colors
    val tintColor = accentColor ?: colors.onSurface
    val view = LocalView.current
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val scope = rememberCoroutineScope()
    val modes by app.latchRepository.modes.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeModeState by app.latchRepository.activeModeState.collectAsStateWithLifecycle()

    var isPressed by remember { mutableStateOf(false) }
    var shakeTrigger by remember { mutableIntStateOf(0) }
    var showModePicker by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        label = "lockBtnScale",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (isPressed) 0.16f else 0.08f,
        label = "lockBtnBg",
    )

    val shakeOffset = remember { Animatable(0f) }
    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            for (v in listOf(-10f, 10f, -7f, 7f, -4f, 4f, 0f)) {
                shakeOffset.animateTo(v, tween(50))
            }
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            fillProgress.snapTo(0f)
            var completed = false
            try {
                coroutineScope {
                    launch {
                        fillProgress.animateTo(1f, tween(LONG_PRESS_DURATION_MS.toInt(), easing = LinearEasing))
                    }
                    launch {
                        delay(80)
                        val start = System.currentTimeMillis()
                        while (System.currentTimeMillis() - start < LONG_PRESS_DURATION_MS) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            val elapsed = System.currentTimeMillis() - start
                            val p = (elapsed.toFloat() / LONG_PRESS_DURATION_MS).coerceIn(0f, 1f)
                            delay((120 - 90 * p).toLong().coerceAtLeast(30))
                        }
                    }
                }
                completed = true
                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (activeModeState.activeModeId == null) showModePicker = true
            } finally {
                if (!completed) {
                    view.performHapticFeedback(HapticFeedbackConstants.REJECT)
                    shakeTrigger++
                }
            }
        } else if (fillProgress.value > 0.01f) {
            fillProgress.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .offset(x = shakeOffset.value.dp)
                    .scale(scale)
                    .background(color = tintColor.copy(alpha = bgAlpha), shape = CircleShape)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            isPressed = true
                            waitForUpOrCancellation()
                            isPressed = false
                        }
                    },
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Latch now",
                    modifier = Modifier.size(24.dp),
                    tint = tintColor.copy(alpha = 0.7f),
                )
            }

            if (showSubtitle) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_or_scan_nfc),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }

    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = {
                Text(
                    text = "Latch now",
                    fontFamily = SatoshiFamily,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
            },
            text = {
                Column {
                    if (modes.isEmpty()) {
                        Text(
                            text = "Create a Mode first, then choose it here whenever you want to latch without scanning a physical Latch.",
                            fontFamily = SatoshiFamily,
                        )
                    } else {
                        Text(
                            text = "Choose a Mode. Once latched, you will still need its authorised physical Unlatch or safety release to regain full access.",
                            fontFamily = SatoshiFamily,
                        )
                        Spacer(Modifier.height(8.dp))
                        modes.forEach { mode ->
                            TextButton(
                                onClick = {
                                    showModePicker = false
                                    scope.launch {
                                        if (app.latchRepository.activeModeState.value.activeModeId == null) {
                                            app.latchRepository.latch(mode.id)
                                        }
                                    }
                                },
                            ) {
                                Text(mode.name, fontFamily = SatoshiFamily, color = colors.primary)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModePicker = false }) {
                    Text("Cancel", fontFamily = SatoshiFamily)
                }
            },
        )
    }
}
