package dev.blipwave.ble.demo

import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.data.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class DemoBackendTest {

    private val uartDevice = DemoCatalog.devices.first { it.hasUart && it.dropsAfterMillis == null && !it.sendsTelemetry }
    private val plainDevice = DemoCatalog.devices.first { !it.hasUart }
    private val flakyDevice = DemoCatalog.devices.first { it.dropsAfterMillis != null }

    private fun TestScope.connected(device: DemoDevice): DemoConnection {
        val connection = DemoConnection(device, backgroundScope, Random(7))
        connection.connect()
        advanceTimeBy(DemoConnection.CONNECT_DELAY_MILLIS + 1)
        return connection
    }

    private fun TestScope.collectIncoming(connection: DemoConnection): List<ByteArray> {
        val received = mutableListOf<ByteArray>()
        backgroundScope.launch { connection.incoming.collect { received += it } }
        runCurrent()
        return received
    }

    @Test
    fun `connect goes through Connecting before Connected`() = runTest {
        val connection = DemoConnection(uartDevice, backgroundScope, Random(7))
        assertEquals(ConnectionState.Idle, connection.state.value)

        connection.connect()
        assertEquals(ConnectionState.Connecting, connection.state.value)
        assertTrue(connection.services.value.isEmpty())

        advanceTimeBy(DemoConnection.CONNECT_DELAY_MILLIS + 1)
        assertEquals(ConnectionState.Connected, connection.state.value)
        assertTrue(connection.services.value.any { it.uuid == NordicUart.SERVICE })
    }

    @Test
    fun `PING is answered with PONG`() = runTest {
        val connection = connected(uartDevice)
        val received = collectIncoming(connection)

        assertTrue(connection.send("PING".toByteArray()).isSuccess)
        advanceTimeBy(1_000)

        assertEquals("PONG", received.single().decodeToString())
    }

    @Test
    fun `any other payload is echoed back unchanged`() = runTest {
        val connection = connected(uartDevice)
        val received = collectIncoming(connection)
        val payload = byteArrayOf(0x00, 0x7F, 0xFF.toByte())

        connection.send(payload)
        advanceTimeBy(1_000)

        assertArrayEquals(payload, received.single())
    }

    @Test
    fun `reply rule ignores case and surrounding whitespace`() {
        assertEquals("PONG", DemoConnection.replyFor("ping\r\n".toByteArray()).decodeToString())
        assertEquals("pinger", DemoConnection.replyFor("pinger".toByteArray()).decodeToString())
    }

    @Test
    fun `sending before the link is up fails with NotConnected`() = runTest {
        val connection = DemoConnection(uartDevice, backgroundScope, Random(7))
        connection.connect()

        val result = connection.send("PING".toByteArray())

        assertEquals(BleError.NotConnected, result.exceptionOrNull())
    }

    @Test
    fun `sending to a device without UART fails with UartUnavailable`() = runTest {
        val connection = connected(plainDevice)

        val result = connection.send("PING".toByteArray())

        assertEquals(BleError.UartUnavailable, result.exceptionOrNull())
    }

    @Test
    fun `user disconnect is reported as expected and cancels pending replies`() = runTest {
        val connection = connected(uartDevice)
        val received = collectIncoming(connection)
        connection.send("PING".toByteArray())

        connection.disconnect()
        advanceTimeBy(5_000)

        assertEquals(ConnectionState.Disconnected(unexpected = false), connection.state.value)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `flaky device drops the link unexpectedly and can reconnect`() = runTest {
        val connection = connected(flakyDevice)

        advanceTimeBy(flakyDevice.dropsAfterMillis!! + 1)
        val state = connection.state.value
        assertTrue(state is ConnectionState.Disconnected && state.unexpected)

        connection.connect()
        advanceTimeBy(DemoConnection.CONNECT_DELAY_MILLIS + 1)
        assertEquals(ConnectionState.Connected, connection.state.value)
    }

    @Test
    fun `rssi samples arrive every second and stay near the base level`() = runTest {
        val connection = DemoConnection(uartDevice, backgroundScope, Random(7))
        val samples = mutableListOf<Int>()
        backgroundScope.launch { connection.rssi.collect { samples += it } }
        runCurrent()

        connection.connect()
        advanceTimeBy(DemoConnection.CONNECT_DELAY_MILLIS + 4_500)

        assertEquals(5, samples.size)
        assertTrue(samples.all { it in (uartDevice.baseRssi - 12)..(uartDevice.baseRssi + 8) })
    }

    @Test
    fun `closing a connection that never connected stays Idle`() = runTest {
        val connection = DemoConnection(uartDevice, backgroundScope, Random(7))

        connection.close()

        assertEquals(ConnectionState.Idle, connection.state.value)
    }

    @Test
    fun `scanner reveals devices one per round and keeps rssi in range`() = runTest {
        val scanner = DemoBleScanner(Clock { testScheduler.currentTime }, Random(7))

        // 1 + 2 + 3 = ilk üç turun toplam yayını
        val firstRounds = scanner.scan().take(6).toList()
        assertEquals(
            listOf(0, 0, 1, 0, 1, 2).map { DemoCatalog.devices[it].address },
            firstRounds.map { it.address },
        )

        val many = scanner.scan().take(200).toList()
        assertEquals(DemoCatalog.devices.map { it.address }.toSet(), many.map { it.address }.toSet())
        many.forEach { result ->
            val base = DemoCatalog.find(result.address)!!.baseRssi
            assertTrue(result.rssi in (base - 10)..(base + 8))
        }
    }
}
