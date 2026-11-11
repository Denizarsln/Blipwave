package dev.blipwave.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.testing.FakeScheduler;
import dev.blipwave.testing.Fakes.FakeConnection;

public class DeviceSessionTest {

    private final FakeScheduler time = new FakeScheduler();
    private FakeConnection connection;
    private DeviceSession session;
    private final List<BleError> results = new ArrayList<>();

    @Before
    public void setUp() {
        connection = new FakeConnection("AA", null);
        session = new DeviceSession(connection, time);
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static List<Integer> rssiValues(List<RssiHistory.Sample> samples) {
        List<Integer> values = new ArrayList<>();
        for (RssiHistory.Sample sample : samples) values.add(sample.rssi);
        return values;
    }

    @Test
    public void sentPayloadsAreLoggedAsTxAndCounted() {
        session.send(bytes("PING"), results::add);

        ChatMessage message = session.messages().get().get(0);
        assertEquals(ChatMessage.Direction.TX, message.direction);
        assertEquals("PING", new String(message.payload(), StandardCharsets.UTF_8));
        assertEquals(new ByteStats(4, 0), session.byteStats().get());
        assertEquals(1, connection.sent.size());
        assertEquals(Arrays.asList((BleError) null), results);
    }

    @Test
    public void incomingPayloadsAreLoggedAsRxAndCounted() {
        connection.incoming.emit(bytes("PONG!"));

        ChatMessage message = session.messages().get().get(0);
        assertEquals(ChatMessage.Direction.RX, message.direction);
        assertEquals(new ByteStats(0, 5), session.byteStats().get());
    }

    @Test
    public void failedSendsAreNeitherLoggedNorCounted() {
        connection.failSendsWith = BleError.notConnected();

        session.send(bytes("PING"), results::add);

        assertEquals(BleError.notConnected(), results.get(0));
        assertTrue(session.messages().get().isEmpty());
        assertEquals(ByteStats.EMPTY, session.byteStats().get());
    }

    @Test
    public void emptyPayloadsAreIgnored() {
        session.send(new byte[0], results::add);

        assertTrue(connection.sent.isEmpty());
        assertTrue(session.messages().get().isEmpty());
        assertNull(results.get(0));
    }

    @Test
    public void messagesGetIncreasingIdsAndTheClockTimestamp() {
        session.send(bytes("a"), results::add);
        time.advanceBy(1_500);
        connection.incoming.emit(bytes("b"));

        List<ChatMessage> messages = session.messages().get();
        assertTrue(messages.get(1).id > messages.get(0).id);
        assertEquals(0L, messages.get(0).timestampMillis);
        assertEquals(1_500L, messages.get(1).timestampMillis);
    }

    @Test
    public void rssiHistoryKeepsOnlyTheLast60Seconds() {
        connection.rssi.emit(-50);
        time.advanceBy(30_000);
        connection.rssi.emit(-60);
        time.advanceBy(40_000);
        connection.rssi.emit(-70);

        assertEquals(Arrays.asList(-60, -70), rssiValues(session.rssiHistory().get()));
    }

    @Test
    public void rssiHistoryRestartsOnANewConnectionAttempt() {
        connection.rssi.emit(-50);

        connection.state.set(ConnectionState.CONNECTING);

        assertTrue(session.rssiHistory().get().isEmpty());
    }

    @Test
    public void clearMessagesResetsTheLogAndTheCounters() {
        session.send(bytes("abc"), results::add);

        session.clearMessages();

        assertTrue(session.messages().get().isEmpty());
        assertEquals(ByteStats.EMPTY, session.byteStats().get());
    }

    @Test
    public void closeClosesTheConnectionAndStopsListening() {
        session.close();
        connection.incoming.emit(bytes("late"));

        assertTrue(connection.closed);
        assertTrue(session.messages().get().isEmpty());
        assertSame(connection, session.connection());
    }

    @Test
    public void rssiWindowBoundaryIsInclusive() {
        List<RssiHistory.Sample> history = Arrays.asList(
                new RssiHistory.Sample(0, -40), new RssiHistory.Sample(10_000, -50));

        List<RssiHistory.Sample> result = RssiHistory.append(history, new RssiHistory.Sample(60_000, -60));
        assertEquals(Arrays.asList(-40, -50, -60), rssiValues(result));

        result = RssiHistory.append(result, new RssiHistory.Sample(60_001, -70));
        assertEquals(Arrays.asList(-50, -60, -70), rssiValues(result));
    }
}
