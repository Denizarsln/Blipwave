package dev.blipwave.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.util.Log
import dev.blipwave.ble.BleConnection
import dev.blipwave.ble.GattUuids
import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.CharacteristicProperty
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ble.model.GattCharacteristicInfo
import dev.blipwave.ble.model.GattServiceInfo
import dev.blipwave.ble.model.isActive
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Gerçek GATT bağlantısı. Akış: bağlan → MTU iste → servisleri keşfet →
 * (varsa) UART TX bildirimlerini aç → Connected.
 */
@SuppressLint("MissingPermission") // İzinler UI katmanında PermissionGate ile alınır
class AndroidGattConnection(
    private val context: Context,
    override val address: String,
    override val deviceName: String?,
    private val scope: CoroutineScope,
) : BleConnection {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _services = MutableStateFlow<List<GattServiceInfo>>(emptyList())
    override val services: StateFlow<List<GattServiceInfo>> = _services.asStateFlow()

    private val _rssi = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    override val rssi: SharedFlow<Int> = _rssi.asSharedFlow()

    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incoming: SharedFlow<ByteArray> = _incoming.asSharedFlow()

    private val lock = Any()
    private var gatt: BluetoothGatt? = null
    private var uartRx: BluetoothGattCharacteristic? = null
    private var mtu = DEFAULT_MTU
    private var rssiJob: Job? = null
    private var timeoutJob: Job? = null

    private val writeMutex = Mutex()

    @Volatile
    private var pendingWrite: CompletableDeferred<Boolean>? = null

    override fun connect() {
        synchronized(lock) {
            if (_state.value.isActive) return
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                _state.value = ConnectionState.Disconnected(unexpected = true, reason = "Bluetooth off")
                return
            }
            _state.value = ConnectionState.Connecting
            _services.value = emptyList()
            try {
                val device = adapter.getRemoteDevice(address)
                gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            } catch (e: SecurityException) {
                _state.value = ConnectionState.Disconnected(unexpected = true, reason = "Permission denied")
                return
            } catch (e: IllegalArgumentException) {
                _state.value = ConnectionState.Disconnected(unexpected = true, reason = "Invalid address")
                return
            }
            timeoutJob?.cancel()
            timeoutJob = scope.launch {
                delay(CONNECT_TIMEOUT_MILLIS)
                if (_state.value == ConnectionState.Connecting) {
                    Log.w(TAG, "Bağlantı zaman aşımına uğradı: $address")
                    teardown(ConnectionState.Disconnected(unexpected = true, reason = "Timeout"))
                }
            }
        }
    }

    override fun disconnect() {
        teardown(ConnectionState.Disconnected(unexpected = false))
    }

    override fun close() {
        teardown(ConnectionState.Disconnected(unexpected = false))
    }

    override suspend fun send(data: ByteArray): Result<Unit> = writeMutex.withLock {
        val currentGatt = gatt
        val characteristic = uartRx
        if (_state.value != ConnectionState.Connected || currentGatt == null) {
            return@withLock Result.failure(BleError.NotConnected)
        }
        if (characteristic == null) return@withLock Result.failure(BleError.UartUnavailable)

        val writeType = if (
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        ) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        // ATT başlığı 3 bayt; kalan MTU kadar parçalar halinde yaz
        val chunkSize = (mtu - 3).coerceAtLeast(1)
        var offset = 0
        while (offset < data.size) {
            val chunk = data.copyOfRange(offset, minOf(offset + chunkSize, data.size))
            val deferred = CompletableDeferred<Boolean>()
            pendingWrite = deferred
            if (!startWrite(currentGatt, characteristic, chunk, writeType)) {
                pendingWrite = null
                return@withLock Result.failure(BleError.WriteFailed)
            }
            val acknowledged = withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) { deferred.await() } ?: false
            pendingWrite = null
            if (!acknowledged) return@withLock Result.failure(BleError.WriteFailed)
            offset += chunk.size
        }
        Result.success(Unit)
    }

    private fun startWrite(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                characteristic.writeType = writeType
                characteristic.value = value
                gatt.writeCharacteristic(characteristic)
            }
        }
    } catch (e: SecurityException) {
        false
    }

    private fun teardown(finalState: ConnectionState) {
        synchronized(lock) {
            timeoutJob?.cancel()
            rssiJob?.cancel()
            pendingWrite?.complete(false)
            uartRx = null
            mtu = DEFAULT_MTU
            gatt?.let {
                runCatching { it.disconnect() }
                runCatching { it.close() }
            }
            gatt = null
            // Hiç bağlanmamış bir nesneyi kapatmak "koptu" olarak görünmemeli
            if (_state.value != ConnectionState.Idle) _state.value = finalState
        }
    }

    private fun markConnected(gatt: BluetoothGatt) {
        synchronized(lock) {
            if (this.gatt !== gatt) return
            timeoutJob?.cancel()
            _state.value = ConnectionState.Connected
            rssiJob?.cancel()
            rssiJob = scope.launch {
                while (isActive) {
                    runCatching { gatt.readRemoteRssi() }
                    delay(RSSI_INTERVAL_MILLIS)
                }
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // Bazı cihazlar MTU isteğine yanıt vermez; istek reddedilirse doğrudan keşfe geç
                if (!gatt.requestMtu(PREFERRED_MTU)) gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Bağlantı koptu: $address (durum $status)")
                val reason = if (status == BluetoothGatt.GATT_SUCCESS) null else "GATT $status"
                // Kullanıcı kaynaklı kopuş teardown() içinde zaten işlenir; buraya düşen beklenmediktir
                if (this@AndroidGattConnection.gatt === gatt) {
                    teardown(ConnectionState.Disconnected(unexpected = true, reason = reason))
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) this@AndroidGattConnection.mtu = mtu
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                teardown(ConnectionState.Disconnected(unexpected = true, reason = "Discovery failed ($status)"))
                return
            }
            _services.value = gatt.services.map { service ->
                GattServiceInfo(
                    uuid = service.uuid,
                    characteristics = service.characteristics.map {
                        GattCharacteristicInfo(it.uuid, it.properties.toPropertySet())
                    },
                )
            }

            val uart = gatt.getService(NordicUart.SERVICE)
            uartRx = uart?.getCharacteristic(NordicUart.RX_CHARACTERISTIC)
            val tx = uart?.getCharacteristic(NordicUart.TX_CHARACTERISTIC)
            val cccd = tx?.getDescriptor(GattUuids.CLIENT_CONFIG_DESCRIPTOR)
            if (tx == null || cccd == null || !enableNotifications(gatt, tx, cccd)) {
                markConnected(gatt)
            }
            // Bildirim açılırsa Connected durumuna onDescriptorWrite içinde geçilir
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            markConnected(gatt)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            pendingWrite?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }

        // Android 13+
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (characteristic.uuid == NordicUart.TX_CHARACTERISTIC) _incoming.tryEmit(value)
        }

        // Android 12 ve öncesi
        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            @Suppress("DEPRECATION")
            val value = characteristic.value ?: return
            if (characteristic.uuid == NordicUart.TX_CHARACTERISTIC) _incoming.tryEmit(value.copyOf())
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) _rssi.tryEmit(rssi)
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        cccd: BluetoothGattDescriptor,
    ): Boolean {
        if (!gatt.setCharacteristicNotification(characteristic, true)) return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(cccd, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                cccd.value = value
                gatt.writeDescriptor(cccd)
            }
        }
    }

    private fun Int.toPropertySet(): Set<CharacteristicProperty> = buildSet {
        if (this@toPropertySet and BluetoothGattCharacteristic.PROPERTY_READ != 0) add(CharacteristicProperty.READ)
        if (this@toPropertySet and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) add(CharacteristicProperty.WRITE)
        if (this@toPropertySet and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0) {
            add(CharacteristicProperty.WRITE_NO_RESPONSE)
        }
        if (this@toPropertySet and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) add(CharacteristicProperty.NOTIFY)
        if (this@toPropertySet and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) add(CharacteristicProperty.INDICATE)
    }

    private companion object {
        const val TAG = "AndroidGattConnection"
        const val DEFAULT_MTU = 23
        const val PREFERRED_MTU = 247
        const val CONNECT_TIMEOUT_MILLIS = 15_000L
        const val WRITE_TIMEOUT_MILLIS = 5_000L
        const val RSSI_INTERVAL_MILLIS = 1_000L
    }
}
