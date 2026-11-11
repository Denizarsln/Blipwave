package dev.blipwave.ui.device

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blipwave.R
import dev.blipwave.ble.GattUuids
import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.SignalLevel
import dev.blipwave.ble.model.CharacteristicProperty
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattCharacteristicInfo
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.ble.model.isActive
import dev.blipwave.ui.components.Badge
import dev.blipwave.ui.components.ConnectionStatusPill
import dev.blipwave.ui.components.GradientButton
import dev.blipwave.ui.components.GradientCard
import dev.blipwave.ui.components.SignalBars
import dev.blipwave.ui.components.signalColor
import dev.blipwave.ui.theme.BlipTheme
import dev.blipwave.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    onBack: () -> Unit,
    onOpenConsole: (address: String) -> Unit,
    viewModel: DeviceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        state.name ?: stringResource(R.string.device_unnamed),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionHero(
                state = state,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
                onOpenConsole = { onOpenConsole(state.address) },
            )
            SignalCard(state)
            ServicesSection(state)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectionHero(
    state: DeviceDetailUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenConsole: () -> Unit,
) {
    val connection = state.connection
    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        brush = BlipTheme.colors.heroGradient,
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ConnectionOrb(connection, Modifier.size(132.dp))
            Spacer(Modifier.height(16.dp))
            ConnectionStatusPill(connection)
            Spacer(Modifier.height(10.dp))
            Text(state.address, style = MonoStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)

            val reason = (connection as? ConnectionState.Disconnected)?.reason
            AnimatedVisibility(visible = reason != null) {
                Text(
                    stringResource(R.string.detail_reason, reason.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (connection.isActive) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(Icons.Rounded.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.detail_disconnect), maxLines = 1)
                    }
                } else {
                    GradientButton(
                        text = stringResource(R.string.detail_reconnect),
                        icon = Icons.Rounded.Link,
                        brush = BlipTheme.colors.warmGradient,
                        onClick = onConnect,
                        modifier = Modifier.weight(1f),
                    )
                }
                GradientButton(
                    text = stringResource(R.string.detail_open_console),
                    icon = Icons.Rounded.Forum,
                    onClick = onOpenConsole,
                    enabled = state.canOpenConsole,
                    modifier = Modifier.weight(1f),
                )
            }

            AnimatedVisibility(visible = connection == ConnectionState.Connected && !state.hasUart) {
                Text(
                    stringResource(R.string.detail_no_uart),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun SignalCard(state: DeviceDetailUiState) {
    val latest = state.rssiHistory.lastOrNull()?.rssi
    GradientCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.detail_signal_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.detail_signal_caption),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (latest != null) {
                    Text(
                        stringResource(R.string.rssi_value, latest),
                        style = MaterialTheme.typography.titleLarge,
                        color = signalColor(SignalLevel.fromRssi(latest)),
                    )
                    Spacer(Modifier.width(10.dp))
                    SignalBars(latest)
                }
            }
            Spacer(Modifier.height(14.dp))
            RssiChart(
                samples = state.rssiHistory,
                live = state.connection == ConnectionState.Connected,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp),
            )
        }
    }
}

@Composable
private fun ServicesSection(state: DeviceDetailUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.detail_services_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (state.services.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Badge(state.services.size.toString(), MaterialTheme.colorScheme.primary)
            }
        }

        when {
            state.services.isNotEmpty() -> state.services.forEach { ServiceCard(it) }
            state.connection == ConnectionState.Connecting -> {
                Text(
                    stringResource(R.string.detail_discovering),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
            else -> Text(
                stringResource(R.string.detail_services_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ServiceCard(service: GattServiceInfo) {
    // UART servisi en ilgi çekici olan; varsayılan olarak açık gelsin
    var expanded by rememberSaveable(service.uuid.toString()) {
        mutableStateOf(service.uuid == NordicUart.SERVICE)
    }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "serviceChevron")

    GradientCard(modifier = Modifier.fillMaxWidth(), elevation = 4.dp, onClick = { expanded = !expanded }) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        GattUuids.serviceName(service.uuid) ?: stringResource(R.string.service_unknown),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        GattUuids.shortLabel(service.uuid),
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    stringResource(R.string.detail_characteristics, service.characteristics.size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    service.characteristics.forEach { characteristic ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        )
                        CharacteristicRow(characteristic)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacteristicRow(characteristic: GattCharacteristicInfo) {
    Column {
        Text(
            GattUuids.characteristicName(characteristic.uuid) ?: stringResource(R.string.characteristic_unknown),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            GattUuids.shortLabel(characteristic.uuid),
            style = MonoStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            characteristic.properties.forEach { property ->
                Badge(
                    text = property.name.replace('_', ' '),
                    color = when (property) {
                        CharacteristicProperty.READ -> BlipTheme.colors.signalExcellent
                        CharacteristicProperty.WRITE, CharacteristicProperty.WRITE_NO_RESPONSE -> BlipTheme.colors.signalFair
                        CharacteristicProperty.NOTIFY, CharacteristicProperty.INDICATE -> BlipTheme.colors.signalGood
                    },
                )
            }
        }
    }
}
