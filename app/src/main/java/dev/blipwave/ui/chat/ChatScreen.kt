package dev.blipwave.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.blipwave.R
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.data.ByteStats
import dev.blipwave.data.ChatMessage
import dev.blipwave.data.MessageDirection
import dev.blipwave.data.PayloadCodec
import dev.blipwave.data.PayloadFormat
import dev.blipwave.ui.components.ConnectionStatusPill
import dev.blipwave.ui.components.SegmentedToggle
import dev.blipwave.ui.theme.BlipTheme
import dev.blipwave.ui.theme.MonoStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val QUICK_COMMANDS = listOf("PING", "Hello")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                context.getString(
                    when (event) {
                        ChatEvent.NOT_CONNECTED -> R.string.chat_send_failed_not_connected
                        ChatEvent.SEND_FAILED -> R.string.chat_send_failed
                    },
                ),
            )
        }
    }

    // Yeni mesaj gelince en alta kay
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.name ?: stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearLog, enabled = state.messages.isNotEmpty()) {
                        Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.chat_clear))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Composer(
                input = viewModel.input,
                hasError = viewModel.inputError,
                format = state.format,
                enabled = state.connection == ConnectionState.Connected,
                onInputChange = viewModel::onInputChange,
                onFormatChange = viewModel::setFormat,
                onSend = viewModel::send,
                onQuickSend = viewModel::sendQuick,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StatsStrip(state.connection, state.stats)

            if (state.messages.isEmpty()) {
                EmptyConversation(Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(message, state.format, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsStrip(connection: ConnectionState, stats: ByteStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ConnectionStatusPill(connection)
        Spacer(Modifier.weight(1f))
        ByteCounter(
            label = stringResource(R.string.chat_tx),
            bytes = stats.sentBytes,
            icon = Icons.Rounded.ArrowUpward,
            color = MaterialTheme.colorScheme.primary,
        )
        ByteCounter(
            label = stringResource(R.string.chat_rx),
            bytes = stats.receivedBytes,
            icon = Icons.Rounded.ArrowDownward,
            color = MaterialTheme.colorScheme.tertiary,
        )
    }
}

@Composable
private fun ByteCounter(label: String, bytes: Long, icon: ImageVector, color: Color) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Spacer(Modifier.width(6.dp))
        // Sayaç değiştikçe rakamlar yukarı doğru kayar
        AnimatedContent(
            targetState = bytes,
            transitionSpec = {
                (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
            },
            label = "byteCounter",
        ) { value ->
            Text(
                stringResource(R.string.chat_bytes, value),
                style = MonoStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, format: PayloadFormat, modifier: Modifier = Modifier) {
    val isTx = message.direction == MessageDirection.TX
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    val shape = if (isTx) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                val scale = 0.8f + 0.2f * appear.value
                scaleX = scale
                scaleY = scale
                // Baloncuk kendi tarafındaki alt köşeden büyür
                transformOrigin = TransformOrigin(if (isTx) 1f else 0f, 1f)
            },
        horizontalAlignment = if (isTx) Alignment.End else Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .shadow(6.dp, shape, ambientColor = BlipTheme.colors.glow, spotColor = BlipTheme.colors.glow)
                .clip(shape)
                .then(
                    if (isTx) {
                        Modifier.background(BlipTheme.colors.brandGradient)
                    } else {
                        Modifier
                            .background(BlipTheme.colors.bubbleRx)
                            .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f), shape)
                    },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                text = PayloadCodec.decode(message.payload, format),
                style = if (format == PayloadFormat.HEX) MonoStyle else MaterialTheme.typography.bodyLarge,
                color = if (isTx) Color.White else BlipTheme.colors.onBubbleRx,
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildString {
                append(stringResource(if (isTx) R.string.chat_tx else R.string.chat_rx))
                append(" · ")
                append(timeFormat.format(Date(message.timestampMillis)))
                append(" · ")
                append(stringResource(R.string.chat_bytes, message.payload.size.toLong()))
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun EmptyConversation(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BlipTheme.colors.coolGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Forum, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.chat_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.chat_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun Composer(
    input: String,
    hasError: Boolean,
    format: PayloadFormat,
    enabled: Boolean,
    onInputChange: (String) -> Unit,
    onFormatChange: (PayloadFormat) -> Unit,
    onSend: () -> Unit,
    onQuickSend: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedToggle(
                options = listOf(stringResource(R.string.chat_format_text), stringResource(R.string.chat_format_hex)),
                selectedIndex = if (format == PayloadFormat.TEXT) 0 else 1,
                onSelect = { onFormatChange(if (it == 0) PayloadFormat.TEXT else PayloadFormat.HEX) },
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                QUICK_COMMANDS.forEach { command ->
                    QuickChip(command, enabled = enabled, onClick = { onQuickSend(command) })
                }
            }
        }

        AnimatedVisibility(visible = hasError) {
            Text(
                stringResource(R.string.chat_invalid_hex),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        stringResource(
                            if (format == PayloadFormat.TEXT) R.string.chat_hint_text else R.string.chat_hint_hex,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                isError = hasError,
                textStyle = if (format == PayloadFormat.HEX) {
                    MonoStyle.copy(color = MaterialTheme.colorScheme.onSurface)
                } else {
                    MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                },
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = if (format == PayloadFormat.HEX) {
                        KeyboardCapitalization.Characters
                    } else {
                        KeyboardCapitalization.None
                    },
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
            Spacer(Modifier.width(10.dp))
            SendButton(enabled = enabled && input.isNotEmpty(), onClick = onSend)
        }
    }
}

@Composable
private fun QuickChip(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val color = MaterialTheme.colorScheme.tertiary
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = color.copy(alpha = if (enabled) 1f else 0.4f),
        modifier = Modifier
            .clip(shape)
            .border(1.dp, color.copy(alpha = if (enabled) 0.6f else 0.2f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    )
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .shadow(if (enabled) 10.dp else 0.dp, CircleShape, spotColor = BlipTheme.colors.glow)
            .clip(CircleShape)
            .background(BlipTheme.colors.brandGradient)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.Send,
            contentDescription = stringResource(R.string.chat_send),
            tint = Color.White,
        )
    }
}
