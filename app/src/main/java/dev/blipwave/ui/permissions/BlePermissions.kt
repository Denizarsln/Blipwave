package dev.blipwave.ui.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.blipwave.R
import dev.blipwave.ui.components.GradientButton
import dev.blipwave.ui.components.GradientCard
import dev.blipwave.ui.theme.BlipTheme

/** Android 12+ için yeni Bluetooth izinleri, öncesi için konum izni. */
fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Stable
class BlePermissionState(
    val granted: Boolean,
    /** Kullanıcı reddetti; sistem artık izin penceresini göstermeyebilir. */
    val denied: Boolean,
    val permanentlyDenied: Boolean,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

@Composable
fun rememberBlePermissionState(): BlePermissionState {
    val context = LocalContext.current
    val permissions = remember { requiredBlePermissions() }

    fun allGranted() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var granted by remember { mutableStateOf(allGranted()) }
    var deniedOnce by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        granted = result.values.all { it }
        if (!granted) deniedOnce = true
    }

    // Kullanıcı sistem ayarlarından dönünce izin durumunu tazele
    LifecycleResumeEffect(Unit) {
        granted = allGranted()
        onPauseOrDispose { }
    }

    val activity = context.findActivity()
    val canAskAgain = activity != null && permissions.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }

    return BlePermissionState(
        granted = granted,
        denied = deniedOnce && !granted,
        permanentlyDenied = deniedOnce && !granted && !canAskAgain,
        request = { launcher.launch(permissions) },
        openSettings = {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
    )
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** İzin verilmeden önce ve reddedildiğinde gösterilen açıklayıcı kart. */
@Composable
fun PermissionExplainer(
    state: BlePermissionState,
    onTryDemo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GradientCard(modifier = modifier.fillMaxWidth(), brush = BlipTheme.colors.heroGradient) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(BlipTheme.colors.warmGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
            }
            Text(
                stringResource(R.string.permission_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    when {
                        state.denied -> R.string.permission_denied_body
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> R.string.permission_body_s
                        else -> R.string.permission_body_legacy
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            if (state.permanentlyDenied) {
                GradientButton(
                    text = stringResource(R.string.permission_open_settings),
                    icon = Icons.Rounded.Settings,
                    onClick = state.openSettings,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                GradientButton(
                    text = stringResource(R.string.permission_grant),
                    onClick = state.request,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedButton(onClick = onTryDemo, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Science, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_try_demo))
            }
        }
    }
}
