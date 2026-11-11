# Media

Images referenced by the main README. All of them were captured in demo mode on an Android 14
handheld (720×1280, Turkish system language).

## Main gallery (`docs/media/`)

- `demo.gif` — scan start: radar, pulse rings and cards sliding in (300 px wide)
- `scan.png` — scanning with all demo devices
- `detail.png` — connected BLE device with the live RSSI chart and GATT services
- `console.png` — UART console in HEX mode
- `connections.png` — open sessions in different states
- `settings.png` — settings in the dark theme
- `light.png` — scan screen in the light theme

## Classic Bluetooth & extras (`docs/media/pda/`)

- `classic-filter.png` — filter panel with *Classic* selected
- `spp-console.png` — serial console connected over SPP
- `permission.png` — Bluetooth permission explainer
- `settings-light.png` — settings in the light theme

Capture a screenshot with:

```bash
adb exec-out screencap -p > docs/media/scan.png
```

Real scans show the names and MAC addresses of nearby devices. Use demo mode for public screenshots,
or blur those details before committing.
