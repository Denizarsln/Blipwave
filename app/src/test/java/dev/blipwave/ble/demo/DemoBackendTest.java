package dev.blipwave.ble.demo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import dev.blipwave.ble.BleScanner;
import dev.blipwave.ble.NordicUart;
import dev.blipwave.ble.model.BleError;
import dev.blipwave.ble.model.ConnectionState;
import dev.blipwave.ble.model.GattServiceInfo;
import dev.blipwave.ble.model.ScannedDevice;
import dev.blipwave.testing.FakeScheduler;
import dev.blipwave.util.Cancellable;

public class DemoBackendTest {

    private final FakeScheduler time = new FakeScheduler();
    private final List<BleError> sendResults = new ArrayList<>();

    private static DemoDevice find(boolean uart, boolean telemetry, boolean drops) {
        for (DemoDevice device : DemoCatalog.DEVICES) {
            if (device.hasUart() == uart && device.sendsTelemetry == telemetry && (device.dropsAfterMillis > 0) == drops) {
                return device;
            }
        }
        throw new AssertionError("Katalogda uygun cihaz yok");
    }

    private final DemoDevice uartDevice = find(true, false, false);
    private final DemoDevice plainDevice = find(false, false, false);
    private final DemoDevice flakyDevice = find(true, false, true);

    private DemoConnection newConnection(DemoDevice device) {
        return new DemoConnection(device, time, new Random(7));
    }

    private DemoConnection connected(DemoDevice device) {
        DemoConnection connection = newConnection(device);
        connection.connect();
        time.advanceBy(DemoConnection.CONNECT_DELAY_MILLIS);
        return connection;
    }

    private static byte[] bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    public void connectGoesThroughConnectingBeforeConnected() {
        DemoConnection connection = newConnection(uartDevice);
        assertEquals(ConnectionState.IDLE, connection.state().get());

        connection.connect();
        assertEquals(ConnectionState.CONNECTING, connection.state().get());
        assertTrue(connection.services().get().isEmpty());

        time.advanceBy(DemoConnection.CONNECT_DELAY_MILLIS);
        assertEquals(ConnectionState.CONNECTED, connection.state().get());
        boolean hasUart = false;
        for (GattServiceInfo service : connection.services().get()) hasUart |= NordicUart.SERVICE.equals(service.uuid);
        assertTrue(hasUart);
    }

