package com.nathanb.lock.ui.screens.modes

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nathanb.lock.LockApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ModeApp(val packageName: String, val label: String)

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
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as LockApplication
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<ModeApp>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }
    var selectedDuration by remember { mutableStateOf(4L * 60L * 60_000L) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
                .mapNotNull { it.activityInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != context.packageName }
                .map { info -> ModeApp(info.packageName, info.loadLabel(pm).toString()) }
                .sortedBy { it.label.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
            }
            Text(
                text = "New Mode",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Mode name") },
            placeholder = { Text("Bedtime, Focus, Family Time…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "What do you want to let through?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
        )
        Text(
            text = "Everything else will be blocked while this Mode is active.",
            style = MaterialTheme.typography.bodySmall,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(apps, key = { it.packageName }) { item ->
                val checked = item.packageName in selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (checked) selected.remove(item.packageName)
                            else selected.add(item.packageName)
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { isChecked ->
                            if (isChecked) selected.add(item.packageName)
                            else selected.remove(item.packageName)
                        },
                    )
                    Column {
                        Text(item.label)
                        Text(item.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text(
            text = "Maximum latch time",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            durationOptions.take(3).forEach { (value, label) ->
                FilterChip(
                    selected = selectedDuration == value,
                    onClick = { selectedDuration = value },
                    label = { Text(label) },
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            durationOptions.drop(3).forEach { (value, label) ->
                FilterChip(
                    selected = selectedDuration == value,
                    onClick = { selectedDuration = value },
                    label = { Text(label) },
                )
            }
        }

        Spacer(Modifier.padding(top = 8.dp))

        Button(
            onClick = {
                scope.launch {
                    app.latchRepository.createMode(
                        name = name.trim(),
                        allowedPackages = selected.toList(),
                        maxLatchDurationMs = selectedDuration,
                    )
                    onCreated()
                }
            },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save Mode")
        }
    }
}
