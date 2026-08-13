# OCR Finance Design and Progress

## Purpose

OCR Finance is an Android application for turning receipt photographs into editable financial records. A user can photograph or import a receipt, send the image to a vision-capable model running through LM Studio, review and correct the extracted fields, retain the receipt locally, and open a prepared transaction in Cashew.

This document is the living source of truth for the application's intended behavior, architecture, current progress, known limitations, and next development steps. Update it whenever the product scope or implementation status changes.

## Product Goals

- Make receipt entry faster while keeping the user in control of the final data.
- Store receipt images and financial records locally on the Android device.
- Use a user-configured LM Studio vision model for receipt transcription and field extraction.
- Preserve the original extracted text alongside structured financial fields.
- Allow extracted values to be reviewed and corrected before use.
- Provide a convenient handoff to Cashew for transaction creation.

## Current Scope

The application currently supports:

- Photographing a receipt with an installed camera application.
- Importing a receipt through Android's photo picker.
- Storing receipt images in private application storage.
- Maintaining local receipt records in a Room database.
- Resizing, rotating, and compressing images before processing.
- Preserving and displaying the exact prepared JPEG sent to LM Studio alongside the original receipt.
- Opening either the original or sent-to-LM receipt image in a full-screen viewer from the edit screen.
- Sending an image to an OpenAI-compatible LM Studio chat-completions endpoint.
- Extracting:
  - Merchant name
  - Transaction date
  - Subtotal
  - Tax
  - Total
  - Currency
  - Raw receipt text
- Displaying pending, processing, complete, and failed states.
- Retrying failed processing.
- Manually editing and saving extracted fields.
- Listing previously stored receipts.
- Archiving/restoring, deleting, or opening receipts in Cashew with configurable left and right swipe actions and optional confirmation.
- Deleting a receipt and its stored image.
- Opening a prefilled transaction URL in Cashew.
- Configuring whether merchant/title, transaction date, receipt reference, and raw OCR text are forwarded to Cashew; amount remains required.
- Opening a Cashew confirmation dialog by long-pressing a receipt in the list.
- Configuring the LM Studio server URL, model identifier, and optional API token.
- Testing the LM Studio connection and selecting from the server's available models.
- Choosing a persisted appearance theme: follow the device, light, or dark.

## Out of Scope or Not Yet Defined

The following features are not currently represented in the application and require an explicit product decision before implementation:

- Receipt line-item extraction and editing.
- Transaction categories or category suggestions.
- Budgeting, charts, summaries, or financial reports.
- Cloud synchronization or multi-device access.
- User accounts.
- Direct Cashew API synchronization or confirmation that a transaction was saved.
- On-device OCR or an offline fallback when LM Studio is unavailable.
- Batch receipt import or processing.
- PDF receipt import.
- Duplicate receipt detection.
- Data export and backup.
- PDF generation. The planned four-corner perspective correction produces a corrected OCR input image; PDF output requires a separate product decision.

## Confirmed Planned Scope

The following additions were accepted after Phase 1 device testing:

- A durable OCR queue with one concurrent job by default and configurable concurrency.
- Optional processing restricted to a user-designated home Wi-Fi network.
- A second-attempt OCR action with a revised prompt.
- Active and archived receipt views.
- Five-second undo for confirmed archive and delete swipe actions.
- A receipt kebab menu sharing the same actions as long-press.
- Two-column Thumbnail, image-and-details Mixed, and compact List layouts.
- Receipt search, sorting, and filtering.
- Configurable Cashew export fields in a dedicated settings section.
- Interactive four-corner document adjustment and perspective correction before OCR.
- Vision-capability filtering for the LM Studio model selector.
- Configurable OCR image enhancement modes, with the current natural-color preparation remaining the default.

## User Workflow

```text
Configure LM Studio
        |
        v
Photograph or import receipt
        |
        v
Store image and create a local queued record
        |
        v
Wait for an available OCR slot and, if enabled, the home network
        |
        v
Optionally adjust four document corners and perspective-correct the image
        |
        v
Prepare the corrected image and send it to LM Studio
        |
        +---- failure ----> Show error and allow retry
        |
        +---- weak result -> Try OCR again with a second-attempt prompt
        |
        v
Store extracted text and financial fields
        |
        v
User reviews and corrects the receipt
        |
        +----> Keep receipt in local history
        |
        +----> Open prepared transaction in Cashew
```

## Application Architecture

The project is a single-module Android application using Kotlin and Jetpack Compose.

### User interface

`MainActivity.kt` currently contains three Compose screens:

- Receipt list
- Receipt details and editing
- LM Studio settings

`ReceiptViewModel` owns the UI-facing state and launches repository operations.

### Persistence

Room stores `ReceiptEntity` records in `receipts.db`. Receipt images are stored separately in private application storage under a directory associated with the receipt UUID.

Each record contains the image path, source, timestamps, extracted fields, processing status, processing error, archive state, and Cashew handoff timestamp.

### Processing

`ReceiptRepository` currently coordinates record creation and processing-state transitions. `ImagePreprocessor` applies EXIF rotation, optional four-point perspective correction, resizing to a maximum 2200-pixel dimension, JPEG quality 93 compression, and Base64 encoding. `LmStudioReceiptProcessor` sends the prepared image through `LmStudioApi` and maps the returned JSON into a `ProcessedReceipt`.

