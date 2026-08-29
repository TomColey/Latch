package com.nathanb.lock.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.R
import com.nathanb.lock.data.model.LatchAction
import com.nathanb.lock.data.model.Mode
import com.nathanb.lock.ui.screens.home.ManualLockButton
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: LockViewModel,
    onNavigateToApps: () -> Unit = {},
    onNavigateToPermissions: () -> Unit = {},
    onNavigateToNfcTags: () -> Unit = {},
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val modes by app.latchRepository.modes.collectAsState(initial = emptyList())
    val activeModeState by app.latchRepository.activeModeState.collectAsState()
    val activeMode by app.latchRepository.activeMode.collectAsState()
    val modeLatchLinks by app.latchRepository.modeLatchLinks.collectAsState(initial = emptyList())
    val latchDevices by app.latchRepository.latchDevices.collectAsState(initial = emptyList())

    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var overlayOk by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            overlayOk = PermissionHelper.canDrawOverlays(context)
        }
    }

    val permissionsOk = accessibilityOk && overlayOk
    val isLatched = activeModeState.isLatched && activeMode != null
    var showModePicker by remember { mutableStateOf(false) }
    val latchNowFill = remember { Animatable(0f) }

    val releaseLatchNames = remember(activeMode?.id, modeLatchLinks, latchDevices) {
        val activeId = activeMode?.id ?: return@remember emptyList<String>()
        val releaseUids = modeLatchLinks
            .filter { link ->
                link.modeId == activeId &&
                    (LatchAction.fromValue(link.action) == LatchAction.UNLATCH ||
                        LatchAction.fromValue(link.action) == LatchAction.TOGGLE)
            }
            .map { it.latchUid }
            .distinct()
        releaseUids.mapNotNull { uid -> latchDevices.firstOrNull { it.uid == uid }?.name }
    }

    val safetyReleaseText = remember(activeModeState.latchedAt, activeMode?.maxLatchDurationMs) {
        val latchedAt = activeModeState.latchedAt
        val duration = activeMode?.maxLatchDurationMs
        if (latchedAt == null || duration == null) null
        else {
            val releaseAt = Instant.ofEpochMilli(latchedAt + duration)
                .atZone(ZoneId.systemDefault())
            releaseAt.format(DateTimeFormatter.ofPattern("HH:mm"))
        }
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isLatched) colors.lockedContainer else colors.surface,
        label = "latchHomeBackground",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.45f))

            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.ic_latch_logo),
                contentDescription = "Latch",
                modifier = Modifier
                    .width(190.dp)
                    .height(46.dp),
            )

            if (BuildConfig.DEBUG) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "DEV BUILD ${BuildConfig.VERSION_NAME}",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.2.sp,
                    color = colors.primary,
                )
            }

            if (!permissionsOk) {
                Spacer(Modifier.height(18.dp))
                PermissionWarningCard(
                    accessibilityOk = accessibilityOk,
                    overlayOk = overlayOk,
                    onClick = onNavigateToPermissions,
                )
            }

            Spacer(Modifier.height(if (permissionsOk) 42.dp else 28.dp))

            if (isLatched) {
                LatchedHomeContent(
                    mode = activeMode!!,
                    releaseLatchNames = releaseLatchNames,
                    safetyReleaseText = safetyReleaseText,
                )
            } else {
                UnlatchedHomeContent(
                    hasModes = modes.isNotEmpty(),
                    onLatchNow = { showModePicker = true },
                    latchNowFill = latchNowFill,
                )
            }

            Spacer(Modifier.weight(1f))
        }
    }

    if (showModePicker) {
        ModalBottomSheet(
            onDismissRequest = { showModePicker = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Latch now",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Black,
                    fontSize = 24.sp,
                    color = colors.onSurface,
                )
                Text(
                    text = "Choose a Mode to activate. You will still need its authorised physical Latch to unlatch early.",
                    fontFamily = SatoshiFamily,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(4.dp))

                modes.forEach { mode ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        onClick = {
                            scope.launch {
                                if (app.latchRepository.activeModeState.value.activeModeId == null) {
                                    app.latchRepository.latch(mode.id)
                                }
                                showModePicker = false
                            }
                        },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = colors.primaryDark,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = mode.name,
                                    fontFamily = SatoshiFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 16.sp,
                                    color = colors.onSurface,
                                )
                                Text(
                                    text = "${mode.allowedPackages.size} apps get through",
                                    fontFamily = SatoshiFamily,
                                    fontSize = 12.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionWarningCard(
    accessibilityOk: Boolean,
    overlayOk: Boolean,
    onClick: () -> Unit,
) {
    val colors = LockTheme.colors
    val missing = buildList {
        if (!accessibilityOk) add("Accessibility")
        if (!overlayOk) add("Display over other apps")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.08f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Latch needs attention",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.onSurface,
                )
                Text(
                    text = "Missing: ${missing.joinToString(", ")}. Tap to fix permissions.",
                    fontFamily = SatoshiFamily,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UnlatchedHomeContent(
    hasModes: Boolean,
    onLatchNow: () -> Unit,
    latchNowFill: Animatable<Float, *>,
) {
    val colors = LockTheme.colors

    Text(
        text = "UNLATCHED",
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        letterSpacing = 2.sp,
        color = colors.primary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "Your phone is open.",
        fontFamily = SatoshiFamily,
        fontSize = 16.sp,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(38.dp))

    if (hasModes) {
        ManualLockButton(
            onLock = onLatchNow,
            fillProgress = latchNowFill,
            showSubtitle = false,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Hold to Latch now",
            fontFamily = SatoshiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = "Or scan a physical Latch",
            fontFamily = SatoshiFamily,
            fontSize = 12.sp,
            color = colors.onSurfaceVariant.copy(alpha = 0.75f),
        )
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Text(
                text = "Create a Mode before using Latch now. Mode setup will become part of the new onboarding flow later.",
                modifier = Modifier.padding(16.dp),
                fontFamily = SatoshiFamily,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LatchedHomeContent(
    mode: Mode,
    releaseLatchNames: List<String>,
    safetyReleaseText: String?,
) {
    val colors = LockTheme.colors

    Text(
        text = "LATCHED",
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Black,
        fontSize = 38.sp,
        letterSpacing = 2.sp,
        color = colors.lockedPrimary,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = mode.name,
        fontFamily = SatoshiFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        color = colors.onSurface,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(30.dp))

    HomeStatusCard(
        icon = Icons.Outlined.Nfc,
        title = "To unlatch",
        value = when {
            releaseLatchNames.isEmpty() -> "No physical Unlatch assigned"
            releaseLatchNames.size == 1 -> "Scan ${releaseLatchNames.first()}"
            else -> "Scan ${releaseLatchNames.dropLast(1).joinToString(", ")} or ${releaseLatchNames.last()}"
        },
    )

    Spacer(Modifier.height(12.dp))

    HomeStatusCard(
        icon = Icons.Outlined.Schedule,
        title = "Safety release",
        value = safetyReleaseText?.let { "By $it" } ?: "Configured by this Mode",
    )

    Spacer(Modifier.height(22.dp))

    Text(
        text = "There is no manual unlatch. Use an authorised physical Latch or wait for the safety release.",
        fontFamily = SatoshiFamily,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        color = colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 14.dp),
    )
}

@Composable
private fun HomeStatusCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
) {
    val colors = LockTheme.colors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.primaryDark,
                    modifier = Modifier.size(21.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                )
                Text(
                    text = value,
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colors.onSurface,
                )
            }
        }
    }
}
