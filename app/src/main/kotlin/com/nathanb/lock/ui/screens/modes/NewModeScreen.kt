package com.nathanb.lock.ui.screens.modes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.ui.screens.apppicker.AppCard
import com.nathanb.lock.ui.screens.apppicker.AppSearchBar
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlinx.coroutines.launch

private val durationOptions = listOf(
    30L * 60_000L to "30 min",
    60L * 60_000L to "1 hr",
    2L * 60L * 60_000L to "2 hr",
    4L * 60L * 60_000L to "4 hr",
    8L * 60L * 60_000L to "8 hr",
    12L * 60L * 60_000L to "12 hr",
)

private data class DayOption(val label: String, val bit: Int)
private val autoLatchDays = listOf(
    DayOption("M", 1 shl 0), DayOption("T", 1 shl 1), DayOption("W", 1 shl 2),
    DayOption("T", 1 shl 3), DayOption("F", 1 shl 4), DayOption("S", 1 shl 5),
    DayOption("S", 1 shl 6),
)

private data class LatchConflict(val uid: String, val latchName: String, val mode: Mode)

@Composable
fun NewModeScreen(
    viewModel: LockViewModel,
    modeId: Long? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onEditConflictingMode: (Long) -> Unit = {},
) {
    val colors = LockTheme.colors
    val app = viewModel.getApplication<android.app.Application>() as LockApplication
    val scope = rememberCoroutineScope()
    val modes by app.latchRepository.modes.collectAsStateWithLifecycle(initialValue = emptyList())
    val modeLatchLinks by app.latchRepository.modeLatchLinks.collectAsStateWithLifecycle(initialValue = emptyList())
    val latchDevices by app.latchRepository.latchDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    val autoLatchSchedules by app.latchRepository.autoLatchSchedules.collectAsStateWithLifecycle(initialValue = emptyList())
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    val existingMode = modeId?.let { id -> modes.firstOrNull { it.id == id } }
    val existingSchedule = modeId?.let { id -> autoLatchSchedules.firstOrNull { it.modeId == id } }
    val isEditing = modeId != null

    val restoredDraft = remember(modeId) { ModeEditorDraftStore.take(modeId) }
    var step by remember(modeId) { mutableStateOf(restoredDraft?.step ?: 0) }
    var name by remember(modeId) { mutableStateOf(restoredDraft?.name.orEmpty()) }
    var selectedDuration by remember(modeId) { mutableStateOf(restoredDraft?.maxLatchDurationMs) }
    val selectedApps = remember(modeId) {
        mutableStateMapOf<String, Boolean>().apply { restoredDraft?.allowedPackages?.forEach { put(it, true) } }
    }
    var latchUid by remember(modeId) { mutableStateOf(restoredDraft?.latchUid) }
    var unlatchUid by remember(modeId) { mutableStateOf(restoredDraft?.unlatchUid) }
    var autoLatchEnabled by remember(modeId) { mutableStateOf(restoredDraft?.autoLatchEnabled ?: false) }
    var autoLatchDaysOfWeek by remember(modeId) { mutableStateOf(restoredDraft?.autoLatchDaysOfWeek ?: 0) }
    var autoLatchStartMinute by remember(modeId) { mutableStateOf(restoredDraft?.autoLatchStartMinuteOfDay ?: (22 * 60 + 30)) }
    var hourText by remember(modeId) { mutableStateOf((autoLatchStartMinute / 60).toString().padStart(2, '0')) }
    var minuteText by remember(modeId) { mutableStateOf((autoLatchStartMinute % 60).toString().padStart(2, '0')) }
    var initialized by remember(modeId) { mutableStateOf(restoredDraft != null || modeId == null) }
    var searchQuery by remember(modeId) { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingConflict by remember { mutableStateOf<LatchConflict?>(null) }

    LaunchedEffect(Unit) { viewModel.ensureInstalledAppsLoaded() }

    LaunchedEffect(existingMode, existingSchedule, modeId) {
        if (initialized) return@LaunchedEffect
        val mode = existingMode ?: return@LaunchedEffect
        name = mode.name
        selectedDuration = mode.maxLatchDurationMs
        mode.allowedPackages.forEach { selectedApps[it] = true }
        app.latchRepository.getLatchActionsForMode(mode.id).forEach { link ->
            when (LatchAction.fromValue(link.action)) {
                LatchAction.TOGGLE -> { latchUid = link.latchUid; unlatchUid = link.latchUid }
                LatchAction.LATCH -> latchUid = link.latchUid
                LatchAction.UNLATCH -> unlatchUid = link.latchUid
                null -> Unit
            }
        }
        existingSchedule?.let { schedule ->
            autoLatchEnabled = schedule.enabled
            autoLatchDaysOfWeek = schedule.daysOfWeek
            autoLatchStartMinute = schedule.startMinuteOfDay
            hourText = (schedule.startMinuteOfDay / 60).toString().padStart(2, '0')
            minuteText = (schedule.startMinuteOfDay % 60).toString().padStart(2, '0')
        }
        initialized = true
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }
    val selectedCount = selectedApps.count { it.value }
    val hour = hourText.toIntOrNull()
    val minute = minuteText.toIntOrNull()
    val validAutoLatchTime = hour != null && hour in 0..23 && minute != null && minute in 0..59

    fun activationConflictFor(uid: String): Mode? {
        val otherModeId = modeLatchLinks.asSequence()
            .filter { it.latchUid == uid && it.modeId != modeId }
            .firstOrNull { link ->
                val action = LatchAction.fromValue(link.action)
                action == LatchAction.LATCH || action == LatchAction.TOGGLE
            }?.modeId
        return otherModeId?.let { id -> modes.firstOrNull { it.id == id } }
    }

    fun currentDraft() = ModeEditorDraft(
        editorModeId = modeId,
        name = name,
        allowedPackages = selectedApps.filterValues { it }.keys.toSet(),
        maxLatchDurationMs = selectedDuration,
        latchUid = latchUid,
        unlatchUid = unlatchUid,
        autoLatchEnabled = autoLatchEnabled,
        autoLatchDaysOfWeek = autoLatchDaysOfWeek,
        autoLatchStartMinuteOfDay = autoLatchStartMinute,
        step = step,
    )

    fun leaveEditor() { ModeEditorDraftStore.clear(modeId); onBack() }

    fun saveMode() {
        val duration = selectedDuration ?: return
        val startUid = latchUid ?: return
        val releaseUid = unlatchUid ?: return
        val scheduleMinute = if (autoLatchEnabled) {
            if (!validAutoLatchTime) return
            hour!! * 60 + minute!!
        } else autoLatchStartMinute

        scope.launch {
            val savedModeId = if (existingMode == null) {
                app.latchRepository.createMode(
                    name = name.trim(),
                    allowedPackages = selectedApps.filterValues { it }.keys.toList(),
                    maxLatchDurationMs = duration,
                )
            } else {
                app.latchRepository.updateMode(
                    existingMode.copy(
                        name = name.trim(),
                        allowedPackages = selectedApps.filterValues { it }.keys.toList(),
                        maxLatchDurationMs = duration,
                    )
                )
                existingMode.id
            }

            val links = if (startUid == releaseUid) {
                listOf(startUid to LatchAction.TOGGLE)
            } else {
                listOf(startUid to LatchAction.LATCH, releaseUid to LatchAction.UNLATCH)
            }
            app.latchRepository.replaceLatchActions(savedModeId, links)
            app.latchRepository.setAutoLatchSchedule(
                modeId = savedModeId,
                enabled = autoLatchEnabled,
                daysOfWeek = autoLatchDaysOfWeek,
                startMinuteOfDay = scheduleMinute,
            )
            ModeEditorDraftStore.clear(modeId)
            onSaved()
        }
    }

    val stepTitle = when (step) {
        0 -> "Apps"
        1 -> "Physical Latches"
        2 -> "Auto-latch"
        else -> "Safety release"
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(64.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { if (step > 0) step-- else leaveEditor() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEditing) "Edit Mode" else "New Mode",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 23.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "Step ${step + 1} of 4 · $stepTitle",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (isEditing && existingMode != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Mode", tint = colors.lockedPrimary)
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().background(colors.surface).navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(0.38f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Back", fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold) }
                }
                val canContinue = when (step) {
                    0 -> name.isNotBlank() && initialized
                    1 -> latchUid != null && unlatchUid != null && latchUid?.let { activationConflictFor(it) } == null
                    2 -> !autoLatchEnabled || (validAutoLatchTime && autoLatchDaysOfWeek != 0)
                    else -> selectedDuration != null
                }
                Button(
                    onClick = {
                        if (step == 2 && autoLatchEnabled && validAutoLatchTime) {
                            autoLatchStartMinute = hour!! * 60 + minute!!
                        }
                        if (step < 3) step++ else saveMode()
                    },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = if (step < 3) "Continue" else if (isEditing) "Save changes" else "Save Mode",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        },
    ) { padding ->
        when (step) {
            0 -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(4.dp)) }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Mode name", fontFamily = SatoshiFamily) },
                        placeholder = { Text("Bedtime, Focus, Family Time…", fontFamily = SatoshiFamily) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeading("What do you want to let through?", "$selectedCount selected · everything else is blocked")
                }
                item(span = { GridItemSpan(maxLineSpan) }) { AppSearchBar(query = searchQuery, onQueryChange = { searchQuery = it }) }
                items(filteredApps, key = { it.packageName }) { item ->
                    viewModel.getAppIcon(item.packageName)
                    val selected = selectedApps[item.packageName] == true
                    AppCard(
                        app = item,
                        isSelected = selected,
                        icon = iconCache[item.packageName],
                        onToggle = { selectedApps[item.packageName] = !selected },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(16.dp)) }
            }

            1 -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                SectionHeading("Choose your physical Latches", "A Latch can release many Modes, but it can only start one.")
                LatchChoiceCard(
                    title = "Latch with",
                    subtitle = "Scan this Latch to start the Mode when your phone is open.",
                    selectedUid = latchUid,
                    latches = latchDevices.map { it.uid to it.name },
                    activationModeName = { uid -> activationConflictFor(uid)?.name },
                    onSelect = { uid, latchName ->
                        val conflict = activationConflictFor(uid)
                        if (conflict != null) pendingConflict = LatchConflict(uid, latchName, conflict) else latchUid = uid
                    },
                )
                LatchChoiceCard(
                    title = "Unlatch with",
                    subtitle = "Scan this Latch to return to full access while this Mode is active.",
                    selectedUid = unlatchUid,
                    latches = latchDevices.map { it.uid to it.name },
                    activationModeName = { null },
                    onSelect = { uid, _ -> unlatchUid = uid },
                )
                InfoCard(
                    if (latchDevices.isEmpty()) "No physical Latches have been added yet. Add one from Settings before creating a Mode."
                    else "Using the same physical Latch for both actions makes it a toggle. Using different Latches lets you build physical friction into the Mode."
                )
                Spacer(Modifier.height(16.dp))
            }

            2 -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                SectionHeading("Auto-latch", "Optionally start this Mode automatically at a chosen time.")
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
                            modifier = Modifier.size(44.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-latch this Mode", fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
                            Text(if (autoLatchEnabled) "On" else "Off", fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoLatchEnabled,
                            onCheckedChange = { autoLatchEnabled = it },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.primary),
                        )
                    }
                }
                InfoCard("Auto-latch only starts this Mode. It never unlatches your phone and it will not replace another Mode that is already active. You still need an authorised physical Latch to release it early.")

                if (autoLatchEnabled) {
                    Text("Time", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onSurface)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { value -> if (value.length <= 2 && value.all(Char::isDigit)) hourText = value },
                            label = { Text("Hour", fontFamily = SatoshiFamily) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { value -> if (value.length <= 2 && value.all(Char::isDigit)) minuteText = value },
                            label = { Text("Minute", fontFamily = SatoshiFamily) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    if (!validAutoLatchTime) {
                        Text("Enter a valid time between 00:00 and 23:59.", fontFamily = SatoshiFamily, fontSize = 12.sp, color = colors.lockedPrimary)
                    }
                    Text("Days", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onSurface)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        autoLatchDays.forEach { day ->
                            FilterChip(
                                selected = (autoLatchDaysOfWeek and day.bit) != 0,
                                onClick = {
                                    autoLatchDaysOfWeek = if ((autoLatchDaysOfWeek and day.bit) != 0) {
                                        autoLatchDaysOfWeek and day.bit.inv()
                                    } else autoLatchDaysOfWeek or day.bit
                                },
                                label = { Text(day.label, fontFamily = SatoshiFamily) },
                            )
                        }
                    }
                    if (autoLatchDaysOfWeek == 0) {
                        Text("Choose at least one day.", fontFamily = SatoshiFamily, fontSize = 12.sp, color = colors.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            else -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                SectionHeading("Maximum latch time", "Choose the latest point at which Latch must release automatically.")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).background(colors.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Timer, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(22.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Why does Latch need a maximum?", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = colors.onSurface)
                            Text(
                                "This is a safety release, not a session timer. If your physical Latch is lost, damaged or unavailable, full access will return automatically after this time. Normally you unlatch earlier by scanning your authorised Unlatch.",
                                fontFamily = SatoshiFamily,
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Safety release after", fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            durationOptions.take(3).forEach { (value, label) ->
                                FilterChip(selected = selectedDuration == value, onClick = { selectedDuration = value }, label = { Text(label, fontFamily = SatoshiFamily) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            durationOptions.drop(3).forEach { (value, label) ->
                                FilterChip(selected = selectedDuration == value, onClick = { selectedDuration = value }, label = { Text(label, fontFamily = SatoshiFamily) })
                            }
                        }
                    }
                }
                if (selectedDuration == null) {
                    Text("Choose a maximum latch time to finish this Mode.", fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = { Text("${conflict.latchName} already starts ${conflict.mode.name}", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = { Text("A physical Latch can only start one Mode, otherwise a scan would be ambiguous. You can edit ${conflict.mode.name}, or choose a different Latch here.", fontFamily = SatoshiFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModeEditorDraftStore.save(currentDraft())
                        pendingConflict = null
                        onEditConflictingMode(conflict.mode.id)
                    },
                ) {
                    Text("Edit ${conflict.mode.name}", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            },
            dismissButton = { TextButton(onClick = { pendingConflict = null }) { Text("Choose another Latch", fontFamily = SatoshiFamily) } },
        )
    }

    if (showDeleteConfirm && existingMode != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${existingMode.name}?", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove the Mode, its Latch assignments and its Auto-latch setting.", fontFamily = SatoshiFamily) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.latchRepository.deleteMode(existingMode)
                            ModeEditorDraftStore.clear(modeId)
                            showDeleteConfirm = false
                            onSaved()
                        }
                    },
                ) { Text("Delete", color = colors.lockedPrimary, fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel", fontFamily = SatoshiFamily) } },
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    val colors = LockTheme.colors
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(title, fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = colors.onSurface)
        Text(subtitle, fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun LatchChoiceCard(
    title: String,
    subtitle: String,
    selectedUid: String?,
    latches: List<Pair<String, String>>,
    activationModeName: (String) -> String?,
    onSelect: (String, String) -> Unit,
) {
    val colors = LockTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Nfc, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
                Column {
                    Text(title, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = colors.onSurface)
                    Text(subtitle, fontFamily = SatoshiFamily, fontSize = 12.sp, color = colors.onSurfaceVariant)
                }
            }
            if (latches.isEmpty()) {
                Text("No physical Latches added yet", fontFamily = SatoshiFamily, fontSize = 13.sp, color = colors.onSurfaceVariant)
            } else {
                latches.forEach { (uid, latchName) ->
                    val conflictModeName = activationModeName(uid)
                    val selected = selectedUid == uid
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(uid, latchName) }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(uid, latchName) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(latchName, fontFamily = SatoshiFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.onSurface)
                            Text(
                                when {
                                    conflictModeName != null -> "Starts $conflictModeName"
                                    selected -> "Selected"
                                    else -> "Available"
                                },
                                fontFamily = SatoshiFamily,
                                fontSize = 12.sp,
                                color = if (conflictModeName != null) colors.lockedPrimary else colors.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(iconText: String) {
    val colors = LockTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().background(colors.surfaceContainer, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(iconText, fontFamily = SatoshiFamily, fontSize = 13.sp, lineHeight = 19.sp, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}
