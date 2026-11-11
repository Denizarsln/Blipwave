package dev.blipwave.ui.connections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.blipwave.R
import dev.blipwave.data.ConnectionManager
import dev.blipwave.data.DeviceSession
import dev.blipwave.ui.components.ConnectionStatusPill
import dev.blipwave.ui.components.GradientButton
import dev.blipwave.ui.components.GradientCard
import dev.blipwave.ui.components.GradientText
import dev.blipwave.ui.components.SignalBars
import dev.blipwave.ui.theme.BlipTheme
import dev.blipwave.ui.theme.MonoStyle
import javax.inject.Inject

@HiltViewModel
class ConnectionsViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
) : ViewModel() {
    val sessions = connectionManager.sessions

    fun forget(address: String) = connectionManager.forget(address)
}

@Composable
fun ConnectionsScreen(
    onOpenDevice: (address: String, name: String?) -> Unit,
    onGoToScan: () -> Unit,
    viewModel: ConnectionsViewModel = hiltViewModel(),
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Column {
                GradientText(stringResource(R.string.nav_connections), MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.connections_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (sessions.isEmpty()) {
            item(key = "empty") { EmptyConnections(onGoToScan, Modifier.animateItem()) }
        }

        items(sessions.values.toList(), key = { it.connection.address }) { session ->
            SessionCard(
                session = session,
                onOpen = { onOpenDevice(session.connection.address, session.connection.deviceName) },
                onForget = { viewModel.forget(session.connection.address) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: DeviceSession,
    onOpen: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState by session.connection.state.collectAsStateWithLifecycle()
    val history by session.rssiHistory.collectAsStateWithLifecycle()
    val latestRssi = history.lastOrNull()?.rssi

    GradientCard(modifier = modifier.fillMaxWidth(), onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(BlipTheme.colors.coolGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Link, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.connection.deviceName ?: stringResource(R.string.device_unnamed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        session.connection.address,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (latestRssi != null) SignalBars(latestRssi)
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionStatusPill(connectionState)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onForget) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.connections_forget))
                }
                TextButton(onClick = onOpen) { Text(stringResource(R.string.connections_open)) }
            }
        }
    }
}

@Composable
private fun EmptyConnections(onGoToScan: () -> Unit, modifier: Modifier = Modifier) {
    GradientCard(modifier = modifier.fillMaxWidth(), brush = BlipTheme.colors.heroGradient) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BlipTheme.colors.brandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.connections_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.connections_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(R.string.connections_go_scan),
                icon = Icons.Rounded.Radar,
                onClick = onGoToScan,
            )
        }
    }
}