OCR execution uses a durable WorkManager-backed queue. It preserves queued work across process death and defaults to one serial OCR lane. Settings can distribute newly queued work across 1–4 serial lanes. Home-network restrictions and protection against late results overwriting newer manual edits remain planned.

LM Studio is expected to expose an OpenAI-compatible endpoint at:

```text
{configured server URL}/v1/chat/completions
```

### Cashew integration

`CashewLinkBuilder` creates a Cashew web URL containing the receipt total, merchant, date, and a reference note. Opening the URL records the handoff time locally, but does not confirm that Cashew saved the transaction.

## Data Lifecycle

Receipt processing uses these states:

| State | Meaning |
| --- | --- |
| `PENDING` | The local receipt record exists but processing has not started. |
| `PROCESSING` | The image is being sent to and analyzed by LM Studio. |
| `COMPLETE` | Processing returned and the extracted fields were stored. |
| `FAILED` | Processing failed; an error is stored and the user can retry. |

Deleting a receipt removes both its Room record and its private image directory.

The queue uses an explicit `QUEUED` state. Planned delete undo will delay permanent database and image removal for five seconds instead of deleting immediately.

## Progress Timeline

The checkout does not contain Git history. This timeline is reconstructed from the current architecture and file timestamps, and should be replaced or extended with dated milestones going forward.

| Phase | Status | Result |
| --- | --- | --- |
| Android project foundation | Complete | Kotlin and Jetpack Compose application created. |
| Local receipt model | Complete | Room entity, DAO, database, and repository implemented. |
| Image acquisition | Complete | Camera capture and photo import implemented. |
| Image preparation | Complete | Rotation, resizing, compression, and encoding implemented. |
| LM Studio processing | Complete | Settings, network request, prompt, and response parsing implemented. |
| Core UI workflow | Complete | Receipt list, detail editing, deletion, retry, and settings implemented. |
| Cashew handoff | Complete for current scope | Prefilled transaction and long-press handoff validated on the target phone. |
| Phase 1 device validation | Complete | Capture, import, editing, failure/retry, swipes, persistence, Cashew, and deletion were tested by the user. |
| Automated verification | Not started | Existing tests are Android template placeholders. |
| Reliability and queue hardening | In progress | Durable queuing, configurable concurrency, tracked retries, home-network gating, edit-revision protection, and interrupted-state recovery are implemented. Setup guidance, navigation restoration, parsing, and field normalization remain. |
| Receipt organization | In progress | Active/Archived/All scopes, archive restore, confirmation, and latest-action Undo are implemented; list modes, search, sort, and advanced filters remain. |
| Image correction | In progress | Manual four-point adjustment, perspective correction, and sent-image preview are implemented; automatic edge detection remains. |
| Release readiness | Not started | Device QA, accessibility, security review, and release configuration remain. |

### Current milestone

Phase 1 manual validation is complete. The application is a working early MVP whose primary workflow has been verified on the target phone. Phase 2 now focuses on durable queued processing, state correctness, receipt organization, configurable Cashew export, image correction, and vision-model discovery. The app is not yet release-ready because these changes and meaningful automated coverage remain outstanding.

## Known Limitations and Risks

### Testing

- There are no meaningful unit, integration, or Compose UI tests.
- The main workflow has been manually verified on a Samsung SM-S906U, but it lacks automated regression coverage.

### State and lifecycle

- Startup reconciliation recovers orphaned `PROCESSING` receipts, but forced worker-interruption behavior still needs device-level regression coverage.
- Processing is durable, but automated device tests for killing an active worker and observing recovery are still pending.
- Changing concurrency affects newly enqueued receipts; work already assigned to an existing serial lane is not redistributed.
- Home-network matching depends on Android exposing the current SSID and therefore requires Nearby Wi-Fi and precise location permission, with system location services enabled.

### Processing correctness

- A late model response can overwrite manual changes made while processing is still running.
- Response parsing assumes a clean JSON object or a simple Markdown JSON fence.
- Invalid or partial model output falls back to storing the response as raw text without structured values.
- Extracted dates, currencies, and monetary values are stored as unvalidated strings.
- There is no duplicate receipt detection.
- Retrying a completed but weak result with a deliberate second-attempt prompt is not supported.
- Image quality remains a major determinant of model accuracy; manual perspective correction is available, while automatic edge detection and optional enhancement modes remain planned.

### Configuration and connectivity

- Users can capture or import a receipt before configuring a model, causing processing to fail instead of guiding them through setup.
- The connection test lists all advertised models and does not yet filter for vision capability.
- A physical phone requires the computer's LAN address and an LM Studio server reachable on the local network.
- Cleartext local-network traffic is enabled for development use.
- The optional API token is stored in ordinary shared preferences.

### Cashew handoff

- A handoff is marked when Android opens the URL, not when Cashew confirms a saved transaction.
- Amount cleanup and date formatting are minimal.
- Users cannot yet choose which optional receipt fields are forwarded.

### User experience and maintainability

- Most user-facing text is hard-coded instead of stored in string resources.
- Navigation is implemented with a local enum rather than a navigation component.
- Most UI code is concentrated in `MainActivity.kt`.
- Accessibility semantics and error guidance are minimal.
- There are no dedicated date, currency, or monetary input controls.
- Archived receipts are available through Archived and All list scopes and can be restored with the configured archive swipe.
- Swipe actions execute without confirmation or undo.
- There is no kebab-menu alternative to the long-press action.
- Only the thumbnail receipt layout exists.
- Search, sorting, and filtering are not implemented.