    @Test
    public void pingIsAnsweredWithPong() {
        DemoConnection connection = connected(uartDevice);
        List<byte[]> received = new ArrayList<>();
        connection.incoming().subscribe(received::add);

        connection.send(bytes("PING"), sendResults::add);
        time.advanceBy(1_000);

        assertNull(sendResults.get(0));
        assertEquals(1, received.size());
        assertEquals("PONG", new String(received.get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void anyOtherPayloadIsEchoedBackUnchanged() {
        DemoConnection connection = connected(uartDevice);
        List<byte[]> received = new ArrayList<>();
        connection.incoming().subscribe(received::add);
        byte[] payload = {0x00, 0x7F, (byte) 0xFF};

        connection.send(payload, sendResults::add);
        time.advanceBy(1_000);

        assertArrayEquals(payload, received.get(0));
    }

    @Test
    public void replyRuleIgnoresCaseAndSurroundingWhitespace() {
        assertEquals("PONG", new String(DemoConnection.replyFor(bytes("ping\r\n")), StandardCharsets.UTF_8));
        assertEquals("pinger", new String(DemoConnection.replyFor(bytes("pinger")), StandardCharsets.UTF_8));
    }

    @Test
    public void sendingBeforeTheLinkIsUpFailsWithNotConnected() {
        DemoConnection connection = newConnection(uartDevice);
        connection.connect();

        connection.send(bytes("PING"), sendResults::add);

        assertEquals(BleError.notConnected(), sendResults.get(0));
    }

    @Test
    public void sendingToADeviceWithoutUartFailsWithUartUnavailable() {
        DemoConnection connection = connected(plainDevice);

        connection.send(bytes("PING"), sendResults::add);

        assertEquals(BleError.uartUnavailable(), sendResults.get(0));
    }

    @Test
    public void userDisconnectIsReportedAsExpectedAndCancelsPendingReplies() {
        DemoConnection connection = connected(uartDevice);
        List<byte[]> received = new ArrayList<>();
        connection.incoming().subscribe(received::add);
        connection.send(bytes("PING"), sendResults::add);

        connection.disconnect();
        time.advanceBy(5_000);

        assertEquals(ConnectionState.disconnectedByUser(), connection.state().get());
        assertTrue(received.isEmpty());
        assertEquals(0, time.pendingTaskCount());
    }

    @Test
    public void flakyDeviceDropsTheLinkUnexpectedlyAndCanReconnect() {
        DemoConnection connection = connected(flakyDevice);

        time.advanceBy(flakyDevice.dropsAfterMillis);
        assertTrue(connection.state().get().isLost());

        connection.connect();
        time.advanceBy(DemoConnection.CONNECT_DELAY_MILLIS);
        assertEquals(ConnectionState.CONNECTED, connection.state().get());
    }

    @Test
    public void rssiSamplesArriveEverySecondAndStayNearTheBaseLevel() {
        DemoConnection connection = newConnection(uartDevice);
        List<Integer> samples = new ArrayList<>();
        connection.rssi().subscribe(samples::add);

        connection.connect();
        time.advanceBy(DemoConnection.CONNECT_DELAY_MILLIS + 4_500);

        assertEquals(5, samples.size());
        for (int value : samples) {
            assertTrue(value >= uartDevice.baseRssi - 12 && value <= uartDevice.baseRssi + 8);
        }
    }

    @Test
    public void closingAConnectionThatNeverConnectedStaysIdle() {
        DemoConnection connection = newConnection(uartDevice);

        connection.close();

        assertEquals(ConnectionState.IDLE, connection.state().get());
    }

    @Test
    public void classicDemoDeviceConnectsOverSerialPortAndEchoes() {
        DemoDevice headset = null;
        for (DemoDevice device : DemoCatalog.DEVICES) {
            if (device.transport == ScannedDevice.Transport.CLASSIC) {
                headset = device;
                break;
            }
        }
        DemoConnection connection = connected(headset);
        List<byte[]> received = new ArrayList<>();
        connection.incoming().subscribe(received::add);

        assertTrue(dev.blipwave.ble.GattUuids.isSerialPort(connection.services().get()));
        connection.send(bytes("PING"), sendResults::add);
        time.advanceBy(1_000);

        assertNull(sendResults.get(0));
        assertEquals("PONG", new String(received.get(0), StandardCharsets.UTF_8));
    }

    @Test
    public void scannerReportsClassicAndDualDevicesWithTheirTransport() {
        DemoBleScanner scanner = new DemoBleScanner(time, time, new Random(7), DemoCatalog.DEVICES);
        java.util.Map<String, ScannedDevice> latest = new java.util.HashMap<>();
        Cancellable handle = scanner.start(new BleScanner.Listener() {
            @Override public void onDevice(ScannedDevice device) { latest.put(device.address, device); }
            @Override public void onError(BleError error) { throw new AssertionError(error); }
        });

        time.advanceBy(DemoBleScanner.INTERVAL_MILLIS * DemoCatalog.DEVICES.size());
        handle.cancel();

        int classicCount = 0;
        int dualCount = 0;
        for (ScannedDevice device : latest.values()) {
            if (device.transport == ScannedDevice.Transport.CLASSIC) {
                classicCount++;
                assertTrue(!device.canConnectGatt());
                assertEquals(ScannedDevice.Category.AUDIO_VIDEO, device.category);
            }
            if (device.transport == ScannedDevice.Transport.DUAL) {
                dualCount++;
                assertTrue(device.canConnectGatt());
            }
        }
        assertEquals(2, classicCount);
        assertEquals(1, dualCount);
    }

    @Test
    public void scannerRevealsOneMoreDevicePerRoundAndKeepsRssiInRange() {
        DemoBleScanner scanner = new DemoBleScanner(time, time, new Random(7), DemoCatalog.DEVICES);
        List<ScannedDevice> results = new ArrayList<>();
        Cancellable handle = scanner.start(new BleScanner.Listener() {
            @Override public void onDevice(ScannedDevice device) { results.add(device); }
            @Override public void onError(BleError error) { throw new AssertionError(error); }
        });

        // İlk üç tur: 1 + 2 + 3 yayın
        time.advanceBy(DemoBleScanner.INTERVAL_MILLIS * 2);
        List<String> firstRounds = new ArrayList<>();
        for (ScannedDevice device : results) firstRounds.add(device.address);
        List<DemoDevice> catalog = DemoCatalog.DEVICES;
        assertEquals(Arrays.asList(
                catalog.get(0).address,
                catalog.get(0).address, catalog.get(1).address,
                catalog.get(0).address, catalog.get(1).address, catalog.get(2).address), firstRounds);

        time.advanceBy(DemoBleScanner.INTERVAL_MILLIS * 30);
        Set<String> seen = new HashSet<>();
        for (ScannedDevice device : results) {
            seen.add(device.address);
            DemoDevice source = DemoCatalog.find(device.address);
            assertTrue(device.rssi >= source.baseRssi - 10 && device.rssi <= source.baseRssi + 8);
        }
        assertEquals(catalog.size(), seen.size());

        handle.cancel();
        int countAfterStop = results.size();
        time.advanceBy(5_000);
        assertEquals(countAfterStop, results.size());
    }
}
