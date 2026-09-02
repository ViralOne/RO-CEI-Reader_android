# RO CEI Reader (Android)

![CI](https://github.com/ViralOne/RO-CEI-Reader_android/actions/workflows/ci.yml/badge.svg)
![Release](https://github.com/ViralOne/RO-CEI-Reader_android/actions/workflows/release.yml/badge.svg)

An open-source Android app that reads the Romanian electronic ID card
(*Cartea Electronică de Identitate*, CEI) over NFC and exports the holder's
data as a PDF.

Independent, clean-room implementation of the ICAO 9303 + national eID protocol.
Not affiliated with the Romanian Ministry of Internal Affairs (MAI) or its
official *RO CEI Reader* app.

## Features

- **Offline NFC read** of the Romanian CEI card — no server, no network.
- Reads photo (DG2) and every national data file: name, CNP, sex, citizenship,
  date and place of birth, document serial, issuing authority, issue/expiry
  dates, domicile, plus temporary/foreign address periods when present.
- Modern Jetpack Compose UI in Romanian: entry → *"Țineți cardul nemișcat"*
  reading screen → results → typed errors.
- Live NFC-state awareness: warns when NFC is off, opens NFC settings, and
  re-arms reader mode automatically when NFC is turned back on.
- **PDF export** matching the official app's layout (share sheet, no permanent
  storage). Multi-page when addresses are long.
- Diacritics render correctly (UTF-8 end to end).

## Privacy

- Manifest declares **only `android.permission.NFC`**. No `INTERNET`.
- Card data lives only in memory. Nothing is written to disk unless *you* tap
  **Exportă PDF**, and PDFs are written to app-private cache (evicted on the
  next export). No logs contain personal data.

## Requirements

- Android **8.0 (API 26)+** with NFC hardware.
- Your CEI card and its **CAN** (the 6-digit number printed on the card) plus
  your **PIN**.

## Using the app

1. Enter CAN and PIN.
2. Rest the card flat on the back of the phone (near the NFC antenna) and hold
   it still.
3. The app reads DG2 + the national applet in one session and shows all data
   plus your photo. Tap **Exportă PDF** to share.

If NFC is off, the entry screen shows an **Activează NFC** button that opens
the system NFC settings; when NFC comes back on the app rearms itself with no
restart.

## Build

The project builds inside a Docker Android image (JDK 17 + Android SDK 35 +
Gradle 8.7), so it works on hosts with no local SDK.

```sh
./scripts/build.sh                          # assembleDebug in Docker
./scripts/gradle.sh :app:testDebugUnitTest  # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Local host needs Docker and `adb`. On Apple Silicon the image is pinned to
`linux/amd64` (baked into `scripts/gradle.sh`).

## How it works

- **Contactless PACE** with the CAN over NFC opens a secure channel and lets
  the app read the ICAO **DG2** (face image).
- A plaintext `SELECT` of the national applet (AID
  `A000000077030C60000000FE00000500`) then a **second PACE** with the same
  CAN establishes a fresh secure channel for the national data.
- `VERIFY PIN`, then `SELECT` the eID DF (AID `E828BD080FA0000001674544415441`)
  and read each EF: `0101` personal, `0102` birth, `0104` issuer, `0106`
  address, `0107` temporary addresses, `0108` foreign addresses.
- Each EF is an ASN.1 `SEQUENCE` of context-tagged UTF-8 strings, decoded
  in-app.

Libraries used for the card side: [JMRTD](https://jmrtd.org),
[SCUBA](https://scuba.sourceforge.net), and BouncyCastle. Everything Romanian-
specific (national applet flow, EF map, ASN.1 layout, PDF layout) is our own
code.

## Project layout

- `app/src/main/java/dev/ceireader/app/`
  - `card/` — PACE session, national applet, APDU/SW helpers, ASN.1 decoder,
    orchestrator.
  - `nfc/` — reader-mode controller.
  - `model/` — domain types (`CeiData`, `ReadState`, validation).
  - `pdf/` — PDF export.
  - `ui/` — Compose screens, theme, ViewModel.
- `app/src/test/java/dev/ceireader/app/` — JVM unit tests (APDU, SW, decoder,
  validation, error mapping).

## CI

- **CI** (`.github/workflows/ci.yml`) — runs on every PR and push to `main`:
  unit tests (`:app:testDebugUnitTest`, report uploaded as an artifact), then
  `:app:assembleDebug` (debug APK uploaded as an artifact).
- **Release** (`.github/workflows/release.yml`) — triggered by pushing a
  `vX.Y.Z` tag on `main`. Builds a debug-signed APK and AAB and publishes them
  on a GitHub Release.

## Contributing

Issues and PRs welcome. Keep changes clean-room; do not import code from the
official MAI application.

## License

MIT — see [LICENSE](LICENSE).

## Acknowledgements

- The Romanian MAI for shipping a card that follows open standards.
- The JMRTD project for a solid eMRTD/PACE implementation.
- [victorholo/romanian-cei-reader-web](https://github.com/victorholo/romanian-cei-reader-web)
  — independent contact-reader project that confirmed the applet AIDs, EFs,
  and PIN VERIFY format used by the card.
- [Poppins](https://github.com/google/fonts/tree/main/ofl/poppins) by the
  Poppins Project Authors — bundled under the SIL Open Font License 1.1 (see
  `app/src/main/font-licenses/Poppins-OFL.txt`) and used for headings only.
