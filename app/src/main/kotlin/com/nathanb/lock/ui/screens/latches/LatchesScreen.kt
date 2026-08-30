package com.nathanb.lock.ui.screens.latches

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.LatchDevice
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import kotlinx.coroutines.launch

@Composable
fun LatchesScreen(
    viewModel: LockViewModel,
    onBack: () -> Unit,
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val scope = rememberCoroutineScope()
    val latches by app.latchRepository.latchDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    val links by app.latchRepository.modeLatchLinks.collectAsStateWithLifecycle(initialValue = emptyList())
    val modes by app.latchRepository.modes.collectAsStateWithLifecycle(initialValue = emptyList())
    val pendingUid by viewModel.pendingPairingUid.collectAsStateWithLifecycle()

    var scanning by remember { mutableStateOf(false) }
    var namingUid by remember { mutableStateOf<String?>(null) }
    var nameDraft by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<LatchDevice?>(null) }
    var deleteTarget by remember { mutableStateOf<LatchDevice?>(null) }
    var duplicateTarget by remember { mutableStateOf<LatchDevice?>(null) }

    LaunchedEffect(pendingUid, scanning) {
        val uid = pendingUid ?: return@LaunchedEffect
        if (!scanning) return@LaunchedEffect

        scanning = false
        viewModel.cancelPairing()
        val existing = app.latchRepository.getLatchDevice(uid)
        if (existing != null) {
            duplicateTarget = existing
        } else {
            namingUid = uid
            nameDraft = ""
        }
    }

    fun stopScanning() {
        scanning = false
        viewModel.nfcManager.disablePairingMode()
        viewModel.cancelPairing()
        viewModel.clearPairingWriteResult()
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
                        stopScanning()
                        onBack()
                    },
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = colors.primary)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Latch devices",
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Black,
                        fontSize = 24.sp,
                        color = colors.onSurface,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = "The NFC devices that start and release your Modes",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .background(colors.surfaceContainer, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = latches.size.toString(),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            if (scanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.primary.copy(alpha = 0.10f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).background(colors.primary.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.Nfc, contentDescription = null, tint = colors.primary, modifier = Modifier.size(28.dp))
                        }
                        Text(
                            text = "Waiting for a Latch device…",
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = colors.onSurface,
                        )
                        Text(
                            text = "Hold the NFC tag against the back of your phone until it is detected.",
                            fontFamily = SatoshiFamily,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                        )
                        TextButton(onClick = ::stopScanning) {
                            Text("Cancel", fontFamily = SatoshiFamily)
                        }
                    }
                }
            }

            latches.forEach { latch ->
                val latchLinks = links.filter { it.latchUid == latch.uid }
                val activationMode = latchLinks.firstOrNull {
                    val action = LatchAction.fromValue(it.action)
                    action == LatchAction.LATCH || action == LatchAction.TOGGLE
                }?.modeId?.let { id -> modes.firstOrNull { it.id == id } }
                val releaseCount = latchLinks.count {
                    val action = LatchAction.fromValue(it.action)
                    action == LatchAction.UNLATCH || action == LatchAction.TOGGLE
                }

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
                            Icon(Icons.Outlined.Nfc, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(latch.name, fontFamily = SatoshiFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.onSurface)
                            Text(
                                text = buildString {
                                    if (activationMode != null) append("Starts ${activationMode.name}") else append("Does not start a Mode")
                                    if (releaseCount > 0) append(" · releases $releaseCount Mode${if (releaseCount == 1) "" else "s"}")
                                },
                                fontFamily = SatoshiFamily,
                                fontSize = 13.sp,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            renameTarget = latch
                            nameDraft = latch.name
                        }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Rename", tint = colors.onSurfaceVariant)
                        }
                        IconButton(onClick = { deleteTarget = latch }) {
                            Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete", tint = colors.lockedPrimary)
                        }
                    }
                }
            }

            if (latches.isEmpty() && !scanning) {
                Text(
                    text = "No Latch devices yet. Add an NFC tag and give it a name based on where you keep it, such as Bedroom, Kitchen or Desk.",
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            if (!scanning) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    onClick = {
                        viewModel.clearPairingWriteResult()
                        viewModel.enableNfcPairing()
                        scanning = true
                    },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, tint = colors.primaryDark, modifier = Modifier.size(20.dp))
                        Text(
                            text = "Add Latch device",
                            fontFamily = SatoshiFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.primaryDark,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    namingUid?.let { uid ->
        AlertDialog(
            onDismissRequest = {
                namingUid = null
                nameDraft = ""
            },
            title = { Text("Name this Latch device", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Use the place or purpose that will make sense when you assign it to a Mode.",
                        fontFamily = SatoshiFamily,
                    )
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("Name", fontFamily = SatoshiFamily) },
                        placeholder = { Text("Bedroom, Kitchen, Desk…", fontFamily = SatoshiFamily) },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank(),
                    onClick = {
                        scope.launch {
                            app.latchRepository.addLatchDevice(uid, nameDraft.trim())
                            namingUid = null
                            nameDraft = ""
                            viewModel.clearPairingWriteResult()
                        }
                    },
                ) {
                    Text("Add device", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    namingUid = null
                    nameDraft = ""
                }) { Text("Cancel", fontFamily = SatoshiFamily) }
            },
        )
    }

    renameTarget?.let { latch ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Latch device", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Name", fontFamily = SatoshiFamily) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = nameDraft.isNotBlank(),
                    onClick = {
                        scope.launch {
                            app.latchRepository.renameLatchDevice(latch.uid, nameDraft.trim())
                            renameTarget = null
                            nameDraft = ""
                        }
                    },
                ) { Text("Save", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, color = colors.primary) }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel", fontFamily = SatoshiFamily) } },
        )
    }

    duplicateTarget?.let { latch ->
        AlertDialog(
            onDismissRequest = { duplicateTarget = null },
            title = { Text("${latch.name} is already added", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = { Text("This NFC tag is already a Latch device. You can rename it from the Latch devices list.", fontFamily = SatoshiFamily) },
            confirmButton = {
                TextButton(onClick = { duplicateTarget = null }) {
                    Text("Got it", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, color = colors.primary)
                }
            },
        )
    }

    deleteTarget?.let { latch ->
        val affectedLinks = links.filter { it.latchUid == latch.uid }
        val affectedModeNames = affectedLinks.mapNotNull { link -> modes.firstOrNull { it.id == link.modeId }?.name }.distinct()
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${latch.name}?", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = if (affectedModeNames.isEmpty()) {
                        "This removes the Latch device from Latch."
                    } else {
                        "This Latch device is used by ${affectedModeNames.joinToString()}. Deleting it will also remove those Latch/Unlatch assignments from the affected Modes."
                    },
                    fontFamily = SatoshiFamily,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            app.latchRepository.removeLatchDevice(latch.uid)
                            deleteTarget = null
                        }
                    },
                ) { Text("Delete", fontFamily = SatoshiFamily, fontWeight = FontWeight.Bold, color = colors.lockedPrimary) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel", fontFamily = SatoshiFamily) } },
        )
    }
}
