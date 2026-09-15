# RMET-5394 — On-device file logging (implementation plan)

## Context

We can't reliably reproduce/observe the main-process-freeze bug (RMET-5394) while
attached to `adb` — USB debugging suppresses Samsung's freezing behavior, and
wireless debugging has been unstable in practice. This plan adds a small,
debug-only logging capture to the library so a QA/dev session can be recorded
to disk with no live debugger attached, then shared afterwards (email/Drive/Slack)
via a normal share-sheet chooser.

This is a diagnostic tool for this branch (`test/RMET-5394/file-logging`), not a
customer-facing feature. It's meant to be built into a one-off test APK, used to
capture a repro, and shared/pulled after the fact.

## Design summary (decisions made)

- **Capture everything, not just this library's own logs.** Achieved by shelling
  out to the `logcat` binary (`ProcessBuilder`/`Runtime.exec`) rather than a custom
  log wrapper — `logcat` reads the OS-level `logd` ring buffer, which contains every
  `Log.*` call from any code running under this app's UID (this library, other
  Cordova plugins, host app code), not just calls routed through our own code.
  No special permission is needed to read our own app's UID logs.
- **One call site, both processes.** `Application.onCreate()` runs once per OS
  process — Android instantiates the `Application` object and calls `onCreate()`
  independently in the main process at launch **and again** in the isolated
  `:OSInAppBrowser` process the moment it's created for `OSIABWebViewActivity`,
  before that activity's own `onCreate()` runs. So a single unconditional call
  added to the *test app's* `Application.onCreate()` starts capture as early as
  possible in both processes, with no extra hook needed in the WebView activity.
- **Each process needs its own file set.** Since the same call runs in both
  processes, the target file name must be derived from the process name
  (e.g. suffix `main` vs `isolated`) — otherwise two independent `logcat`
  subprocesses would race to write/rotate the same file.
- **The isolated process's file is the reliable one.** Android's cached-apps-freezer
  freezes a process's whole cgroup, including any forked/exec'd children — so if
  the main process freezes, its own `logcat` child freezes with it and that file
  stops growing for the duration (expected — this is itself useful evidence).
  The isolated WebView process stays visible/unfrozen for the whole session, so
  its file (which also contains the main process's lines, since both share a UID
  and log to the same buffer) is the one to treat as the continuous record.
- **Rotation:** `logcat -v threadtime -r <kb> -n <count> -f <path>`. `-r` caps each
  file at ~10 MB (`-r 10240`); `-n` (default 4) must be set high (e.g. `9999`) since
  `logcat` overwrites the oldest file once the count is hit — we want manual
  deletion only, not auto-recycling.
- **Sharing:** reuse the existing `FileProvider` (`${applicationId}.fileprovider`).
  `file_paths.xml` already maps the entire cache dir (`<cache-path name="camera"
  path="." />`), so writing logs under a subdirectory of `context.cacheDir` needs
  **no manifest/xml changes**. Share via `Intent.ACTION_SEND` + chooser, with
  `FLAG_GRANT_READ_URI_PERMISSION` explicitly added to the intent — same pattern
  already used for the camera/video capture intents fixed in #57, since chooser
  targets otherwise can't read the `content://` URI.
- **Trigger:** no permanent UI. The share/delete calls will be invoked ad hoc by
  temporarily editing source and rebuilding, per the current debugging workflow.

## Proposed API

New file: `helpers/OSIABLogCaptureHelper.kt` (mirrors `OSIABPdfHelper.kt`'s
placement/style).

```kotlin
object OSIABLogCaptureHelper {
    fun start(context: Context)        // no-op if already started in this process
    fun stop()                         // destroys the logcat subprocess, if running
    fun shareLogs(context: Context)    // ACTION_SEND chooser with all log files for this device/session
    fun deleteLogs(context: Context)   // deletes all files under the log directory
}
```