## Next Steps

### Phase 1: Validate the baseline

- [x] Build the debug application.
- [x] Run unit tests and Android lint.
- [x] Configure a known vision-capable LM Studio model.
- [x] Process a photographed receipt on a physical device.
- [x] Process an imported receipt image.
- [x] Verify correction, deletion, failure, and retry behavior.
- [x] Validate the Cashew handoff on the target device.
- [x] Record initial results and defects in this document.

#### Baseline verification — 2026-08-12

- `assembleDebug`: Passed. Debug APK generated at `app/build/outputs/apk/debug/app-debug.apk`.
- `testDebugUnitTest`: Passed. One template test ran; this does not provide meaningful application coverage.
- `lintDebug`: Passed with 0 errors and 29 warnings.
- LM Studio availability: The server at `http://10.0.0.177:1234` is reachable from both the development environment and the phone. Its OpenAI-compatible `/v1/models` endpoint returns loaded models, and TCP port `1234` is reachable from the phone.
- Device validation: The debug APK installed and launched successfully on a Samsung SM-S906U. The application remained alive as the foreground activity with no recent Android runtime crash.
- Model configuration: The phone is configured to use `http://10.0.0.177:1234` with the loaded `google/gemma-3-4b` model.
- Application upgrade: Database migration 1→2 completed on the phone without losing the existing Home Depot receipt.
- Live settings validation: The in-app connection test reached LM Studio and populated 10 available models.
- Live interaction validation: Android back returned from Settings to the receipt list, and long-pressing the preserved receipt displayed the Cashew confirmation dialog.
- Build environment: The command-line build uses the JDK bundled with Android Studio at `/snap/android-studio/current/jbr`.
- Build blocker resolved: Coil 3.5.0 requires Kotlin standard library 2.4.0, while the project compiler was pinned to Kotlin 2.2.10. The Kotlin Compose plugin was aligned to 2.4.0.
- Notable lint findings include use of the platform `ExifInterface`, globally permitted cleartext traffic, dependency update notices, unused template resources, and minor KTX/style recommendations.

#### User acceptance results — 2026-08-12

- Camera capture works, including back-to-back captures, but immediate parallel processing must be replaced with a queue.
- Image import works; input image quality remains the largest contributor to weak model output.
- Editing and Save correctly return to the receipt list.
- LM Studio outages fail gracefully, and Retry succeeded consistently.
- Right-swipe archive and left-swipe delete work; archived receipts need a visible archive screen.
- Customized swipe directions persist.
- The long-press Cashew handoff works as intended.
- Detail-page deletion works as intended.
- New defects: stale `Unprocessed receipt` title after processing/saving and a minor title issue in the failure path.

#### Receipt title correction — 2026-08-12

- Save navigation now waits for the Room update to complete successfully before returning to the receipt list.
- A nonblank merchant is authoritative regardless of processing state.
- Empty merchants use state-specific titles: `Queued receipt`, `Processing receipt`, `Receipt processing failed`, or `Receipt`.
- JVM tests cover merchant priority and every processing-state fallback.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass.
- The updated APK was installed on the Samsung SM-S906U; the preserved receipt displays `The Home Depot`, and no startup crash was observed.

#### Durable OCR queue — 2026-08-12

- Camera captures, image imports, and manual retries now enqueue durable WorkManager jobs instead of calling LM Studio directly from the ViewModel.
- The default concurrency is one serial OCR lane. Settings allows 1–4 lanes, and newly queued receipts are distributed across the configured number.
- OCR work requires an active network connection, survives application process termination, and is tagged per receipt for cancellation on deletion.
- Receipt cards distinguish queued work from active processing.
- JVM tests cover the single-lane default, deterministic multi-lane distribution, and invalid concurrency fallback.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass.
- The updated APK was installed on the Samsung SM-S906U. Existing data was preserved, the concurrency selector displays the default value of 1, WorkManager components are registered, and no startup crash was observed.

#### Second-attempt OCR — 2026-08-12

- Completed and failed receipts expose `Try OCR again`; queued and actively processing receipts do not.
- Retrying uses a distinct prompt that requests a fresh independent reading, increased attention to faint text, branding/address separation, monetary label alignment, and consistency checks without suggesting an expected answer.
- The current extracted or manually edited values remain visible while the retry is queued and processing.
- Fields are replaced only after valid structured JSON is returned. Malformed model output becomes a visible failure and preserves existing values.
- Each attempt persists its attempt number, start time, and standard or second-attempt prompt type through Room migration 2→3.
- The detail screen shows attempt metadata and explains replacement behavior in a confirmation dialog.
- Prompt-selection JVM tests pass along with `assembleDebug`, `testDebugUnitTest`, and `lintDebug`.
- The APK upgraded successfully on the Samsung SM-S906U without losing existing receipts, the detail screen displays `Try OCR again`, and no startup crash was observed.

#### Home-network-only OCR — 2026-08-12

