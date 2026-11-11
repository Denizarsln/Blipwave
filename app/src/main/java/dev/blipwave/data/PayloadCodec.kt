package dev.blipwave.data

enum class PayloadFormat { TEXT, HEX }

/** Kullanıcı girdisi ↔ bayt dizisi dönüşümleri (Text / HEX modu). */
object PayloadCodec {

    class InvalidHexException(message: String) : IllegalArgumentException(message)

    fun encode(input: String, format: PayloadFormat): Result<ByteArray> = when (format) {
        PayloadFormat.TEXT -> Result.success(input.toByteArray(Charsets.UTF_8))
        PayloadFormat.HEX -> runCatching { parseHex(input) }
    }

    fun decode(bytes: ByteArray, format: PayloadFormat): String = when (format) {
        PayloadFormat.TEXT -> toDisplayText(bytes)
        PayloadFormat.HEX -> toHex(bytes)
    }

    /** "48 65 6C", "48:65:6c", "0x48,0x65" ve "48656C" biçimlerini kabul eder. */
    fun parseHex(input: String): ByteArray {
        val digits = input
            .replace("0x", "", ignoreCase = true)
            .filterNot { it.isWhitespace() || it == ':' || it == ',' || it == '-' }
        if (digits.isEmpty()) throw InvalidHexException("Boş HEX girdisi")
        if (digits.length % 2 != 0) throw InvalidHexException("HEX girdisi çift sayıda hane içermeli")
        return ByteArray(digits.length / 2) { index ->
            val high = Character.digit(digits[index * 2], 16)
            val low = Character.digit(digits[index * 2 + 1], 16)
            if (high < 0 || low < 0) throw InvalidHexException("Geçersiz HEX karakteri")
            ((high shl 4) or low).toByte()
        }
    }

    fun toHex(bytes: ByteArray): String =
        bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }

    /** Yazdırılamayan baytları görünür kılar; satır sonlarını sembolle gösterir. */
    fun toDisplayText(bytes: ByteArray): String =
        bytes.toString(Charsets.UTF_8).map { char ->
            when {
                char == '\n' -> '␤'
                char == '\r' -> '␍'
                char.isISOControl() -> '·'
                else -> char
            }
        }.joinToString("")
}
