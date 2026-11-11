# Media

Images referenced by the main README.

## Demo mode (`docs/media/`)

- `demo.gif`
- `scan.png`, `detail.png`, `console.png`
- `connections.png`, `settings.png`, `light.png`

## Real hardware (`docs/media/pda/`)

Captured on an Android 14 handheld (720×1280).

- `scan.png` — live BLE + classic scan
- `classic-filter.png` — filter panel with *Classic* selected
- `spp-console.png` — serial console connected over SPP
- `device-detail.png` — device detail screen
- `permission.png` — Bluetooth permission explainer
- `settings-light.png` — settings in the light theme

Capture with:

```bash
adb exec-out screencap -p > docs/media/pda/scan.png
```

Real scans show the names and MAC addresses of nearby devices. Use demo mode for public screenshots,
or blur those details before committing.
