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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun NewModeScreen(
    viewModel: LockViewModel,
    modeId: Long? = null,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val colors = LockTheme.colors
    val app = viewModel.getApplication<android.app.Application>() as LockApplication
    val scope = rememberCoroutineScope()
    val modes by app.latchRepository.modes.collectAsStateWithLifecycle(initialValue = emptyList())
    val nfcTags by viewModel.nfcTags.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val iconCache by viewModel.appIconCache.collectAsStateWithLifecycle()
    val existingMode = modeId?.let { id -> modes.firstOrNull { it.id == id } }
    val isEditing = modeId != null

    var name by remember(modeId) { mutableStateOf("") }
    var selectedDuration by remember(modeId) { mutableStateOf(4L * 60L * 60_000L) }
    val selectedApps = remember(modeId) { mutableStateMapOf<String, Boolean>() }
    var latchUid by remember(modeId) { mutableStateOf<String?>(null) }
    var unlatchUid by remember(modeId) { mutableStateOf<String?>(null) }
    var initialized by remember(modeId) { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.ensureInstalledAppsLoaded()
    }

    LaunchedEffect(existingMode, modeId) {
        if (initialized) return@LaunchedEffect
        if (modeId == null) {
            initialized = true
            return@LaunchedEffect
        }
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
        else installedApps.filter {
            it.label.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val selectedCount = selectedApps.count { it.value }

    fun saveMode() {
        scope.launch {
            val savedModeId = if (existingMode == null) {
                app.latchRepository.createMode(
                    name = name.trim(),
                    allowedPackages = selectedApps.filterValues { it }.keys.toList(),
                    maxLatchDurationMs = selectedDuration,
                )
            } else {
                app.latchRepository.updateMode(
                    existingMode.copy(
                        name = name.trim(),
                        allowedPackages = selectedApps.filterValues { it }.keys.toList(),
                        maxLatchDurationMs = selectedDuration,
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
            onSaved()
        }
    }

    Scaffold(
        containerColor = colors.surface,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(56.dp)
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
                        text = if (isEditing) "Edit Mode" else "New Mode",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = if (isEditing) "Change what gets through and how this Mode latches" else "Decide what gets through when this Mode is active",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surface)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = ::saveMode,
                    enabled = name.isNotBlank() && initialized,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
                ) {
                    Text(
                        text = if (isEditing) "Save changes" else "Save Mode",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }
            }
        },
    ) { padding ->
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(4.dp))
            }

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
                AppSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
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

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(
                    title = "Maximum latch time",
                    subtitle = "Safety release if you cannot reach an Unlatch",
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(
                    title = "Physical Latches",
                    subtitle = "Choose where this Mode starts and where full access returns",
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LatchChoiceCard(
                    title = "Latch with",
                    selectedUid = latchUid,
                    tags = nfcTags.map { it.uid to it.name },
                    onSelect = { latchUid = it },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                LatchChoiceCard(
                    title = "Unlatch with",
                    selectedUid = unlatchUid,
                    tags = nfcTags.map { it.uid to it.name },
                    onSelect = { unlatchUid = it },
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(16.dp))
            }
        }
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
            fontSize = 16.sp,
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
    selectedUid: String?,
    tags: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    val colors = LockTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colors.onSurface,
            )
            if (tags.isEmpty()) {
                Text(
                    text = "No physical Latches paired yet",
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedUid == null,
                        onClick = { onSelect(null) },
                        label = { Text("None", fontFamily = SatoshiFamily) },
                    )
                    tags.forEach { (uid, tagName) ->
                        FilterChip(
                            selected = selectedUid == uid,
                            onClick = { onSelect(uid) },
                            label = { Text(tagName, fontFamily = SatoshiFamily) },
                        )
                    }
                }
            }
        }
    }
}
