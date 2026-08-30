package com.nathanb.lock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nathanb.lock.BuildConfig
import com.nathanb.lock.LockApplication
import com.nathanb.lock.ui.screens.settings.SectionHeader
import com.nathanb.lock.ui.screens.settings.SettingsCard
import com.nathanb.lock.ui.screens.settings.SettingsDivider
import com.nathanb.lock.ui.screens.settings.SettingsRow
import com.nathanb.lock.ui.screens.settings.ThemeSelector
import com.nathanb.lock.ui.theme.LockTheme
import com.nathanb.lock.ui.theme.SatoshiFamily
import com.nathanb.lock.ui.theme.ThemeMode
import com.nathanb.lock.ui.viewmodel.LockViewModel
import com.nathanb.lock.util.PermissionHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: LockViewModel,
    onNavigateToProfileDetail: (Long) -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToNfcTags: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToData: () -> Unit,
    onNavigateToSessionSettings: () -> Unit = {},
    onNavigateToSchedules: () -> Unit = {},
) {
    val colors = LockTheme.colors
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val lifecycleOwner = LocalLifecycleOwner.current

    val modes by app.latchRepository.modes.collectAsStateWithLifecycle(initialValue = emptyList())
    val latchDevices by app.latchRepository.latchDevices.collectAsStateWithLifecycle(initialValue = emptyList())
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    var accessibilityOk by remember { mutableStateOf(PermissionHelper.isAccessibilityServiceEnabled(context)) }
    var overlayOk by remember { mutableStateOf(PermissionHelper.canDrawOverlays(context)) }
    var showThemeSheet by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            accessibilityOk = PermissionHelper.isAccessibilityServiceEnabled(context)
            overlayOk = PermissionHelper.canDrawOverlays(context)
        }
    }

    val permissionsOk = accessibilityOk && overlayOk
    val themeLabel = when (themeMode) {
        ThemeMode.SYSTEM -> "System"
        ThemeMode.LIGHT -> "Light"
        ThemeMode.DARK -> "Dark"
    }

    Scaffold(containerColor = colors.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 110.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Settings",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "Set up how Latch works on this phone.",
                    fontFamily = SatoshiFamily,
                    fontSize = 14.sp,
                    color = colors.onSurfaceVariant,
                )

                Spacer(Modifier.height(20.dp))
                SectionHeader("Your Latch")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LatchActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Shield,
                        title = "Modes",
                        subtitle = if (modes.size == 1) "1 Mode" else "${modes.size} Modes",
                        count = modes.size,
                        onClick = onNavigateToProfiles,
                    )
                    LatchActionCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.Nfc,
                        title = "Latch devices",
                        subtitle = if (latchDevices.size == 1) "1 registered" else "${latchDevices.size} registered",
                        count = latchDevices.size,
                        onClick = onNavigateToNfcTags,
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionHeader("Phone")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.VerifiedUser,
                        title = "Permissions",
                        subtitle = if (permissionsOk) "Everything Latch needs is enabled" else "Action needed",
                        subtitleColor = if (permissionsOk) colors.primary else colors.error,
                        onClick = onNavigateToPermissions,
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.LightMode,
                        title = "Appearance",
                        subtitle = themeLabel,
                        onClick = { showThemeSheet = true },
                    )
                }

                Spacer(Modifier.height(18.dp))
                SectionHeader("About Latch")
                SettingsCard {
                    SettingsRow(
                        icon = Icons.Outlined.PrivacyTip,
                        title = "Privacy",
                        subtitle = "Local-first. Modes, Latch devices and active state stay on this device.",
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Outlined.Info,
                        title = "About",
                        subtitle = "Latch ${BuildConfig.VERSION_NAME}",
                    )
                }

                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceContainer, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Latch does not need an account or internet connection for its core functionality.",
                        fontFamily = SatoshiFamily,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showThemeSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThemeSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfaceContainer,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 14.dp, bottom = 10.dp)
                        .size(width = 40.dp, height = 4.dp)
                        .background(colors.onSurface.copy(alpha = 0.15f), RoundedCornerShape(2.dp)),
                )
            },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = "Appearance",
                    fontFamily = SatoshiFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colors.onSurface,
                )
                ThemeSelector(
                    currentMode = themeMode,
                    onModeSelected = { mode ->
                        viewModel.setThemeMode(mode)
                        showThemeSheet = false
                    },
                )
            }
        }
    }
}

@Composable
private fun LatchActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LockTheme.colors
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colors.cardContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(colors.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(21.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.primary.copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = count.toString(),
                        fontFamily = SatoshiFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = colors.primaryDark,
                    )
                }
            }
            Text(
                text = title,
                fontFamily = SatoshiFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = colors.onSurface,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = subtitle,
                    fontFamily = SatoshiFamily,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