- Settings can identify the current Wi-Fi, save its normalized SSID as home, enable or disable home-only OCR, and forget the saved network.
- No Wi-Fi password, BSSID, or network credential is stored.
- When enabled, new WorkManager jobs require an unmetered network and verify that the configured LM Studio `/v1/models` endpoint is reachable before changing a receipt to `PROCESSING`. Unreachable servers leave receipts queued and trigger background retry.
- Android Nearby Wi-Fi and precise location permissions are requested only when the user selects the current network; the UI explains unavailable or denied access.
- API 24–36 Wi-Fi identification and SSID normalization are covered, including quoted and unavailable platform values.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass.
- The updated APK identified the current Wi-Fi as `Not Bill Gates` on the Samsung SM-S906U. The selection was not saved during automated verification, so home-only OCR was not enabled without user confirmation.
- Follow-up testing found that selecting or toggling the home network changed only the local Settings form until the separate bottom `Save settings` button was pressed. This allowed OCR to run on cellular and time out against the LAN-only LM Studio server. Home-network selection, enable/disable, and Forget now persist immediately; the screen states this explicitly.
- Background testing showed that Android can redact SSID access when the app is not foregrounded, requiring the user to reopen the app after reconnecting. Background authorization now relies on an unmetered-network constraint plus direct LM Studio reachability instead of SSID visibility. The saved SSID remains a user-facing label for the selected home network.
- Existing work created before this revision retains its original connected-network constraint, but the worker-level LM Studio reachability gate still prevents it from entering `PROCESSING`. Newly enqueued home-only work uses the unmetered constraint.
- User validation queued three receipts off Wi-Fi; reconnecting at home started processing automatically without reopening the app.

#### OCR edit-revision protection — 2026-08-12

- Each processing attempt uses its persisted start timestamp as the field revision it began from.
- A successful model result replaces extracted fields only when the receipt revision is unchanged.
- If the user saves edits while OCR is running, those values are preserved and only processing status/error state is finalized.
- JVM tests cover both unchanged-result application and changed-revision preservation.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass, and the updated APK installed and launched without a runtime crash.

#### Interrupted-processing recovery — 2026-08-12

- On application startup, receipts marked `PROCESSING` are reconciled with their receipt-tagged WorkManager jobs.
- Enqueued, blocked, or running jobs are left untouched so recovery cannot duplicate active work.
- Receipts with no unfinished owner are returned to the durable queue using their last standard or second-attempt prompt type.
- Worker cancellation now propagates as cancellation instead of being recorded as an OCR failure, allowing WorkManager to preserve retry semantics.
- JVM tests cover every active and terminal WorkManager state.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass; the updated APK installed and launched on the Samsung SM-S906U without a runtime crash.

#### Saved destination restoration — 2026-08-12

- Navigation is represented as one saveable destination containing List, Settings, or Detail plus its required receipt ID.
- Restoring Detail automatically reselects its receipt from Room.
- Invalid destination values, empty detail IDs, and missing/deleted receipts fall back to the list instead of displaying an indefinite spinner.
- JVM tests cover destination round trips and invalid-value fallback.
- `assembleDebug`, `testDebugUnitTest`, and `lintDebug` pass.
- A live Samsung SM-S906U configuration-change test kept the same receipt detail, Ready state, fields, and retry action before and after activity recreation. The temporary night-mode change was restored afterward.

#### Reversible swipe actions — 2026-08-12

- Completing a configured left or right swipe now opens an Archive or Delete confirmation dialog before changing data.
- After confirmation, the receipt is hidden locally and an Undo action remains available for five seconds.
- Archive and permanent deletion are committed only after the Undo window expires, so deletion does not need to reconstruct a database record or deleted image.
- Additional swipe actions remain available during the Undo window. Confirming a new action commits the previous one immediately, and only the most recently confirmed action can be undone.
- The debug build, JVM tests, and lint pass, and the build is installed on the Samsung SM-S906U for interaction testing.

#### Archived receipt management — 2026-08-12

- The receipt stream now includes active and archived records; the list applies a saveable Active, Archived, or All scope.
- Active remains the default scope, with scope-specific empty states.
- Opening an archived receipt retains detail editing, retry, deletion, and Cashew handoff behavior.
- On an archived card, the configured Archive gesture becomes Restore and uses the same confirmation and five-second Undo flow.
- JVM tests cover Active, Archived, and All scope filtering.

#### Shared receipt action menu — 2026-08-12

- Every receipt card has a three-dot button on its right edge.
- The three-dot button and long press open the same receipt action dialog.
- The shared dialog provides Open/Edit, Open in Cashew, Archive or Restore as appropriate, and Delete.
- Archive, Restore, and Delete route through the existing confirmation and latest-action five-second Undo flow.

#### Receipt layouts, search, and ordering — 2026-08-12

- The receipt list supports three persisted layouts: a two-column Thumbnail grid showing only receipt images and merchant titles, a Mixed layout retaining the original image-and-details cards, and a compact text-first List layout.
- Both layouts retain swipe, long-press, three-dot actions, scopes, confirmation, and Undo behavior.
- Search matches merchant, raw OCR text, transaction date, total, and currency without case sensitivity.
- Sorting supports newest date, oldest date, merchant name, total high-to-low, and total low-to-high.
- Status filtering supports Any, Ready, In progress (pending/queued/processing), and Failed.
- Advanced filters support Camera/Imported source, Cashew sent/not-sent status, currency, and an inclusive transaction-date range.
- The collapsed summary shows the live result count and active criteria; Clear filters restores the default Active/Any/Newest state.
- Receipt scope, layout, search, sorting, and processing-status controls collapse behind a summary button to preserve vertical list space.
- Android Back collapses an open filter panel before applying the list screen's normal back behavior.
- Camera and import are translucent icon controls overlaid at the bottom of the list, allowing receipt content to continue behind them.
- JVM coverage combines receipt scopes, search fields, status filtering, and numeric total sorting.

