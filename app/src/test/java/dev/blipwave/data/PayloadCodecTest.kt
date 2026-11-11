package dev.blipwave.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadCodecTest {

    @Test
    fun `text is encoded as UTF-8`() {
        val bytes = PayloadCodec.encode("PING", PayloadFormat.TEXT).getOrThrow()

        assertArrayEquals(byteArrayOf(0x50, 0x49, 0x4E, 0x47), bytes)
    }

    @Test
    fun `non ASCII text round trips`() {
        val bytes = PayloadCodec.encode("ışık 💡", PayloadFormat.TEXT).getOrThrow()

        assertEquals("ışık 💡", PayloadCodec.decode(bytes, PayloadFormat.TEXT))
    }

    @Test
    fun `hex accepts spaces, colons, commas and 0x prefixes`() {
        val expected = byteArrayOf(0x50, 0x49, 0x4E, 0x47)

        assertArrayEquals(expected, PayloadCodec.parseHex("50 49 4E 47"))
        assertArrayEquals(expected, PayloadCodec.parseHex("50:49:4e:47"))
        assertArrayEquals(expected, PayloadCodec.parseHex("0x50, 0x49, 0x4E, 0x47"))
        assertArrayEquals(expected, PayloadCodec.parseHex("50494e47"))
    }

    @Test
    fun `hex handles bytes above 0x7F`() {
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x80.toByte(), 0x00), PayloadCodec.parseHex("FF 80 00"))
    }

    @Test
    fun `odd number of hex digits is rejected`() {
        val result = PayloadCodec.encode("ABC", PayloadFormat.HEX)

        assertTrue(result.exceptionOrNull() is PayloadCodec.InvalidHexException)
    }

    @Test
    fun `non hex characters are rejected`() {
        val result = PayloadCodec.encode("ZZ", PayloadFormat.HEX)

        assertTrue(result.exceptionOrNull() is PayloadCodec.InvalidHexException)
    }

    @Test
    fun `blank hex input is rejected`() {
        val result = PayloadCodec.encode("  ", PayloadFormat.HEX)

        assertTrue(result.exceptionOrNull() is PayloadCodec.InvalidHexException)
    }

    @Test
    fun `hex decoding is upper case and space separated`() {
        val text = PayloadCodec.decode(byteArrayOf(0x0A, 0xFF.toByte(), 0x1b), PayloadFormat.HEX)

        assertEquals("0A FF 1B", text)
    }

    @Test
    fun `hex round trips`() {
        val original = ByteArray(256) { it.toByte() }

        assertArrayEquals(original, PayloadCodec.parseHex(PayloadCodec.toHex(original)))
    }

    @Test
    fun `display text makes control characters visible`() {
        val text = PayloadCodec.toDisplayText(byteArrayOf(0x4F, 0x4B, 0x0D, 0x0A, 0x00))

        assertEquals("OK␍␤·", text)
    }
}
