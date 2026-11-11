package dev.blipwave.ui.scan

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blipwave.R
import dev.blipwave.ble.ScanFilter
import dev.blipwave.ble.SortOrder
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.ui.components.Badge
import dev.blipwave.ui.components.GradientButton
import dev.blipwave.ui.components.GradientCard
import dev.blipwave.ui.components.GradientText
import dev.blipwave.ui.components.PulseRadar
import dev.blipwave.ui.permissions.PermissionExplainer
import dev.blipwave.ui.permissions.rememberBlePermissionState
import dev.blipwave.ui.theme.BlipTheme
import kotlin.math.roundToInt

@Composable
fun ScanScreen(
    onOpenDevice: (ScannedDevice) -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val permission = rememberBlePermissionState()
    var expandedAddress by rememberSaveable { mutableStateOf<String?>(null) }
    var filtersOpen by rememberSaveable { mutableStateOf(false) }

    val enableBluetooth = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.startScan() }

    // Demo modunda donanım izni gerekmez
    val needsPermission = !state.isDemo && !permission.granted

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") { ScanHeader(isDemo = state.isDemo) }

        if (needsPermission) {
            item(key = "permission") {
                PermissionExplainer(
                    state = permission,
                    onTryDemo = viewModel::enableDemoMode,
                    modifier = Modifier.animateItem(),
                )
            }
            return@LazyColumn
        }

        item(key = "radar") {
            RadarHero(
                isScanning = state.isScanning,
                deviceCount = state.totalCount,
                onToggle = viewModel::toggleScan,
            )
        }

        item(key = "filters") {
            FilterBar(
                filter = state.filter,
                shownCount = state.devices.size,
                totalCount = state.totalCount,
                expanded = filtersOpen,
                onToggleExpanded = { filtersOpen = !filtersOpen },
                onNamedOnly = viewModel::setNamedOnly,
                onMinRssi = viewModel::setMinRssi,
                onSortOrder = viewModel::setSortOrder,
            )
        }

        state.error?.let { error ->
            item(key = "error") {
                ErrorCard(
                    error = error,
                    onTurnOnBluetooth = {
                        runCatching { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                    },
                    onTryDemo = viewModel::enableDemoMode,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        items(state.devices, key = { it.address }) { device ->
            DeviceCard(
                device = device,
                expanded = expandedAddress == device.address,
                onToggle = {
                    expandedAddress = if (expandedAddress == device.address) null else device.address
                },
                onConnect = {
                    // Bağlanmadan önce taramayı durdurmak bağlantı kurulumunu hızlandırır
                    viewModel.stopScan()
                    onOpenDevice(device)
                },
                modifier = Modifier.animateItem(),
            )
        }

        if (state.devices.isEmpty() && state.error == null) {
            item(key = "empty") {
                EmptyState(
                    isScanning = state.isScanning,
                    hasHiddenDevices = state.totalCount > 0,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@Composable
private fun ScanHeader(isDemo: Boolean) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientText(stringResource(R.string.app_name), MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(12.dp))
            AnimatedVisibility(visible = isDemo, enter = fadeIn(), exit = fadeOut()) {
                Badge(stringResource(R.string.demo_badge), BlipTheme.colors.signalFair)
            }
        }
        Text(
            stringResource(R.string.scan_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RadarHero(isScanning: Boolean, deviceCount: Int, onToggle: () -> Unit) {
    GradientCard(
        modifier = Modifier.fillMaxWidth(),
        brush = BlipTheme.colors.heroGradient,
        shape = MaterialTheme.shapes.extraLarge,
        elevation = 18.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PulseRadar(
                active = isScanning,
                blipCount = deviceCount,
                modifier = Modifier.size(210.dp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = isScanning,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "scanStatus",
            ) { scanning ->
                Text(
                    stringResource(if (scanning) R.string.scan_status_scanning else R.string.scan_status_idle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(if (isScanning) R.string.scan_stop else R.string.scan_start),
                icon = if (isScanning) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                brush = if (isScanning) BlipTheme.colors.warmGradient else BlipTheme.colors.brandGradient,
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FilterBar(
    filter: ScanFilter,
    shownCount: Int,
    totalCount: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onNamedOnly: (Boolean) -> Unit,
    onMinRssi: (Int) -> Unit,
    onSortOrder: (SortOrder) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.scan_count, shownCount, totalCount),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            BlipFilterChip(
                label = stringResource(R.string.filter_named_only),
                selected = filter.namedOnly,
                onClick = { onNamedOnly(!filter.namedOnly) },
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onToggleExpanded,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (expanded) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    contentColor = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = stringResource(R.string.filter_toggle))
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            GradientCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                elevation = 4.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.filter_min_signal, filter.minRssi),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Slider(
                        value = filter.minRssi.toFloat(),
                        onValueChange = { onMinRssi(it.roundToInt()) },
                        valueRange = ScanFilter.MIN_RSSI_FLOOR.toFloat()..ScanFilter.MAX_RSSI_CEILING.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.tertiary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                    Text(
                        stringResource(R.string.sort_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SortOrder.entries.forEach { order ->
                            BlipFilterChip(
                                label = stringResource(
                                    when (order) {
                                        SortOrder.SIGNAL -> R.string.sort_signal
                                        SortOrder.NAME -> R.string.sort_name
                                        SortOrder.RECENT -> R.string.sort_recent
                                    },
                                ),
                                selected = filter.sortOrder == order,
                                onClick = { onSortOrder(order) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlipFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.extraLarge,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            selectedLabelColor = MaterialTheme.colorScheme.primary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun ErrorCard(
    error: BleError,
    onTurnOnBluetooth: () -> Unit,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GradientCard(modifier = modifier.fillMaxWidth(), elevation = 4.dp) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.BluetoothDisabled,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = when (error) {
                    BleError.BluetoothOff -> stringResource(R.string.error_bluetooth_off)
                    BleError.Unsupported -> stringResource(R.string.error_unsupported)
                    BleError.PermissionDenied -> stringResource(R.string.error_permission)
                    is BleError.ScanFailed -> stringResource(R.string.error_scan_failed, error.code)
                    else -> error.message.orEmpty()
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (error == BleError.BluetoothOff) {
                TextButton(onClick = onTurnOnBluetooth) { Text(stringResource(R.string.action_turn_on)) }
            } else {
                TextButton(onClick = onTryDemo) { Text(stringResource(R.string.action_try_demo)) }
            }
        }
    }
}

@Composable
private fun EmptyState(isScanning: Boolean, hasHiddenDevices: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
                    when {
                        hasHiddenDevices -> R.string.scan_empty_filtered
                        isScanning -> R.string.scan_searching
                        else -> R.string.scan_empty_title
                    },
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            if (!isScanning && !hasHiddenDevices) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.scan_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
