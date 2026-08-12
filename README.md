# OCR Finance

OCR Finance is a local-first Android receipt application. Photograph or import a receipt, process it with a vision model running in LM Studio, review the extracted financial fields, and open a prepared transaction in [Cashew](https://cashewapp.web.app/).

The project is a working early MVP built with Kotlin and Jetpack Compose.

## Features

- Camera capture and Android photo-picker import
- Local receipt images and Room database records
- Durable, configurable OCR queue backed by WorkManager
- Optional home-Wi-Fi-only processing with automatic background resumption
- OpenAI-compatible LM Studio integration and connection testing
- Vision-model selection and improved receipt-specific OCR prompting
- Merchant, date, subtotal, tax, total, currency, and raw-text extraction
- Manual review, editing, retry, archive, restore, and deletion
- Active, Archived, and All receipt scopes
- Thumbnail and compact list layouts
- Search, sorting, and advanced receipt filters
- Configurable swipe actions with confirmation and five-second Undo
- Shared long-press and three-dot receipt action menu
- Configurable Cashew export fields
- Light, dark, and follow-device themes
- Activity and process-state restoration

## Requirements

- Android Studio with a Java 21-compatible runtime
- Android SDK 24 or newer device/emulator
- A computer or other device running [LM Studio](https://lmstudio.ai/)
- A loaded vision-capable model

LM Studio must expose its OpenAI-compatible API to the Android device. For a physical phone, enable LM Studio's local-network serving and use the host computer's LAN address, for example:

```text
http://192.168.1.100:1234
```

For the standard Android emulator, the host is normally available at:

```text
http://10.0.2.2:1234
```

## Getting Started

1. Clone the repository:

   ```bash
   git clone https://github.com/bangfireball/ocr_finance.git
   cd ocr_finance
   ```

2. Open the project in Android Studio and allow Gradle synchronization to finish.

3. Start LM Studio, load a vision-capable model, and enable the local API server.

4. Install and open OCR Finance on an Android device.

5. Open **Settings**, enter the LM Studio server URL, and tap **Test connection**.

6. Select an available model, save the settings, then photograph or import a receipt.

If **Process OCR only at home** is enabled, queued receipts wait for unmetered Wi-Fi and for the configured LM Studio server to become reachable.

## Build and Test

Run commands from the repository root:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Install the debug build on a connected device or emulator:

```bash
./gradlew installDebug
```

Run instrumentation and Compose UI tests:

```bash
./gradlew connectedDebugAndroidTest
```

## How Receipt Processing Works

```text
Capture or import
       ↓
Store image and local record
       ↓
Wait for queue slot and optional home network
       ↓
Prepare image and send it to LM Studio
       ↓
Review and edit extracted fields
       ↓
Keep locally or open a prepared Cashew transaction
```

OCR concurrency defaults to one job and can be set from 1–4 in Settings. Failed or weak results can be retried with a second-attempt prompt. Manual edits are protected from late OCR results.

## Cashew Export

The receipt total is always forwarded as the transaction amount. Settings → **Cashew Export** can independently include or omit:

- Merchant name as the title
- Transaction date
- OCR Finance receipt reference in notes
- Raw OCR text in notes

Opening Cashew records the handoff time locally, but OCR Finance cannot confirm that the transaction was ultimately saved in Cashew.

## Permissions and Privacy

Receipt records and images remain in the application's private local storage. OCR images are sent only to the LM Studio endpoint configured by the user.

The app requests network access. Home-network matching may also require nearby Wi-Fi and location permissions because Android treats Wi-Fi identifiers as location-sensitive data. No Wi-Fi password is stored.

API tokens are optional and currently stored in application preferences. Review local-network security and token storage before using the app in a production or untrusted environment.

## Technology

- Kotlin
- Jetpack Compose and Material 3
- Room
- WorkManager
- Coil
- Gradle version catalog

Application code is under `app/src/main/java/com/example/ocr_finace/`. JVM tests are under `app/src/test/`, and device tests are under `app/src/androidTest/`.

## Project Status

The end-to-end receipt workflow has been validated on a physical Android phone. Queue durability, home-network gating, receipt organization, reversible actions, search/filtering, themes, and configurable Cashew export are implemented.

The next major planned feature is interactive four-corner receipt adjustment with perspective correction before OCR. Release hardening, expanded automated UI coverage, accessibility review, and secure token-storage review remain.

See [DESIGN.md](DESIGN.md) for the detailed roadmap, architecture, decisions, and progress history.

## License

No license has been selected yet. Until one is added, the repository is not licensed for redistribution or reuse.
