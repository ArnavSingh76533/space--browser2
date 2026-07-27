# SPACE User Guide

## First launch

You land on the **start page**: a clock over the galaxy, the Cosmos search
field, your quick links, and the all-time trackers-blocked counter. Tap Cosmos
and type directly into it, or use the toolbar address field. SPACE accepts
either a search query or URL and figures out which you meant.

## The command bar

From left to right:

- **Shield** — shows how many trackers were blocked on this page. Tap it for
  the site panel: connection security, the block count, a per-site shields
  toggle, and Copy URL. Shield starts enabled and also stops known ad
  click-throughs and popups.
- **Address pill** — the lock/warning icon reflects HTTPS state. Tap to edit;
  suggestions blend your history with your engine's live suggestions (which
  you can disable). Private tabs never query the network for suggestions.
- **Tab counter** — opens the tab grid.
- **⋮ menu** — everything else.

While a page loads, a thin progress line runs under the bar; the reload button
becomes a stop button.

## Tabs

The grid shows live thumbnails. Switch between **Browsing** and **Private**
with the segmented control; each side shows its own tabs and counts. Close
with ✕, reopen the last closed tab from the toolbar's restore icon, and
long-press nothing — everything is one tap. The FAB opens a new tab of
whichever kind you're viewing. Closing the final tab now leaves a real
**No tabs open** screen; SPACE creates another tab only when you ask it to.

**Private tabs** (violet accents) write no history, no cache, and are never
saved into session restore. Closing the last private tab drops all session
cookies. See the README for the one honest caveat about WebView's shared
cookie jar.

Normal tabs are **restored after you exit** — pages come back lazily as you
select them, so relaunch stays instant.

## The menu

Quick row: forward · reload · bookmark · share · home. Below it:

- **New tab / New private tab**
- **Library** — bookmarks, searchable history (with per-item delete and
  clear-all), and downloads with live progress.
- **Find in page** — match counter and next/previous.
- **Desktop site** — per-tab toggle.
- **Add to start page** — pins the page as a quick link (long-press a quick
  link to remove it).
- **Print / Save as PDF** — via Android's print dialog.
- **Capture page screenshot** — grabs the visible page and opens the share
  sheet.
- **Download media** — inspect the open page with yt-dlp or choose a detected
  HLS, DASH, MP4, WebM, MKV, M4V, or MOV source, then save a selected
  video/audio format to `Downloads/SPACE`.
- **AI assistant** — see below.
- **Password generator** — length 8–40, digits/symbols toggles,
  cryptographically random, one-tap copy.
- **Settings**, **Exit SPACE** (exit runs your clear-on-exit choices first).

## The AI assistant

Settings → **AI assistant**: set an endpoint (any OpenAI-compatible base URL,
e.g. `https://api.openai.com/v1` or `http://192.168.1.20:11434/v1` for
Ollama), a model name, and an API key if the server needs one. The key lives
in Android's encrypted storage.

The small SPACE AI button is shown directly over the browser by default and
can be hidden in Settings. You can also open AI from the menu.

Choose an action-permission mode:

- **Ask for permission** — confirm every browser/media/web action.
- **Automatic** — run only SPACE's fixed allowlist of supported actions
  without another prompt.

Commands such as **“Play Titanium”** search YouTube, open the first relevant
video, and start it. **“Skip to 2:20”**, play, pause, next, previous, mute,
unmute, and volume commands control the current page's media where supported.

SPACE AI can also open pages, find visible text, click visible buttons/links,
fill ordinary non-sensitive fields, scroll, and perform short multi-step
sequences. The model never supplies raw executable JavaScript: actions use
fixed browser scripts, are limited to eight steps, and reject passwords,
payment details, OTPs, and similar sensitive fields.

For example, **“Open Wikipedia, search for Android WebView, and find the
History section”** opens the result and moves to the matching content.
**“Take me to the privacy policy”** follows a matching link or highlights and
scrolls to matching page content. Ordinary search forms can be filled and
submitted as a short sequence.

For **Summarize · Key points · Explain simply · Translate** (8 languages) and
free-form questions, page text goes only to your configured server when you
trigger the request.

## Settings highlights

Use the search field at the top — every setting is filterable.

- **Appearance** — theme (System / Light / Dark / AMOLED), 8 accent palettes,
  Material You dynamic color (Android 12+), galaxy animation toggle +
  intensity, dark mode for websites.
- **Privacy & shields** — the blocker with rule count, custom rules (one host
  per line blocks it and all subdomains), allowlist management, HTTPS
  upgrading, third-party cookie blocking, Safe Browsing, generic user agent,
  camera/mic/location ask-permission gates (off = silent deny), and
  clear-data-now.
- **Search & browsing** — engine (DuckDuckGo, Brave, Startpage, Google, Bing,
  Ecosia, or a custom `%s` template), suggestions toggle, JavaScript toggle,
  block-images data saver.
- **AI assistant** — quick-button toggle, Ask/Automatic action permissions,
  OpenAI-compatible endpoint, model, and encrypted API key.
- **Media** — background playback keeps already-playing media alive when SPACE
  leaves the foreground and exposes Android notification/headset controls.
- **Downloads** — default-on yt-dlp and direct-media downloader, Wi-Fi-only
  downloads, and download confirmation.
- **Data** — import/export cookies for the current site, clear
  history/cookies/cache on exit, and history retention (forever / 7 / 30 / 90
  days). Cookie exports can contain active sign-in tokens, so protect them.
- **Extensions & developer tools** — import Greasemonkey/Tampermonkey
  `.user.js` files, export a SPACE user-script bundle, enable/disable/remove
  scripts, and opt into remote WebView inspection. Imported scripts can read
  and modify matching pages.
- **Security** — biometric/screen-lock **app lock**; SPACE re-locks whenever
  it leaves the foreground.

## Little touches

- Back walks: find bar → page history → start page → previous tab.
- `http://` links try HTTPS first and fall back once, remembering the
  exception for the session.
- A bad HTTPS certificate is never silently trusted. SPACE shows a warning;
  choosing **Continue once (unsafe)** loads that connection only for the
  current attempt and leaves a red warning marker. No permanent exception is
  saved.
- Downloads land in your Downloads folder with a system notification, and the
  Library's Downloads tab tracks progress live.
- HTML videos can enter immersive fullscreen; Back exits fullscreen before it
  navigates the page.
- Android System WebView cannot load Chrome Web Store CRX/Manifest V3
  extensions. SPACE's local user scripts are the compatible extension option;
  full Chrome-extension support would require replacing WebView with a custom
  Chromium engine.
- SPACE registers as a browser, so "Open with" and web links can use it.
