package dev.blipwave.data

import dev.blipwave.ble.model.BleError
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.testing.FakeConnection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceSessionTest {

    private fun TestScope.newSession(connection: FakeConnection): DeviceSession {
        val session = DeviceSession(connection, backgroundScope, Clock { testScheduler.currentTime })
        runCurrent() // toplayıcılar abone olsun
        return session
    }

    @Test
    fun `sent payloads are logged as TX and counted`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        session.send("PING".toByteArray())

        val message = session.messages.value.single()
        assertEquals(MessageDirection.TX, message.direction)
        assertEquals("PING", message.payload.decodeToString())
        assertEquals(ByteStats(sentBytes = 4, receivedBytes = 0), session.byteStats.value)
        assertEquals(1, connection.sent.size)
    }

    @Test
    fun `incoming payloads are logged as RX and counted`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        connection.incoming.emit("PONG!".toByteArray())
        runCurrent()

        val message = session.messages.value.single()
        assertEquals(MessageDirection.RX, message.direction)
        assertEquals(ByteStats(sentBytes = 0, receivedBytes = 5), session.byteStats.value)
    }

    @Test
    fun `failed sends are neither logged nor counted`() = runTest {
        val connection = FakeConnection("AA").apply { failSendsWith = BleError.NotConnected }
        val session = newSession(connection)

        val result = session.send("PING".toByteArray())

        assertTrue(result.isFailure)
        assertTrue(session.messages.value.isEmpty())
        assertEquals(ByteStats(), session.byteStats.value)
    }

    @Test
    fun `empty payloads are ignored`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        session.send(ByteArray(0))

        assertTrue(connection.sent.isEmpty())
        assertTrue(session.messages.value.isEmpty())
    }

    @Test
    fun `messages get increasing ids and the clock timestamp`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        session.send("a".toByteArray())
        advanceTimeBy(1_500)
        connection.incoming.emit("b".toByteArray())
        runCurrent()

        val (first, second) = session.messages.value
        assertTrue(second.id > first.id)
        assertEquals(0L, first.timestampMillis)
        assertEquals(1_500L, second.timestampMillis)
    }

    @Test
    fun `rssi history keeps only the last 60 seconds`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        connection.rssi.emit(-50)
        runCurrent()
        advanceTimeBy(30_000)
        connection.rssi.emit(-60)
        runCurrent()
        advanceTimeBy(40_000)
        connection.rssi.emit(-70)
        runCurrent()

        assertEquals(listOf(-60, -70), session.rssiHistory.value.map { it.rssi })
    }

    @Test
    fun `rssi history restarts on a new connection attempt`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)
        connection.rssi.emit(-50)
        runCurrent()

        connection.state.value = ConnectionState.Connecting
        runCurrent()

        assertTrue(session.rssiHistory.value.isEmpty())
    }

    @Test
    fun `clearMessages resets the log and the counters`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)
        session.send("abc".toByteArray())

        session.clearMessages()

        assertTrue(session.messages.value.isEmpty())
        assertEquals(ByteStats(), session.byteStats.value)
    }

    @Test
    fun `close closes the connection and stops collecting`() = runTest {
        val connection = FakeConnection("AA")
        val session = newSession(connection)

        session.close()
        runCurrent()
        connection.incoming.emit("late".toByteArray())
        runCurrent()

        assertTrue(connection.closed)
        assertTrue(session.messages.value.isEmpty())
    }

    @Test
    fun `rssi window boundary is inclusive`() {
        val history = listOf(RssiSample(0, -40), RssiSample(10_000, -50))

        val result = RssiHistory.append(history, RssiSample(60_000, -60))

        assertEquals(listOf(-40, -50, -60), result.map { it.rssi })
        assertEquals(listOf(-50, -60, -70), RssiHistory.append(result, RssiSample(60_001, -70)).map { it.rssi })
    }
}
