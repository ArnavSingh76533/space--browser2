# 🌌 SPACE — a galaxy-grade private browser for Android

SPACE is a modern Kotlin/Jetpack Compose browser built on Android System
WebView. It combines tabbed and private browsing, a request-level privacy
shield, local media downloading, background playback, and an
allowlisted AI browser assistant without adding telemetry or accounts.

## What changed in 0.3

- Reliable yt-dlp downloads using current yt-dlp, FFmpeg, and Aria2c
  components, page cookies/headers, retries, fallback, and atomic export to
  `Downloads/SPACE`.
- Working HTML video fullscreen and opt-out background playback with Android
  media controls.
- A real zero-tab state after closing the final tab.
- SPACE AI Ask/Automatic permission modes, a default-on quick-access button,
  direct YouTube search-and-play, seek/play/pause/volume/next controls, and
  safe multi-step webpage interaction.
- A focused Cosmos input that accepts its own text.
- A substantially stronger Shield engine supporting hosts and
  ABP/uBlock-style rules, exceptions, important/resource/party/domain
  modifiers, cosmetic rules, popup and ad-redirect blocking, and per-site
  allowlists. Shield is on by default.
- Recoverable certificate errors now show an explicit warning. The user can
  stop or continue once; continued pages remain visibly marked red and SPACE
  never stores a permanent certificate exception.
- Direct HLS, DASH, MP4, WebM, and other media sources are detected alongside
  yt-dlp inspection. The downloader is enabled by default and can be disabled.
- Current-site cookie import/export, opt-in remote WebView debugging, and
  import/export for local Greasemonkey-style user scripts.

## Building

Requirements: JDK 17 and Android SDK 34. Minimum device: Android 9 (API 28).

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Open the repository root directly in Android Studio. The debug APK is written
to `app/build/outputs/apk/debug/app-debug.apk`.

The original `space-browser-v0.2.zip` is retained for reference; the current,
maintainable source is checked in at the repository root.

## Privacy and permissions

- AI page actions are a fixed allowlist. Ask mode confirms each action;
  Automatic mode runs only those supported actions. Models cannot inject raw
  JavaScript, and SPACE refuses to fill passwords, payment details, OTPs, or
  similar sensitive fields.
- Background playback keeps only the active, already-playing WebView alive and
  uses Android's foreground media-session model.
- The media downloader is optional, does not bypass DRM, and should only be
  used for media you have the right to save.
- Android WebView has no Chrome CRX/Manifest V3 extension API. SPACE supports
  local user scripts with URL match rules instead of pretending incompatible
  Chrome extensions can run.
- Importing cookies or scripts can expose signed-in sessions or page data.
  SPACE requires a native user action, scopes imported cookies to the current
  site, and stores scripts in app-private storage.
- Private tabs are excluded from history and session persistence. Android
  WebView still has a process-wide cookie jar, so private tabs are not separate
  browser containers.

## Architecture

The app is a single Android module with an explicit `AppContainer`:

```text
app/src/main/java/com/spacebrowser/
├── MainActivity.kt / SpaceApp.kt
├── core/
│   ├── adblock/   rule compiler and request/cosmetic filtering
│   ├── browser/   tabs, WebView clients, media/web automation
│   ├── media/     yt-dlp and background playback service
│   ├── settings/  DataStore-backed settings
│   └── db/, net/, security/, util/
└── ui/            Compose browser, home, tabs, AI, library, settings
```

See [the user guide](docs/USER_GUIDE.md) and [changelog](CHANGELOG.md).

## References

The downloader reliability work follows patterns from the
[Seal project](https://github.com/ArnavSingh76533/Seal). Shield's filter
model is adapted to this WebView architecture from
[adblock-rust](https://github.com/ArnavSingh76533/adblock-rust).
See [third-party notices](THIRD_PARTY_NOTICES.md) for attribution and license
details.

## License

MIT — see [LICENSE](LICENSE).