### Phase 2A: Durable OCR processing and state correctness

- [x] Diagnose and fix stale and incorrect receipt titles.
- [x] Add an explicit `QUEUED` processing state.
- [x] Move OCR execution to durable WorkManager jobs.
- [x] Default to one active OCR job.
- [x] Add a concurrent OCR jobs setting with a constrained range, initially 1–4.
- [x] Show queued status and cancel work when a receipt is deleted.
- [x] Add a second-attempt OCR action for completed, failed, or weak results.
- [x] Use a distinct retry prompt without suggesting a desired answer.
- [x] Track attempt count, time, prompt type, and latest error.
- [x] Preserve manual values until a valid retry result is available.
- [x] Add an optional home-network-only OCR setting.
- [x] Allow the current Wi-Fi network to be designated or forgotten as home.
- [x] Keep jobs queued off the home network and explain the waiting state.
- [x] Guide users through LM Studio setup before the first processing attempt.
- [x] Restore the selected receipt and screen consistently after process recreation.
- [x] Prevent late processing results from overwriting newer manual edits.
- [x] Recover or retry receipts left in `PROCESSING`.
- [x] Improve structured-response parsing and error reporting.
- [x] Validate and normalize dates, totals, taxes, and currency values.

#### Phase 2A completion — 2026-08-12

- Capture and import now require a configured LM Studio server URL and selected model; incomplete setup opens a clear explanation with a direct Settings action before any receipt record or OCR job is created.
- Receipt response parsing locates a recognizable JSON object inside Markdown fences or surrounding model commentary and reports missing structure or specifically invalid fields with actionable retry/edit guidance.
- OCR dates normalize to `YYYY-MM-DD`, monetary fields normalize to plain decimal strings, and currencies normalize to validated ISO 4217 codes before model results can replace stored fields.
- Invalid structured values fail the OCR attempt without overwriting existing manual or extracted values.
- Focused JVM tests cover fenced and wrapped JSON, escaped raw text, null fields, malformed responses, invalid-field reporting, common date formats, international amount separators, negative amounts, and currency aliases.

### Phase 2B: Receipt list and reversible actions

- [x] Add Active, Archived, and All receipt scopes.
- [x] Allow archived receipts to be restored, edited, deleted, retried, or forwarded.
- [x] Prompt before executing a swipe action.
- [x] Add a five-second Undo snackbar for archive and delete.
- [x] Implement delayed deletion so Undo leaves the record and image intact.
- [x] Add a kebab menu to every receipt.
- [x] Use one shared action sheet for kebab and long-press actions.
- [x] Support persisted two-column Thumbnail, Mixed, and compact List layouts.
- [x] Add search across merchant, OCR text, date, total, and currency.
- [x] Add date, merchant, and normalized-total sorting.
- [x] Sort date ordering by the user-editable date the receipt was added, independent of transaction date.
- [x] Add filters for scope, processing status, source, Cashew status, date, and currency.
- [x] Add Open in Cashew as a swipe action and allow swipe confirmation to be disabled while retaining Undo for archive and delete.

### Phase 2C: Cashew Export settings

- [x] Add a dedicated `Cashew Export` settings screen.
- [x] List each source field and Cashew destination.
- [x] Allow optional title, date, and notes fields to be toggled.
- [x] Keep required amount behavior explicit.
- [x] Omit disabled parameters rather than forwarding empty values.
- [x] Add a transaction-field preview.
- [x] Distinguish the generated receipt-reference note from optional raw OCR text.
- [x] Apply the same export configuration to detail, long-press, and kebab actions.

### Phase 2D: Four-point document correction

- [x] Add an image-adjustment screen before OCR with a later reopen option.
- [x] Add four independently draggable corner handles with at least 48 dp touch targets.
- [x] Connect the handles with a high-contrast quadrilateral outline and shade the exterior.
- [x] Constrain points to image bounds and reject self-intersecting/invalid shapes.
- [x] Persist normalized corner coordinates.
- [x] Map preview points back to original-image coordinates.
- [x] Apply perspective correction and generate a corrected rectangular image.
- [x] Preserve the original image and use the corrected image as the OCR input.
- [x] Add Reset, Rotate, Cancel, and Apply controls.
- [ ] Add optional automatic document-edge detection to initialize the handles.

PDF generation is not currently part of OCR Finance. Perspective correction targets the OCR input image. PDF output should be planned separately if required.

### Phase 2E: Vision-capable model discovery

