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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Nfc
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

private data class LatchConflict(
    val uid: String,
    val latchName: String,
    val mode: Mode,
)

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
    val nfcTags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    val existingMode = modeId?.let { id -> modes.firstOrNull { it.id == id } }
    val isEditing = modeId != null

    val restoredDraft = remember(modeId) { ModeEditorDraftStore.take(modeId) }
    var step by remember(modeId) { mutableStateOf(restoredDraft?.step ?: 0) }
    var name by remember(modeId) { mutableStateOf(restoredDraft?.name.orEmpty()) }
    var selectedDuration by remember(modeId) { mutableStateOf(restoredDraft?.maxLatchDurationMs) }
    val selectedApps = remember(modeId) {
        mutableStateMapOf<String, Boolean>().apply {
            restoredDraft?.allowedPackages?.forEach { put(it, true) }
        }
    }
    var latchUid by remember(modeId) { mutableStateOf(restoredDraft?.latchUid) }
    var unlatchUid by remember(modeId) { mutableStateOf(restoredDraft?.unlatchUid) }
    var initialized by remember(modeId) { mutableStateOf(restoredDraft != null || modeId == null) }
    var searchQuery by remember(modeId) { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pendingConflict by remember { mutableStateOf<LatchConflict?>(null) }

    LaunchedEffect(Unit) {
        viewModel.ensureInstalledAppsLoaded()
    }

    LaunchedEffect(existingMode, modeId) {
        if (initialized) return@LaunchedEffect
        val mode = existingMode ?: return@LaunchedEffect
        name = mode.name
        selectedDuration = mode.maxLatchDurationMs
        mode.allowedPackages.forEach { selectedApps[it] = true }
        val links = app.latchRepository.getLatchActionsForMode(mode.id)
        links.forEach { link ->
            when (LatchAction.fromValue(link.action)) {
                LatchAction.TOGGLE -> {
                    latchUid = link.latchUid
                    unlatchUid = link.latchUid
                }
                LatchAction.LATCH -> latchUid = link.latchUid
                LatchAction.UNLATCH -> unlatchUid = link.latchUid
                null -> Unit
            }
        }
        initialized = true
    }

    val filteredApps = remember(installedApps, searchQuery) {
        if (searchQuery.isBlank()) installedApps
        else installedApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }
    val selectedCount = selectedApps.count { it.value }

    fun activationConflictFor(uid: String): Mode? {
        val otherModeId = modeLatchLinks
            .asSequence()
            .filter { it.latchUid == uid && it.modeId != modeId }
            .firstOrNull { link ->
                val action = LatchAction.fromValue(link.action)
                action == LatchAction.LATCH || action == LatchAction.TOGGLE
            }
            ?.modeId
        return otherModeId?.let { id -> modes.firstOrNull { it.id == id } }
    }

    fun currentDraft(): ModeEditorDraft = ModeEditorDraft(
        editorModeId = modeId,
        name = name,
        allowedPackages = selectedApps.filterValues { it }.keys.toSet(),
        maxLatchDurationMs = selectedDuration,
        latchUid = latchUid,
        unlatchUid = unlatchUid,
        step = step,
    )

    fun leaveEditor() {
        ModeEditorDraftStore.clear(modeId)
        onBack()
    }

    fun saveMode() {
        val duration = selectedDuration ?: return
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

            val selectedUids = listOfNotNull(latchUid, unlatchUid).distinct()
            selectedUids.forEach { uid ->
                val tag = nfcTags.firstOrNull { it.uid == uid }
                if (tag != null && app.latchRepository.getLatchDevice(uid) == null) {
                    app.latchRepository.addLatchDevice(uid, tag.name)
                }
            }

            val links = when {
                latchUid != null && latchUid == unlatchUid ->
                    listOf(latchUid!! to LatchAction.TOGGLE)
                else -> buildList {
                    latchUid?.let { add(it to LatchAction.LATCH) }
                    unlatchUid?.let { add(it to LatchAction.UNLATCH) }
                }
            }
            app.latchRepository.replaceLatchActions(savedModeId, links)
            ModeEditorDraftStore.clear(modeId)
            onSaved()
        }
    }

    val stepTitle = when (step) {
        0 -> "Apps"
        1 -> "Physical Latches"
        else -> "Safety release"
    }

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
                IconButton(
                    onClick = {
                        if (step > 0) step-- else leaveEditor()
                    },
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.primary,
                    )
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
                        text = "Step ${step + 1} of 3 · $stepTitle",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (isEditing && existingMode != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete Mode",
                            tint = colors.lockedPrimary,
                        )
                    }
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (step > 0) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier.weight(0.38f).height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Back", fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold)
                    }
                }

                val canContinue = when (step) {
                    0 -> name.isNotBlank() && initialized
                    1 -> latchUid != null && unlatchUid != null &&
                        latchUid?.let { activationConflictFor(it) } == null
                    else -> selectedDuration != null
                }
                Button(
                    onClick = {
                        if (step < 2) step++ else saveMode()
                    },
                    enabled = canContinue,
                    modifier = Modifier.weight(1f).height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = if (step < 2) "Continue" else if (isEditing) "Save changes" else "Save Mode",
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
            0 -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .imePadding()
                        .padding(horizontal = 20.dp),
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
                        SectionHeading(
                            title = "What do you want to let through?",
                            subtitle = "$selectedCount selected · everything else is blocked",
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        AppSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                    }
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
            }

            1 -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    SectionHeading(
                        title = "Choose your physical Latches",
                        subtitle = "A Latch can release many Modes, but it can only start one.",
                    )

                    LatchChoiceCard(
                        title = "Latch with",
                        subtitle = "Scan this Latch to start the Mode when your phone is open.",
                        selectedUid = latchUid,
                        tags = nfcTags.map { it.uid to it.name },
                        activationModeName = { uid -> activationConflictFor(uid)?.name },
                        onSelect = { uid, tagName ->
                            val conflict = activationConflictFor(uid)
                            if (conflict != null) {
                                pendingConflict = LatchConflict(uid, tagName, conflict)
                            } else {
                                latchUid = uid
                            }
                        },
                    )

                    LatchChoiceCard(
                        title = "Unlatch with",
                        subtitle = "Scan this Latch to return to full access while this Mode is active.",
                        selectedUid = unlatchUid,
                        tags = nfcTags.map { it.uid to it.name },
                        activationModeName = { null },
                        onSelect = { uid, _ -> unlatchUid = uid },
                    )

                    if (nfcTags.isEmpty()) {
                        InfoCard(
                            iconText = "No physical Latches are paired yet. Pair one before creating a Mode.",
                        )
                    } else {
                        InfoCard(
                            iconText = "Using the same physical Latch for both actions makes it a toggle. Using different Latches lets you build physical friction into the Mode.",
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    SectionHeading(
                        title = "Maximum latch time",
                        subtitle = "Choose the latest point at which Latch must release automatically.",
                    )

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
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(colors.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.Timer,
                                    contentDescription = null,
                                    tint = colors.primaryDark,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(
                                    text = "Why does Latch need a maximum?",
                                    fontFamily = SatoshiFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = "This is a safety release, not a session timer. If your physical Latch is lost, damaged or unavailable, full access will return automatically after this time. Normally you unlatch earlier by scanning your authorised Unlatch.",
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
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Safety release after",
                                fontFamily = SatoshiFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = colors.onSurface,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                durationOptions.take(3).forEach { (value, label) ->
                                    FilterChip(
                                        selected = selectedDuration == value,
                                        onClick = { selectedDuration = value },
                                        label = { Text(label, fontFamily = SatoshiFamily) },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                durationOptions.drop(3).forEach { (value, label) ->
                                    FilterChip(
                                        selected = selectedDuration == value,
                                        onClick = { selectedDuration = value },
                                        label = { Text(label, fontFamily = SatoshiFamily) },
                                    )
                                }
                            }
                        }
                    }

                    if (selectedDuration == null) {
                        Text(
                            text = "Choose a maximum latch time to finish this Mode.",
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    pendingConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { pendingConflict = null },
            title = {
                Text(
                    text = "${conflict.latchName} already starts ${conflict.mode.name}",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    text = "A physical Latch can only start one Mode, otherwise a scan would be ambiguous. You can edit ${conflict.mode.name}, or choose a different Latch here.",
                    fontFamily = SatoshiFamily,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ModeEditorDraftStore.save(currentDraft())
                        pendingConflict = null
                        onEditConflictingMode(conflict.mode.id)
                    },
                ) {
                    Text(
                        text = "Edit ${conflict.mode.name}",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConflict = null }) {
                    Text("Choose another Latch", fontFamily = SatoshiFamily)
                }
            },
        )
    }

    if (showDeleteConfirm && existingMode != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete ${existingMode.name}?", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = { Text("This will remove the Mode and its Latch assignments.", fontFamily = SatoshiFamily) },
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
                ) {
                    Text("Delete", color = colors.lockedPrimary, fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", fontFamily = SatoshiFamily)
                }
            },
        )
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    val colors = LockTheme.colors
    Column(modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)) {
        Text(
            text = title,
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = colors.onSurface,
        )
        Text(
            text = subtitle,
            fontFamily = SatoshiFamily,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun LatchChoiceCard(
    title: String,
    subtitle: String,
    selectedUid: String?,
    tags: List<Pair<String, String>>,
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
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    Icons.Outlined.Nfc,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column {
                    Text(
                        text = title,
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.onSurface,
                    )
                    Text(
                        text = subtitle,
                        fontFamily = SatoshiFamily,
                        fontSize = 12.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            if (tags.isEmpty()) {
                Text(
                    text = "No physical Latches paired yet",
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            } else {
                tags.forEach { (uid, tagName) ->
                    val conflictModeName = activationModeName(uid)
                    val selected = selectedUid == uid
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(uid, tagName) }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { onSelect(uid, tagName) },
                            colors = RadioButtonDefaults.colors(selectedColor = colors.primary),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tagName,
                                fontFamily = SatoshiFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp,
                                color = colors.onSurface,
                            )
                            Text(
                                text = when {
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
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = iconText,
            fontFamily = SatoshiFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
