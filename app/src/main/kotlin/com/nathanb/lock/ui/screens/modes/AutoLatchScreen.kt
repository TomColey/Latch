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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nathanb.lock.LockApplication
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import kotlinx.coroutines.launch

private data class DayOption(val label: String, val bit: Int)

private val dayOptions = listOf(
    DayOption("M", 1 shl 0),
    DayOption("T", 1 shl 1),
    DayOption("W", 1 shl 2),
    DayOption("T", 1 shl 3),
    DayOption("F", 1 shl 4),
    DayOption("S", 1 shl 5),
    DayOption("S", 1 shl 6),
)

@Composable
fun AutoLatchScreen(
    modeId: Long,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val scope = rememberCoroutineScope()
    val modes by app.latchRepository.modes.collectAsState(initial = emptyList())
    val schedules by app.latchRepository.autoLatchSchedules.collectAsState(initial = emptyList())
    val mode = modes.firstOrNull { it.id == modeId }
    val existing = schedules.firstOrNull { it.modeId == modeId }

    var initialized by remember(modeId) { mutableStateOf(false) }
    var enabled by remember(modeId) { mutableStateOf(false) }
    var hourText by remember(modeId) { mutableStateOf("22") }
    var minuteText by remember(modeId) { mutableStateOf("30") }
    var selectedDays by remember(modeId) { mutableStateOf(0) }

    LaunchedEffect(existing, modeId) {
        if (initialized) return@LaunchedEffect
        if (existing != null) {
            enabled = existing.enabled
            hourText = (existing.startMinuteOfDay / 60).toString().padStart(2, '0')
            minuteText = (existing.startMinuteOfDay % 60).toString().padStart(2, '0')
            selectedDays = existing.daysOfWeek
        }
        initialized = true
    }

    val hour = hourText.toIntOrNull()
    val minute = minuteText.toIntOrNull()
    val validTime = hour != null && hour in 0..23 && minute != null && minute in 0..59
    val canSave = initialized && (!enabled || (validTime && selectedDays != 0))

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(64.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primary,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-latch",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 23.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = mode?.name ?: "Mode",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = {
                        if (!validTime && enabled) return@Button
                        val startMinute = if (validTime) hour!! * 60 + minute!! else 22 * 60 + 30
                        scope.launch {
                            app.latchRepository.setAutoLatchSchedule(
                                modeId = modeId,
                                enabled = enabled,
                                daysOfWeek = selectedDays,
                                startMinuteOfDay = startMinute,
                            )
                            onBack()
                        }
                    },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = "Save Auto-latch",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = colors.primaryDark,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-latch this Mode",
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = if (enabled) "On" else "Off",
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Text(
                    text = "Auto-latch only starts this Mode. It never schedules an unlatch and it will not replace another Mode that is already active. Full access still requires an authorised physical Unlatch, or the Mode's maximum latch time safety release.",
                    modifier = Modifier.padding(16.dp),
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            if (enabled) {
                Text(
                    text = "Time",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) hourText = value
                        },
                        label = { Text("Hour", fontFamily = SatoshiFamily) },
                        placeholder = { Text("22", fontFamily = SatoshiFamily) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { value ->
                            if (value.length <= 2 && value.all(Char::isDigit)) minuteText = value
                        },
                        label = { Text("Minute", fontFamily = SatoshiFamily) },
                        placeholder = { Text("30", fontFamily = SatoshiFamily) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                if (!validTime) {
                    Text(
                        text = "Enter a valid time between 00:00 and 23:59.",
                        fontFamily = SatoshiFamily,
                        fontSize = 12.sp,
                        color = colors.lockedPrimary,
                    )
                }

                Text(
                    text = "Days",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = colors.onSurface,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    dayOptions.forEach { day ->
                        FilterChip(
                            selected = (selectedDays and day.bit) != 0,
                            onClick = {
                                selectedDays = if ((selectedDays and day.bit) != 0) {
                                    selectedDays and day.bit.inv()
                                } else {
                                    selectedDays or day.bit
                                }
                            },
                            label = { Text(day.label, fontFamily = SatoshiFamily) },
                        )
                    }
                }
                if (selectedDays == 0) {
                    Text(
                        text = "Choose at least one day.",
                        fontFamily = SatoshiFamily,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
