package dev.blipwave.ble;

import static dev.blipwave.testing.Fakes.device;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.blipwave.ble.model.ScannedDevice;

public class ScanResultsTest {

    private static List<String> addresses(List<ScannedDevice> devices) {
        List<String> result = new ArrayList<>();
        for (ScannedDevice device : devices) result.add(device.address);
        return result;
    }

    @Test
    public void mergeAddsNewDevicesAndReplacesExistingOnesByAddress() {
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), device("AA", null, -70, 0));
        map = ScanResults.merge(map, device("BB", null, -50, 0));
        map = ScanResults.merge(map, device("AA", null, -40, 5));

        assertEquals(2, map.size());
        assertEquals(-40, map.get("AA").rssi);
        assertEquals(5L, map.get("AA").lastSeenMillis);
    }

    @Test
    public void mergeDoesNotModifyTheInputMap() {
        Map<String, ScannedDevice> original = ScanResults.merge(Collections.emptyMap(), device("AA"));

        ScanResults.merge(original, device("BB"));

        assertEquals(1, original.size());
    }

    @Test
    public void mergeKeepsAKnownNameWhenALaterAdvertisementHasNone() {
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), device("AA", "Sensor", -60, 0));
        map = ScanResults.merge(map, device("AA", null, -45, 1));

        assertEquals("Sensor", map.get("AA").name);
        assertEquals(-45, map.get("AA").rssi);
    }

    @Test
    public void mergeLetsANewNameReplaceTheOldOne() {
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), device("AA", "Old", -60, 0));
        map = ScanResults.merge(map, device("AA", "New", -60, 1));

        assertEquals("New", map.get("AA").name);
    }

    @Test
    public void pruneStaleDropsDevicesNotSeenWithinTheWindow() {
        Map<String, ScannedDevice> map = new LinkedHashMap<>();
        map.put("AA", device("AA", null, -60, 0));
        map.put("BB", device("BB", null, -60, 9_000));

        Map<String, ScannedDevice> pruned = ScanResults.pruneStale(map, 12_000, 10_000);

        assertEquals(Collections.singleton("BB"), pruned.keySet());
    }

    @Test
    public void pruneStaleKeepsADeviceExactlyAtTheBoundary() {
        Map<String, ScannedDevice> map = Collections.singletonMap("AA", device("AA", null, -60, 2_000));

        assertTrue(ScanResults.pruneStale(map, 12_000, 10_000).containsKey("AA"));
    }

    @Test
    public void namedOnlyHidesDevicesWithoutAName() {
        List<ScannedDevice> devices = Arrays.asList(
                device("AA", "Lamp", -60, 0), device("BB"), device("CC", " ", -60, 0));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter().withNamedOnly(true));

        assertEquals(Collections.singletonList("AA"), addresses(result));
    }

    @Test
    public void minRssiKeepsOnlyDevicesAtOrAboveTheThreshold() {
        List<ScannedDevice> devices = Arrays.asList(
                device("AA", null, -50, 0), device("BB", null, -80, 0), device("CC", null, -81, 0));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter().withMinRssi(-80));

        assertEquals(Arrays.asList("AA", "BB"), addresses(result));
    }

    @Test
    public void minRssiIsClampedToTheSupportedRange() {
        assertEquals(-100, new ScanFilter().withMinRssi(-500).minRssi);
        assertEquals(-30, new ScanFilter().withMinRssi(0).minRssi);
    }

    @Test
    public void signalSortPutsTheStrongestDeviceFirst() {
        List<ScannedDevice> devices = Arrays.asList(
                device("AA", null, -90, 0), device("BB", null, -40, 0), device("CC", null, -65, 0));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter().withSortOrder(ScanFilter.SortOrder.SIGNAL));

        assertEquals(Arrays.asList("BB", "CC", "AA"), addresses(result));
    }

    @Test
    public void nameSortIsCaseInsensitiveAndPutsUnnamedDevicesLast() {
        List<ScannedDevice> devices = Arrays.asList(
                device("AA"), device("BB", "zeta", -60, 0), device("CC", "Alpha", -60, 0));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter().withSortOrder(ScanFilter.SortOrder.NAME));

        assertEquals(Arrays.asList("CC", "BB", "AA"), addresses(result));
    }

    @Test
    public void recentSortPutsTheMostRecentlySeenDeviceFirst() {
        List<ScannedDevice> devices = Arrays.asList(
                device("AA", null, -60, 100), device("BB", null, -60, 300), device("CC", null, -60, 200));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter().withSortOrder(ScanFilter.SortOrder.RECENT));

        assertEquals(Arrays.asList("BB", "CC", "AA"), addresses(result));
    }

    @Test
    public void equalSignalStrengthFallsBackToAStableAddressOrder() {
        List<ScannedDevice> devices = Arrays.asList(
                device("CC", null, -60, 0), device("AA", null, -60, 0), device("BB", null, -60, 0));

        List<ScannedDevice> result = ScanResults.apply(devices, new ScanFilter());

        assertEquals(Arrays.asList("AA", "BB", "CC"), addresses(result));
    }

    // ---- Klasik Bluetooth ---------------------------------------------------

    private static ScannedDevice classic(String address, String name, int rssi, long seen) {
        return ScannedDevice.classic(address, name, rssi, seen, ScannedDevice.Category.AUDIO_VIDEO);
    }

    @Test
    public void classicDevicesCannotOpenGatt() {
        ScannedDevice headset = classic("AA", "Headset", -60, 0);

        assertEquals(ScannedDevice.Transport.CLASSIC, headset.transport);
        assertTrue(headset.isClassic());
        assertTrue(!headset.canConnectGatt());
        // ...ama seri port (SPP) ile bağlanılabilir
        assertTrue(headset.canConnect());
    }

    @Test
    public void seeingAnLeDeviceInClassicDiscoveryMakesItDualAndKeepsItsLeData() {
        ScannedDevice le = new ScannedDevice("AA", null, -60, 0, true,
                Collections.singletonList(NordicUart.SERVICE));
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), le);

        map = ScanResults.merge(map, classic("AA", "Phone", -55, 1));

        ScannedDevice merged = map.get("AA");
        assertEquals(ScannedDevice.Transport.DUAL, merged.transport);
        assertEquals("Phone", merged.name);
        assertEquals(ScannedDevice.Category.AUDIO_VIDEO, merged.category);
        assertEquals(Collections.singletonList(NordicUart.SERVICE), merged.serviceUuids);
        assertTrue(merged.canConnectGatt());
    }

    @Test
    public void seeingAClassicDeviceInLeScanMakesItDualAndKeepsItsCategory() {
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), classic("AA", "Phone", -70, 0));

        map = ScanResults.merge(map, device("AA", null, -50, 1));

        ScannedDevice merged = map.get("AA");
        assertEquals(ScannedDevice.Transport.DUAL, merged.transport);
        assertEquals(ScannedDevice.Category.AUDIO_VIDEO, merged.category);
        assertEquals("Phone", merged.name);
        assertEquals(-50, merged.rssi);
    }

    @Test
    public void repeatedClassicResultsStayClassic() {
        Map<String, ScannedDevice> map = ScanResults.merge(Collections.emptyMap(), classic("AA", "Headset", -70, 0));

        map = ScanResults.merge(map, classic("AA", "Headset", -65, 1));

        assertEquals(ScannedDevice.Transport.CLASSIC, map.get("AA").transport);
    }

    @Test
    public void classicOnlyDevicesSurviveLongerBetweenDiscoveryRounds() {
        Map<String, ScannedDevice> map = new LinkedHashMap<>();
        map.put("LE", device("LE", null, -60, 0));
        map.put("CL", classic("CL", "Headset", -60, 0));

        assertEquals(Collections.singleton("CL"), ScanResults.pruneStale(map, 20_000).keySet());
        assertTrue(ScanResults.pruneStale(map, ScanResults.CLASSIC_STALE_AFTER_MILLIS + 1).isEmpty());
    }

    @Test
    public void transportFilterShowsDualDevicesInBothViews() {
        Map<String, ScannedDevice> map = new LinkedHashMap<>();
        map = ScanResults.merge(map, device("LE", null, -60, 0));
        map = ScanResults.merge(map, classic("CL", "Headset", -61, 0));
        map = ScanResults.merge(map, classic("DU", "Phone", -62, 0));
        map = ScanResults.merge(map, device("DU", null, -62, 1));

        ScanFilter filter = new ScanFilter();
        assertEquals(Arrays.asList("LE", "CL", "DU"), addresses(ScanResults.apply(map.values(), filter)));
        assertEquals(Arrays.asList("LE", "DU"),
                addresses(ScanResults.apply(map.values(), filter.withTransport(ScanFilter.TransportFilter.LE))));
        assertEquals(Arrays.asList("CL", "DU"),
                addresses(ScanResults.apply(map.values(), filter.withTransport(ScanFilter.TransportFilter.CLASSIC))));
    }

    @Test
    public void signalLevelBoundaries() {
        assertEquals(SignalLevel.EXCELLENT, SignalLevel.fromRssi(-60));
        assertEquals(SignalLevel.GOOD, SignalLevel.fromRssi(-61));
        assertEquals(SignalLevel.GOOD, SignalLevel.fromRssi(-72));
        assertEquals(SignalLevel.FAIR, SignalLevel.fromRssi(-85));
        assertEquals(SignalLevel.WEAK, SignalLevel.fromRssi(-86));
    }
}
