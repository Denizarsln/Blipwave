package dev.blipwave.data;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Kullanıcı girdisi ↔ bayt dizisi dönüşümleri (Text / HEX modu). */
public final class PayloadCodec {

    public enum Format { TEXT, HEX }

    public static final class InvalidHexException extends IllegalArgumentException {
        InvalidHexException(String message) {
            super(message);
        }
    }

    private PayloadCodec() { }

    /** @throws InvalidHexException HEX modunda girdi geçersizse */
    public static byte[] encode(String input, Format format) {
        if (format == Format.HEX) return parseHex(input);
        return input.getBytes(StandardCharsets.UTF_8);
    }

    public static String decode(byte[] bytes, Format format) {
        return format == Format.HEX ? toHex(bytes) : toDisplayText(bytes);
    }

    /** "48 65 6C", "48:65:6c", "0x48,0x65" ve "48656C" biçimlerini kabul eder. */
    public static byte[] parseHex(String input) {
        String withoutPrefixes = input.replaceAll("(?i)0x", "");
        StringBuilder digits = new StringBuilder();
        for (char c : withoutPrefixes.toCharArray()) {
            if (Character.isWhitespace(c) || c == ':' || c == ',' || c == '-') continue;
            digits.append(c);
        }
        if (digits.length() == 0) throw new InvalidHexException("Boş HEX girdisi");
        if (digits.length() % 2 != 0) throw new InvalidHexException("HEX girdisi çift sayıda hane içermeli");

        byte[] result = new byte[digits.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(digits.charAt(i * 2), 16);
            int low = Character.digit(digits.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) throw new InvalidHexException("Geçersiz HEX karakteri");
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) builder.append(' ');
            builder.append(String.format(Locale.US, "%02X", bytes[i] & 0xFF));
        }
        return builder.toString();
    }

    /** Yazdırılamayan baytları görünür kılar; satır sonlarını sembolle gösterir. */
    public static String toDisplayText(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        StringBuilder builder = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') builder.append('␤');
            else if (c == '\r') builder.append('␍');
            else if (Character.isISOControl(c)) builder.append('·');
            else builder.append(c);
        }
        return builder.toString();
    }
}
