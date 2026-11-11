package dev.blipwave.ble.android

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.blipwave.ble.BleBackend
import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.BleScanner
import dev.blipwave.data.Clock
import dev.blipwave.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidBleBackend @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    clock: Clock,
) : BleBackend {
    override val isDemo: Boolean = false
    override val scanner: BleScanner = AndroidBleScanner(context, clock)

    override fun createConnection(address: String, name: String?): BleConnection =
        AndroidGattConnection(context, address, name, scope)
}
