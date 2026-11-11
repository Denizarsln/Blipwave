package dev.blipwave.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;

import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.testing.FakeScheduler;
import dev.blipwave.testing.Fakes.FakeBackend;
import dev.blipwave.testing.Fakes.FakeBackendProvider;
import dev.blipwave.testing.Fakes.FakeConnection;

public class ConnectionManagerTest {

    private FakeBackend backend;
    private FakeBackendProvider provider;
    private ConnectionManager manager;

    @Before
    public void setUp() {
        backend = new FakeBackend(false);
        provider = new FakeBackendProvider(backend);
        manager = new ConnectionManager(provider, new FakeScheduler());
    }

    @Test
    public void connectCreatesASessionAndStartsConnecting() {
        DeviceSession session = manager.connect("AA", "Sensor");

        assertEquals(1, backend.created.size());
        assertEquals("Sensor", session.connection().getDeviceName());
        assertEquals(ConnectionState.CONNECTED, session.connection().state().get());
        assertSame(session, manager.session("AA"));
        assertEquals(Collections.singleton("AA"), manager.sessions().get().keySet());
    }

    @Test
    public void connectingTwiceReusesTheSessionWithoutReconnecting() {
        DeviceSession first = manager.connect("AA", null);
        DeviceSession second = manager.connect("AA", null);

        assertSame(first, second);
        assertEquals(1, backend.created.size());
        assertEquals(1, backend.created.get(0).connectCalls);
    }

    @Test
    public void connectOnADisconnectedSessionReconnectsTheSameConnection() {
        manager.connect("AA", null);
        manager.disconnect("AA");
        assertEquals(ConnectionState.disconnectedByUser(), manager.session("AA").connection().state().get());

        manager.connect("AA", null);

        assertEquals(1, backend.created.size());
        assertEquals(2, backend.created.get(0).connectCalls);
    }

    @Test
    public void disconnectKeepsTheSessionSoItsLogSurvives() {
        DeviceSession session = manager.connect("AA", null);
        session.send("hello".getBytes(StandardCharsets.UTF_8), error -> { });

        manager.disconnect("AA");

        assertNotNull(manager.session("AA"));
        assertEquals(1, manager.session("AA").messages().get().size());
    }

    @Test
    public void forgetClosesTheConnectionAndRemovesTheSession() {
        manager.connect("AA", null);
        manager.connect("BB", null);

        manager.forget("AA");

        assertNull(manager.session("AA"));
        assertEquals(Collections.singleton("BB"), manager.sessions().get().keySet());
        FakeConnection forgotten = backend.connectionFor("AA");
        assertNotNull(forgotten);
        assertTrue(forgotten.closed);
    }

    @Test
    public void newSessionsUseTheBackendActiveAtConnectTime() {
        FakeBackend demoBackend = new FakeBackend(true);
        manager.connect("AA", null);

        provider.backend.set(demoBackend);
        manager.connect("BB", null);

        assertEquals(1, backend.created.size());
        assertEquals("AA", backend.created.get(0).getAddress());
        assertEquals(1, demoBackend.created.size());
        assertEquals("BB", demoBackend.created.get(0).getAddress());
    }

    @Test
    public void classicConnectionsAreRequestedFromTheBackend() {
        manager.connect("AA", "Tag");
        manager.connect("BB", "Headset", true);

        assertEquals(java.util.Arrays.asList(false, true), backend.classicFlags);
    }

    @Test
    public void unknownAddressesAreIgnoredByDisconnectAndForget() {
        manager.disconnect("ZZ");
        manager.forget("ZZ");

        assertTrue(manager.sessions().get().isEmpty());
    }
}
