package com.nathanb.lock.ui.screens.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.R
import com.nathanb.lock.data.model.LatchAction
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val LatchDevOrange = Color(0xFFFF7A00)
private const val DEV_TEST_MODE_NAME = "DEV TEST"
private const val DEV_TEST_DURATION_MS = 2 * 60 * 1000L

/**
 * Temporary Latch development identity.
 *
 * The original Lock animated triangle is deliberately replaced during the early fork so a
 * development build is immediately distinguishable from the production Lock app on a real
 * device. The full home-screen treatment will be rebuilt later as part of the Latch UI phase.
 */
@Composable
internal fun AnimatedTriangleLogo(
    isLocked: Boolean,
    iconScale: Float,
    onVisualLockedChange: (Boolean) -> Unit,
    fillProgress: Float = 0f,
    accentColor: Color? = null,
) {
    LaunchedEffect(isLocked) {
        onVisualLockedChange(isLocked)
    }

    val app = LocalContext.current.applicationContext as LockApplication
    val activeMode by app.latchRepository.activeMode.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isDevTestLatched = activeMode?.name == DEV_TEST_MODE_NAME

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(width = 220.dp, height = 235.dp)
            .scale(iconScale),
    ) {
        Spacer(Modifier.height(45.dp))

        Image(
            painter = painterResource(R.drawable.ic_latch_logo),
            contentDescription = "Latch",
            modifier = Modifier
                .width(180.dp)
                .height(43.dp),
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "DEV BUILD ${BuildConfig.VERSION_NAME}",
            color = LatchDevOrange,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
        )

        if (BuildConfig.DEBUG) {
            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        if (isDevTestLatched) {
                            app.latchRepository.unlatch()
                        } else if (activeMode == null) {
                            // Reuse the existing DEV TEST Mode rather than creating a new row on
                            // every run. If a legacy paired tag exists, mirror it into the Latch
                            // model and make it a TOGGLE action for this test Mode.
                            val existingMode = app.latchRepository.modes.first()
                                .firstOrNull { it.name == DEV_TEST_MODE_NAME }
                            val modeId = existingMode?.id ?: app.latchRepository.createMode(
                                name = DEV_TEST_MODE_NAME,
                                allowedPackages = emptyList(),
                                maxLatchDurationMs = DEV_TEST_DURATION_MS,
                            )

                            app.repository.nfcTags.first().firstOrNull()?.let { tag ->
                                app.latchRepository.addLatchDevice(tag.uid, tag.name)
                                app.latchRepository.replaceLatchActions(
                                    modeId = modeId,
                                    links = listOf(tag.uid to LatchAction.TOGGLE),
                                )
                            }

                            app.latchRepository.latch(modeId)
                        }
                    }
                },
                enabled = activeMode == null || isDevTestLatched,
            ) {
                Text(
                    text = if (isDevTestLatched) "UNLATCH TEST" else "TEST LATCH · 2 MIN",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
