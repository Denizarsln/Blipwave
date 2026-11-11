package dev.blipwave.data

enum class MessageDirection { TX, RX }

/** Sohbet ekranındaki tek mesaj. İçerik ham bayt olarak saklanır, gösterim moduna göre çözülür. */
class ChatMessage(
    val id: Long,
    val direction: MessageDirection,
    val payload: ByteArray,
    val timestampMillis: Long,
)

data class ByteStats(val sentBytes: Long = 0, val receivedBytes: Long = 0)

data class RssiSample(val timestampMillis: Long, val rssi: Int)

/** Canlı grafik için kayan zaman penceresi. */
object RssiHistory {
    const val WINDOW_MILLIS = 60_000L

    fun append(
        history: List<RssiSample>,
        sample: RssiSample,
        windowMillis: Long = WINDOW_MILLIS,
    ): List<RssiSample> {
        val cutoff = sample.timestampMillis - windowMillis
        return history.filter { it.timestampMillis >= cutoff } + sample
    }
}
