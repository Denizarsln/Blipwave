package dev.blipwave.data

import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.testing.FakeBackend
import dev.blipwave.testing.FakeBackendProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionManagerTest {

    private val backend = FakeBackend()
    private val provider = FakeBackendProvider(backend)

    private fun TestScope.newManager() = ConnectionManager(provider, backgroundScope, Clock { 0L })

    @Test
    fun `connect creates a session and starts connecting`() = runTest {
        val manager = newManager()

        val session = manager.connect("AA", "Sensor")

        assertEquals(1, backend.created.size)
        assertEquals("Sensor", session.connection.deviceName)
        assertEquals(ConnectionState.Connected, session.connection.state.value)
        assertSame(session, manager.session("AA"))
        assertEquals(setOf("AA"), manager.sessions.value.keys)
    }

    @Test
    fun `connecting twice reuses the session without reconnecting`() = runTest {
        val manager = newManager()

        val first = manager.connect("AA", null)
        val second = manager.connect("AA", null)

        assertSame(first, second)
        assertEquals(1, backend.created.size)
        assertEquals(1, backend.created.single().connectCalls)
    }

    @Test
    fun `connect on a disconnected session reconnects the same connection`() = runTest {
        val manager = newManager()
        manager.connect("AA", null)

        manager.disconnect("AA")
        assertEquals(ConnectionState.Disconnected(unexpected = false), manager.session("AA")?.connection?.state?.value)
        manager.connect("AA", null)

        assertEquals(1, backend.created.size)
        assertEquals(2, backend.created.single().connectCalls)
    }

    @Test
    fun `disconnect keeps the session so its log survives`() = runTest {
        val manager = newManager()
        val session = manager.connect("AA", null)
        session.send("hello".toByteArray())

        manager.disconnect("AA")

        assertEquals(1, manager.session("AA")?.messages?.value?.size)
    }

    @Test
    fun `forget closes the connection and removes the session`() = runTest {
        val manager = newManager()
        manager.connect("AA", null)
        manager.connect("BB", null)

        manager.forget("AA")

        assertNull(manager.session("AA"))
        assertEquals(setOf("BB"), manager.sessions.value.keys)
        assertTrue(backend.created.first { it.address == "AA" }.closed)
    }

    @Test
    fun `new sessions use the backend that is active at connect time`() = runTest {
        val manager = newManager()
        val demoBackend = FakeBackend(isDemo = true)
        manager.connect("AA", null)

        provider.backend.value = demoBackend
        manager.connect("BB", null)

        assertEquals(listOf("AA"), backend.created.map { it.address })
        assertEquals(listOf("BB"), demoBackend.created.map { it.address })
    }

    @Test
    fun `unknown addresses are ignored by disconnect and forget`() = runTest {
        val manager = newManager()

        manager.disconnect("ZZ")
        manager.forget("ZZ")

        assertTrue(manager.sessions.value.isEmpty())
    }
}
