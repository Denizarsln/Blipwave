# ESP32 UART echo firmware

A minimal Arduino sketch that exposes the **Nordic UART Service** so you can try
Blipwave's UART console with real hardware.

| Message from phone | Reply             |
|--------------------|-------------------|
| `PING` (any case)  | `PONG`            |
| anything else      | echoed back as-is |

## Flashing

1. Install the [Arduino IDE](https://www.arduino.cc/en/software) and the
   **esp32 by Espressif Systems** board package (Boards Manager, version 3.x).
2. Open `esp32_uart.ino`, select your board (e.g. *ESP32 Dev Module*) and port.
3. Upload, then open the Serial Monitor at **115200 baud** to see RX logs.

The device advertises as **`Blipwave-ESP32`**. In the app: scan → tap the
device → **Connect** → **UART console** → send `PING`.

## UUIDs

| Role | UUID | Properties |
|------|------|------------|
| Service | `6E400001-B5A3-F393-E0A9-E50E24DCCA9E` | |
| RX (phone → ESP32) | `6E400002-B5A3-F393-E0A9-E50E24DCCA9E` | Write, Write without response |
| TX (ESP32 → phone) | `6E400003-B5A3-F393-E0A9-E50E24DCCA9E` | Notify |

Replies are sent in 20-byte notifications so they work at the default MTU.