- [ ] Exclude embedding models from selection.
- [ ] Use LM Studio capability metadata when available.
- [ ] Classify known vision/multimodal model families when metadata is absent.
- [ ] Optionally probe and cache model image-input capability.
- [ ] Show confirmed, likely, and untested capability states.
- [ ] Provide a `Show all models` fallback for unknown model families.
- [ ] Persist the exact model identifier and relevant processing configuration with every OCR attempt so historical results remain attributable after settings change.
- [ ] Track end-to-end processing duration and, when available, separate queue wait, image preparation, network/request, and model-generation durations.
- [ ] Capture LM Studio usage metadata when returned, including prompt tokens, completion tokens, total tokens, tokens per second, and time to first token when supported.
- [ ] Track success, timeout, connection failure, model/API failure, malformed response, and field-validation failure counts per model.
- [ ] Track structured-result completeness per attempt, including how many expected fields were populated and whether date, amount, and currency normalization succeeded.
- [ ] Track second-attempt frequency and whether a retry produced a valid replacement result.
- [ ] Measure user correction burden without retaining duplicate sensitive content: record which extracted fields changed, how many fields changed, and whether the raw OCR text was replaced after processing.
- [ ] Add an optional user rating for an OCR attempt, such as Good, Needs correction, or Unusable, because extracted values alone cannot establish ground-truth accuracy.
- [ ] Record comparison context that can affect performance, including image dimensions/encoded size, Natural or Enhanced mode, prompt type, device/network timeout, and concurrent OCR setting.
- [ ] Aggregate per-model metrics using sample count, median, p90, and recent-window values so outliers and model warm-up do not distort comparisons.
- [ ] Add a `Model performance` link in LM Studio Settings that opens a metrics screen with per-model speed, reliability, completeness, correction burden, and user-rating summaries.
- [ ] Allow model metrics to be filtered by date range, prompt type, image mode, and first versus second attempt.
- [ ] Show a clear insufficient-data state and avoid declaring a model “best” until it has a meaningful sample size across comparable receipts.
- [ ] Allow users to clear or export local model-performance history independently from receipt records.
- [ ] Keep performance history local by default and document exactly which receipt-derived metadata is retained.

### Phase 2F: Configurable image enhancement

- [ ] Add an OCR image mode setting with Natural, Enhanced, and Natural + Enhanced options.
- [ ] Keep Natural as the default and preserve the original receipt image in every mode.
- [ ] Implement conservative illumination/shadow correction, local contrast enhancement, gentle noise reduction, and mild sharpening.
- [ ] Avoid destructive thresholding that can erase decimal points, punctuation, or faint thermal-print characters.
- [ ] Allow Natural + Enhanced to send both image variants when supported by the selected vision model.
- [ ] Explain the request-size and model-memory tradeoff of sending two images.
- [ ] Add representative faint, shadowed, low-resolution, and already-clean receipt fixtures for comparison testing.
- [ ] Measure extraction accuracy before making any enhanced mode the default.

### Phase 2G: Queue visibility and processing notifications

#### Queue position

- [ ] Persist a queue-entry timestamp or monotonic sequence for every newly queued and retried OCR attempt so ordering does not depend on the editable receipt-added date.
- [ ] Derive a live global queue position from unfinished OCR attempts, displaying `Queue #N` on each `QUEUED` receipt in Mixed and List layouts and a compact numbered badge in Thumbnail layout.
- [ ] Define position `#1` as the next waiting receipt across all configured WorkManager lanes; show currently executing receipts as `Processing` rather than assigning them a waiting position.
- [ ] Recalculate visible positions when work starts, completes, retries, is cancelled, or concurrency changes, without rewriting every receipt record solely to update its displayed number.
- [ ] Explain that concurrency can cause more than one receipt to process at once and that displayed positions describe waiting order, not a guaranteed completion order.
- [ ] Reconcile queue positions against WorkManager during startup recovery so stale database state does not produce duplicate or missing positions.

#### Processing notifications

- [ ] Create a low-noise `Receipt processing` Android notification channel.
- [ ] Request Android 13+ notification permission contextually when the user enables processing notifications or first queues background OCR; continue processing normally if permission is denied.
- [ ] Add a persisted Settings toggle for processing notifications, enabled by default where permission is available.
- [ ] Post a completion notification after a receipt transitions successfully to `COMPLETE`, including the merchant or receipt fallback title and a concise success message without exposing totals or raw OCR text on the lock screen.
- [ ] Post a failure notification after a terminal OCR failure with a safe error summary and a retry-oriented message.
- [ ] Make each notification open the corresponding receipt detail screen and restore that destination correctly after process death.
- [ ] Suppress or quietly update notifications when OCR finishes while the matching receipt is already visible in the foreground, avoiding redundant alerts.
- [ ] Use stable per-receipt notification IDs so retries update an existing notification instead of creating duplicates; group multiple completed receipts into a summary when appropriate.
- [ ] Ensure worker cancellation or receipt deletion removes any related active notification.

#### Unprocessed visual treatment

- [ ] Give `PENDING` and `QUEUED` receipts a distinct theme-aware container or border treatment in Thumbnail, Mixed, and List layouts.
- [ ] Give `PROCESSING` and `FAILED` their own related but distinguishable treatments so waiting, active work, and errors cannot be confused.
- [ ] Keep `COMPLETE` receipts on the normal surface treatment and preserve readable contrast in light, dark, and Follow device themes.
- [ ] Pair every color treatment with text, iconography, or semantics; color alone must never communicate processing state.
- [ ] Add accessibility descriptions announcing the receipt title, processing state, and queue position where applicable.

#### Verification

- [ ] Add JVM tests for stable queue ordering, retries receiving a new queue position, cancellation, concurrent lanes, and startup reconciliation.
- [ ] Add worker/notification tests for completion, failure, permission denial, duplicate suppression, deep-link destinations, and deletion cleanup.
- [ ] Add Compose tests for queue badges and state treatments across Thumbnail, Mixed, and List layouts, including light/dark contrast and accessibility semantics.
- [ ] Validate notification delivery and deep links on the target Samsung device with the app foregrounded, backgrounded, and process-stopped.

