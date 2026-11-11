package dev.blipwave.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class PayloadCodecTest {

    private static final byte[] PING = {0x50, 0x49, 0x4E, 0x47};

    @Test
    public void textIsEncodedAsUtf8() {
        assertArrayEquals(PING, PayloadCodec.encode("PING", PayloadCodec.Format.TEXT));
    }

    @Test
    public void nonAsciiTextRoundTrips() {
        byte[] bytes = PayloadCodec.encode("ışık ✓", PayloadCodec.Format.TEXT);

        assertEquals("ışık ✓", PayloadCodec.decode(bytes, PayloadCodec.Format.TEXT));
    }

    @Test
    public void hexAcceptsSpacesColonsCommasAndPrefixes() {
        assertArrayEquals(PING, PayloadCodec.parseHex("50 49 4E 47"));
        assertArrayEquals(PING, PayloadCodec.parseHex("50:49:4e:47"));
        assertArrayEquals(PING, PayloadCodec.parseHex("0x50, 0x49, 0x4E, 0x47"));
        assertArrayEquals(PING, PayloadCodec.parseHex("50494e47"));
    }

    @Test
    public void hexHandlesBytesAbove0x7F() {
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0x80, 0x00}, PayloadCodec.parseHex("FF 80 00"));
    }

    @Test(expected = PayloadCodec.InvalidHexException.class)
    public void oddNumberOfHexDigitsIsRejected() {
        PayloadCodec.encode("ABC", PayloadCodec.Format.HEX);
    }

    @Test(expected = PayloadCodec.InvalidHexException.class)
    public void nonHexCharactersAreRejected() {
        PayloadCodec.encode("ZZ", PayloadCodec.Format.HEX);
    }

    @Test(expected = PayloadCodec.InvalidHexException.class)
    public void blankHexInputIsRejected() {
        PayloadCodec.encode("  ", PayloadCodec.Format.HEX);
    }

    @Test
    public void hexDecodingIsUpperCaseAndSpaceSeparated() {
        assertEquals("0A FF 1B", PayloadCodec.decode(new byte[]{0x0A, (byte) 0xFF, 0x1b}, PayloadCodec.Format.HEX));
    }

    @Test
    public void hexRoundTripsEveryByteValue() {
        byte[] original = new byte[256];
        for (int i = 0; i < original.length; i++) original[i] = (byte) i;

        assertArrayEquals(original, PayloadCodec.parseHex(PayloadCodec.toHex(original)));
    }

    @Test
    public void displayTextMakesControlCharactersVisible() {
        String text = PayloadCodec.toDisplayText("OK\r\n\0".getBytes(StandardCharsets.UTF_8));

        assertEquals("OK␍␤·", text);
    }
}