- Log directory: `context.cacheDir/logs/`.
- File base name: `oslog-<processSuffix>.txt` where `processSuffix` is derived
  from `Application.getProcessName()` (guarded by `SDK_INT >= P`, same pattern as
  `isIsolatedProcess()` in `OSIABWebViewActivity`), e.g. `oslog-main.txt`,
  `oslog-isolated.txt` (+ `.1`, `.2`, ... rotated siblings).
- `start()` builds and runs:
  `logcat -v threadtime -r 10240 -n 9999 -f <cacheDir>/logs/oslog-<suffix>.txt`
- `shareLogs()` collects every file in `context.cacheDir/logs/`, builds an
  `ArrayList<Uri>` via `FileProvider.getUriForFile`, and sends
  `Intent.ACTION_SEND_MULTIPLE` with `FLAG_GRANT_READ_URI_PERMISSION`, wrapped in
  `Intent.createChooser(...)`. Needs `FLAG_ACTIVITY_NEW_TASK` if invoked from a
  non-Activity `Context` (e.g. called from `Application`).

## Implementation steps

1. Add `helpers/OSIABLogCaptureHelper.kt` with `start`/`stop`/`shareLogs`/`deleteLogs`
   as above.
2. Wire `OSIABLogCaptureHelper.start(this)` into the test app's `Application`
   subclass `onCreate()` (this lives in the consuming Cordova app, not this
   library — will need a custom `Application` class + `android:name` on
   `<application>` in that app's manifest if one doesn't already exist).
3. Add a temporary call to `OSIABLogCaptureHelper.shareLogs(this)` at whatever
   point in the library/app code is convenient for the current repro (e.g. a
   button, or directly in `onDestroy`/a specific callback) — edited in and out
   manually as needed, not a permanent UI element.
4. No `AndroidManifest.xml` or `file_paths.xml` changes expected (existing
   `FileProvider`/cache-path mapping already covers this).
5. Manual verification on a real Samsung device per the existing repro
   difficulties (see prior discussion) — confirm both files are produced, confirm
   rotation at 10 MB, confirm the isolated-process file keeps growing through a
   simulated freeze while the main-process file stalls, confirm `shareLogs()`
   chooser works and the recipient app can actually open the shared files.

## Open items / risks to resolve before or during implementation

1. **`logcat -f` restart behavior is unverified.** Unclear whether re-running the
   command (e.g. next app launch, log files from a previous session still present)
   appends, truncates, or errors on an existing target file. Needs an on-device
   check; if it doesn't behave as desired, `start()` may need to pick a
   timestamped file name per launch instead of a fixed base name.
2. **`Application.getProcessName()` requires API 28+.** minSdk for this library is
   26. On API 26/27 the process-suffix check can't run the same way
   `isIsolatedProcess()` already does — needs a decision: accept API 28+ only for
   this diagnostic tool (consistent with existing precedent), or add a fallback
   (e.g. reading `/proc/self/cmdline`).
3. **No automatic cap on total disk usage.** Per-file size is bounded (10 MB) and
   rotation count is intentionally large to avoid auto-deletion, so total usage
   grows unbounded across a long session until `deleteLogs()` is called manually.
   Acceptable for a manual debug workflow, but worth remembering.
4. **Orphaned `logcat` subprocess on process death.** Not yet confirmed whether the
   spawned `logcat` process reliably terminates when its parent app process is
   killed/removed from recents, or lingers as an orphan. Low priority (doesn't
   block the plan) but worth a quick check so stale processes don't accumulate
   during repeated test cycles.
5. **Consuming test app changes needed.** Since `Application.onCreate()` lives in
   the host Cordova app, not this library, we need write access to (or a local
   checkout of) the specific customer app being used for repro, including adding
   a custom `Application` class if one doesn't exist yet. Confirm that app/checkout
   is available before starting implementation.
6. **No test coverage planned.** This is a temporary diagnostic tool spawning a
   native subprocess and doing file I/O — hard to meaningfully unit test, and not
   intended to ship, so no automated tests are planned. Flagging explicitly so
   it's a conscious choice, not an oversight.
