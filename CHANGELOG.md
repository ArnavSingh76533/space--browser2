# Changelog

## 0.3.0 — media, intelligence, and shields

- Rebuilt the optional yt-dlp downloader around an application-scoped job,
  current yt-dlp/FFmpeg/Aria2c components, authenticated page context,
  retries, safe output discovery, downloader fallback, and atomic MediaStore
  export. Closing the download sheet no longer cancels the job.
- Added working HTML video fullscreen with immersive system bars, back-button
  exit, WebView callbacks, and keep-screen-on behavior.
- Added opt-out background media playback. The active playing WebView remains
  alive when SPACE leaves the foreground, with a foreground media session and
  notification/headset play, pause, previous, next, and seek support.
- Closing the final tab now leaves a genuine zero-tab state with an explicit
  New tab action; no replacement "New Tab" is silently created.
- SPACE AI now has Ask and Automatic action-permission modes, a default-on
  quick-access button, deterministic YouTube search-and-play, media controls,
  and up to eight safe web steps for opening pages, finding/clicking visible
  content, filling non-sensitive fields, scrolling, and waiting.
- The Cosmos field now owns its own text and focus, so tapping it never sends
  typing to the toolbar address input.
- Shield now uses a compiled rule engine inspired by adblock-rust: hosts and
  ABP/uBlock-style network rules, exceptions, important rules, resource and
  party constraints, domain restrictions, per-site allowlists, plus cosmetic
  CSS rules and exceptions. The bundled slim list contains more than 4,600
  rules.
- Added focused tests for SPACE AI action parsing and the new ad-block engine,
  and restored the missing Gradle wrapper so clones build directly.

## 0.2.0 — course correction

- Start page now shows your 5 most-visited sites with their real favicons
  (long-press to hide one); seeded shortcuts removed via DB migration.
- Google is the default search engine and shields default to off on fresh
  installs — existing users keep their choices.
- Shield is tappable before navigation: outline = off, filled = on, with a
  global toggle dialog on the home screen and the site panel on pages.
- Desktop mode can be chosen before a page loads, plus a
  "Desktop sites by default" setting; toggling reloads once, never loops.
- Fixed the old page flashing when navigating from the start page
  (opaque cover + first-paint gate) and "Search the Cosmos" now focuses the
  address bar and opens the keyboard reliably.
- Tab-closing fixes: closing the last tab lands on a clean start page and
  "close all" no longer resurrects a ghost tab.
- Toolbar polish: stop/reload lives inside the address pill, clearer
  placeholder, larger touch targets.
- Themes: Auto (Daylight 7 AM–6 PM, Dim at night), Daylight, Dim, and
  Dark (AMOLED), plus Follow system.
- Downloads: Wi-Fi-only option, ask-before-download confirmation, and the
  Library tab now shows percent, live speed, time remaining, and
  cancel / retry / delete / copy-URL actions.
- Optional media downloader built on yt-dlp (off by default, enable in
  Settings → Downloads): pick best / 720p / audio-only, live progress,
  files land in Downloads/SPACE. No DRM circumvention; you're responsible
  for downloading only what you're allowed to.
- SPACE AI: defaults to api.openai.com with gpt-4o-2024-08-06 (bring your
  own key), and can run allowlisted browser commands — open a URL, search,
  switch theme, toggle desktop or shield, open downloads, close private
  tabs — each behind an explicit confirmation dialog.

## 0.1.0 — first flight

- Tabbed browsing on the system WebView with live-thumbnail grid switcher,
  private tabs, reopen-closed, and lazy session restore.
- Shields: bundled host blocklist + custom rules + per-site allowlist, with
  per-page and all-time counters.
- HTTPS upgrading with one-shot fallback; third-party cookie blocking; generic
  user agent; silent-deny permission gates; Safe Browsing toggle.
- Galaxy UI: animated starfield/nebula/aurora background, 8 accent palettes,
  System/Light/Dark/AMOLED themes, Material You, glass chrome.
- Start page with clock, quick links, and privacy stats.
- Library: bookmarks, searchable history with retention, downloads tracking.
- Find in page, desktop mode, share, print/save-PDF, page screenshots,
  password generator, biometric app lock, clear-on-exit.
- Bring-your-own-endpoint AI assistant (summarize / key points / explain /
  translate / ask) with encrypted key storage.
- Unit tests for URL heuristics and the blocklist matcher.
