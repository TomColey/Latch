package com.nathanb.lock.ui.screens.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.AutoLatchSchedule
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily

@Composable
fun ModesListScreen(
    onBack: () -> Unit,
    onNewMode: () -> Unit,
    onEditMode: (Long) -> Unit,
) {
    val colors = LockTheme.colors
    val app = LocalContext.current.applicationContext as LockApplication
    val modes by app.latchRepository.modes.collectAsState(initial = emptyList())
    val schedules by app.latchRepository.autoLatchSchedules.collectAsState(initial = emptyList())
    val activeModeState by app.latchRepository.activeModeState.collectAsState()
    val safetyPausedModeIds by app.latchRepository.safetyPausedModeIds.collectAsState()

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modes", fontFamily = SatoshiFamily, fontWeight = FontWeight.Black, fontSize = 24.sp, color = colors.onSurface, letterSpacing = (-0.5).sp)
                    Text("Choose what gets through when your phone is latched", fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(colors.surfaceContainer).padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(modes.size.toString(), fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            modes.forEach { mode ->
                val isActive = activeModeState.activeModeId == mode.id
                ModeListItem(
                    mode = mode,
                    schedule = schedules.firstOrNull { it.modeId == mode.id },
                    isActive = isActive,
                    isSafetyPaused = mode.id in safetyPausedModeIds,
                    onClick = { if (!isActive) onEditMode(mode.id) },
                )
            }
            if (modes.isEmpty()) {
                Text(
                    "No Modes yet. Create one to decide what you want to let through.",
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                onClick = onNewMode,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(20.dp))
                    Text("New Mode", fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.primaryDark, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModeListItem(
    mode: Mode,
    schedule: AutoLatchSchedule?,
    isActive: Boolean,
    isSafetyPaused: Boolean,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val autoLatchOn = schedule?.enabled == true
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) colors.primary.copy(alpha = 0.08f) else colors.cardContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        isActive -> Icons.Outlined.Lock
                        isSafetyPaused -> Icons.Outlined.WarningAmber
                        else -> Icons.Outlined.Shield
                    },
                    contentDescription = null,
                    tint = if (isSafetyPaused) colors.error else colors.primaryDark,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(mode.name, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
                when {
                    isActive -> Text(
                        "ACTIVE · Unlatch before editing or deleting",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.primaryDark,
                    )
                    isSafetyPaused -> Text(
                        "AUTO-LATCH PAUSED · Safety release was reached",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = colors.error,
                    )
                    else -> Text(
                        "${mode.allowedPackages.size} apps get through · safety release ${formatDuration(mode.maxLatchDurationMs)}",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (autoLatchOn) {
                    Text(autoLatchSummary(schedule), fontFamily = SatoshiFamily, fontSize = 12.sp, color = colors.primaryDark)
                } else if (isSafetyPaused) {
                    Text("Open this Mode and turn Auto-latch back on when you're ready.", fontFamily = SatoshiFamily, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            if (autoLatchOn) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = "Auto-latch enabled",
                    tint = colors.primary,
                    modifier = Modifier.size(21.dp),
                )
            }
            if (!isActive) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun autoLatchSummary(schedule: AutoLatchSchedule?): String {
    if (schedule == null || !schedule.enabled) return ""
    val hour = schedule.startMinuteOfDay / 60
    val minute = schedule.startMinuteOfDay % 60
    return "Auto-latch ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}

private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000L
    return if (minutes < 60) "$minutes min" else {
        val hours = minutes / 60
        if (minutes % 60L == 0L) "$hours hr" else "$hours hr ${minutes % 60L} min"
    }
}