### Phase 3: Add automated coverage

- [ ] Test LM Studio response parsing, including malformed and fenced responses.
- [ ] Test amount and date normalization.
- [ ] Test Cashew URL construction.
- [ ] Test repository processing-state transitions.
- [ ] Test processing failures and retry behavior.
- [ ] Test deletion of database records and stored images.
- [ ] Test detail-screen state restoration.
- [ ] Add Compose tests for capture/import, editing, and error states.
- [ ] Test queue ordering, persistence, cancellation, and concurrency limits.
- [ ] Test home-network gating and automatic resumption.
- [ ] Test retry prompt selection and attempt tracking.
- [x] Test manual-edit revision protection against late OCR results.
- [x] Test device-following and explicit light/dark theme resolution.
- [ ] Test archive restoration and delayed delete/undo.
- [ ] Test search, sort, and filter query combinations.
- [ ] Test all Cashew export-toggle combinations.
- [ ] Test crop coordinate mapping, corner ordering, and invalid quadrilaterals.
- [ ] Add Room migration tests from the current version 2 database.

### Phase 4: Improve product quality

- [x] Add a persisted Follow device, Light, and Dark appearance setting.
- [ ] Move user-facing text into Android string resources.
- [ ] Split `MainActivity.kt` into focused screen and component files.
- [ ] Introduce structured navigation and saved state handling.
- [ ] Improve loading, empty, setup, and error states.
- [ ] Add accessibility descriptions and semantics.
- [ ] Review secure token storage and local-network security behavior.
- [x] Move offline OCR, line items, categories, PDF output, and portable export into an explicit post-MVP stretch-goals backlog.

### Phase 5: Prepare a releasable MVP

- [ ] Complete emulator and physical-device regression testing.
- [ ] Add Room migrations before changing the persisted schema.
- [ ] Define backup, restore, and export behavior.
- [ ] Finalize branding and launcher assets.
- [ ] Configure release signing, optimization, versioning, and release notes.
- [ ] Capture screenshots or recordings for release review.

## Stretch Goals and Longshot Features

These ideas are intentionally outside the releasable MVP. They require separate product, privacy, storage-format, and maintenance decisions before implementation.

- [ ] Add portable local export and restore containing receipt records, original images, optional prepared OCR images, settings, and a versioned manifest in formats such as JSON and CSV.
- [ ] Support provider-neutral cloud backup and restore through Android's Storage Access Framework, allowing users to choose Google Drive, OneDrive, another document provider, or a local folder without giving OCR Finance direct cloud-account credentials.
- [ ] Evaluate optional direct Google Drive and Microsoft OneDrive synchronization only if scheduled background sync, conflict resolution, multi-device restore, and backup-status reporting justify provider-specific OAuth and API maintenance.
- [ ] Define encryption, retention, duplicate handling, partial-failure recovery, schema migration, and user-visible verification for all backup and restore workflows.
- [ ] Add shareable per-receipt export packages for sending an image and reviewed structured data through Android's share sheet.
- [ ] Evaluate offline or on-device OCR as a fallback when LM Studio is unavailable.
- [ ] Evaluate receipt line-item extraction, editing, categories, and category suggestions.
- [ ] Evaluate duplicate-receipt detection using image and extracted-field similarity.
- [ ] Evaluate PDF receipt import and optional corrected-PDF generation.
- [ ] Evaluate aggregate spending summaries, charts, and reports after category and normalization quality is sufficient.
- [ ] Evaluate direct financial-app synchronization beyond URL handoff only where stable, supported APIs exist.

## Definition of MVP Completion

The MVP is complete when a user can reliably configure LM Studio, capture or import a receipt, obtain accurate editable results, recover from processing failures, retain the receipt through application restarts, and hand the reviewed transaction to Cashew. The critical workflow must have automated coverage and successful real-device verification, with no known data-loss or state-restoration defects.

## Decision Log

Record meaningful product and architecture decisions here so future work does not depend on conversation history.

