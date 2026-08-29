package com.nathanb.lock.ui.screens.modes

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nathanb.lock.LockApplication
import com.nathanb.lock.data.model.Mode

@Composable
fun ModesListScreen(
    onBack: () -> Unit,
    onNewMode: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as LockApplication
    val modes by app.latchRepository.modes.collectAsState(initial = emptyList())

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
                text = "Modes",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Button(onClick = onNewMode) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text(" New Mode")
            }
        }

        Text(
            text = "A Mode decides what gets through while your phone is latched.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(vertical = 16.dp),
        )

        if (modes.isEmpty()) {
            Text("No Modes yet. Create one to get started.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(modes, key = { it.id }) { mode ->
                    ModeCard(mode)
                }
            }
        }
    }
}

@Composable
private fun ModeCard(mode: Mode) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(mode.name, fontWeight = FontWeight.Bold)
            Text(
                text = "${mode.allowedPackages.size} apps get through",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "Maximum latch time: ${formatDuration(mode.maxLatchDurationMs)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val minutes = durationMs / 60_000L
    return if (minutes < 60) "$minutes min" else {
        val hours = minutes / 60
        if (minutes % 60L == 0L) "$hours hr" else "$hours hr ${minutes % 60L} min"
    }
}