| Date | Decision | Reason |
| --- | --- | --- |
| 2026-08-12 | Treat the current application as a functional prototype and early MVP. | The end-to-end workflow exists, but testing and reliability hardening have not been completed. |
| 2026-08-12 | Use this document as the living application scope and progress record. | The checkout has no Git history or previous product-design document. |
| 2026-08-12 | Align the Kotlin Compose plugin to 2.4.0. | Coil 3.5.0 resolves Kotlin standard library 2.4.0, which is incompatible with the previously configured Kotlin 2.2.10 compiler. |
| 2026-08-12 | Make merchant extraction prioritize receipt branding over location text. | A Home Depot receipt was incorrectly identified as `Hartford`; the prompt now distinguishes logos and business names from cities, addresses, store numbers, and other header text. |
| 2026-08-12 | Archive receipts with a persisted `isArchived` flag and Room migration 1→2. | A right-swipe archive action should remove a receipt from the active list without deleting its record or image. |
| 2026-08-12 | Default list swipes to right/archive and left/delete, with both directions configurable in Settings. | These are the only swipe actions currently in scope, while persisted direction settings leave room for future actions. |
| 2026-08-12 | Discover LM Studio models through `/v1/models` after a successful connection test. | Model selection should use identifiers actually advertised by the configured server. |
| 2026-08-12 | Mark Phase 1 manual device validation complete. | The user verified capture, import, editing, graceful failure/retry, swipe persistence, Cashew handoff, and deletion on the target phone. |
| 2026-08-12 | Replace immediate OCR execution with a durable configurable queue. | Back-to-back captures currently process immediately; the default must be one OCR job at a time with an optional higher limit. |
| 2026-08-12 | Support optional home-network-only OCR. | Receipts should remain queued until the device reconnects to the designated Wi-Fi network. |
| 2026-08-12 | Require confirmation and five-second undo for swipe actions. | Archive and delete gestures need protection from accidental activation; delete must be delayed to remain recoverable. |
| 2026-08-12 | Name forwarding preferences `Cashew Export`. | The destination-specific name is clearer than the proposed `Data Forward` label. |
| 2026-08-12 | Apply four-point perspective correction to the OCR input image. | OCR Finance does not currently generate PDFs; PDF output remains a separate product decision. |
| 2026-08-12 | Filter model discovery by vision capability with a show-all fallback. | `/v1/models` may not expose complete capability metadata, so unknown but compatible models must remain discoverable. |
| 2026-08-12 | Track local per-model OCR performance and expose it from LM Studio Settings. | Duration alone favors fast models even when results are incomplete; reliability, structured completeness, retry frequency, correction burden, user ratings, and comparable processing context provide a more useful model comparison without pretending unverified OCR output is ground truth. |
| 2026-08-12 | Add explicit OCR queue positions, state-aware receipt styling, and optional completion/failure notifications. | Durable background processing should remain understandable when several receipts are waiting, while notification content must be useful without exposing sensitive financial details on the lock screen. |
| 2026-08-12 | Navigate back to the list only after a receipt save succeeds. | Waiting for the Room write removes the stale-list race; state-aware fallback titles avoid labeling completed or failed receipts as unprocessed. |
| 2026-08-12 | Implement OCR concurrency as durable serial WorkManager lanes. | One lane guarantees the default single-job behavior; 2–4 lanes allow bounded parallel requests while preserving ordering within each lane. |
| 2026-08-12 | Treat retry as a tracked second attempt and reject malformed structured output. | A weak OCR result needs a genuinely fresh reading, while invalid responses must not erase the user's current fields. |
| 2026-08-12 | Match home-network-only OCR by normalized Wi-Fi SSID. | SSID is understandable to the user and avoids storing Wi-Fi credentials, while Android permissions explicitly disclose its location-sensitive nature. |
| 2026-08-12 | Persist home-network controls immediately. | A distant general Save button made an apparently enabled safety restriction remain unsaved, allowing queued OCR to run over cellular and time out. |
| 2026-08-12 | Gate background home-only OCR by unmetered connectivity and LM Studio reachability. | Android may redact SSID in a background worker; testing the required service directly allows queued OCR to resume without reopening the app. |
| 2026-08-12 | Default the appearance theme to Follow device and persist explicit Light or Dark overrides immediately. | The application should respect system appearance by default while giving users a predictable manual override. |
| 2026-08-12 | Delay swipe archive and delete commits until a five-second Undo window expires. | Deferring the mutation makes both actions genuinely reversible without recreating a deleted image or database record. |
| 2026-08-12 | Expose Active, Archived, and All receipt scopes and map Archive to Restore for archived cards. | Archiving must be reversible and must not make receipts inaccessible. |
| 2026-08-12 | Use one context-aware receipt action dialog for both the kebab button and long press. | Both entry points should expose the same behavior and reuse existing confirmation and Undo safety. |
| 2026-08-12 | Persist only the chosen receipt layout while keeping search, sort, status, and scope as saveable screen state. | Layout is a durable user preference; transient list exploration should survive recreation without permanently changing future sessions. |
| 2026-08-12 | Search and sort the observed local receipt collection in the UI layer for the MVP. | The list already observes all local records for Active/Archived/All scopes, so this avoids schema changes while remaining responsive for the expected on-device dataset. |
| 2026-08-12 | Use the editable receipt-added date for Newest and Oldest sorting. | Import chronology is distinct from the transaction date extracted from the receipt, and users may need to correct the date assigned on import. |
| 2026-08-12 | Make Cashew amount required and all other forwarded fields independently optional. | This preserves a valid transaction while giving users control over title, date, generated reference notes, and potentially sensitive raw OCR text. |
| 2026-08-12 | Defer configurable image enhancement and keep high-quality natural color as the default. | Conservative enhancement may improve faint receipts, but it requires accuracy comparisons to ensure it does not erase or alter important characters. |
| 2026-08-12 | Apply OCR results only when the receipt revision is unchanged. | A model response arriving after a manual save must finalize processing without overwriting the user's newer values. |
| 2026-08-12 | Reconcile `PROCESSING` receipts with WorkManager at startup. | Durable work should remain authoritative when active, while orphaned database state must be safely returned to the queue without duplicate OCR requests. |
| 2026-08-12 | Save navigation as a destination containing its required receipt ID. | Detail state and selection must restore atomically; a detail screen without a valid receipt must safely return to the list. |

## Update Guidelines

When completing work:

1. Update the relevant checklist and progress-table status.
2. Add newly discovered risks or remove limitations that are demonstrably resolved.
3. Record significant scope or architecture choices in the decision log.
4. Add verification evidence for completed milestones.
5. Keep planned features separate from implemented behavior.
