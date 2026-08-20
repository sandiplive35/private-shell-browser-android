# PrivateShell Browser (Android)

A deliberately isolated, privacy-first Android browser shell. It is designed to minimize app-to-device integration and persistent browser state while keeping all normal HTTP(S) navigation inside the app.

## Threat model

PrivateShell is intended to reduce these classes of leakage:

- application access to contacts, location, camera, microphone, phone state, media/storage, files, and installed apps;
- accidental hand-off to `intent://`, `tel:`, `mailto:`, app-store, payment-app, social-app, or other custom URI handlers;
- third-party cookies and common analytics/pixel endpoints;
- persistent WebView cookies/storage/history/cache/service workers after the user wipes the session;
- invalid-TLS bypasses, cleartext HTTP, mixed content, client-certificate prompts, file pickers, downloads, WebView debugging, screenshots, and Autofill integration.

## What it does **not** promise

No ordinary phone browser can honestly guarantee that a remote website learns "nothing" about the user. A site can still observe the public IP address, request timing, TLS/network behavior, WebView/browser characteristics, screen/layout characteristics exposed by the web platform, and any data the user types into the site. A VPN/Tor-style network layer would be required to hide the public IP from destination sites, and even that does not eliminate all fingerprinting.

This repository therefore makes a narrower, auditable claim: **minimal Android permissions + minimal device integration + strict in-app navigation + ephemeral browser state controls**.

## Default behavior

`STRICT ON` is the default:

- JavaScript OFF
- all cookies OFF
- DOM storage OFF
- third-party cookies OFF
- common tracker hosts blocked
- no file/content URI access
- no geolocation
- no camera/mic WebView permissions
- no file picker
- no downloads
- no external custom-scheme launches
- HTTPS only / mixed content blocked
- invalid TLS fails closed
- WebView debugging OFF
- per-launch isolated WebView profile when supported
- Attribution Reporting disabled
- WebAuthn/passkey integration disabled
- WebView Media Integrity disabled when supported
- screen capture OFF
- Autofill OFF

Tap `STRICT ON` to enter `COMPAT` mode when a modern site requires JavaScript or first-party cookies. Tracker blocking and the Android isolation controls stay enabled.

## Navigation containment

Only `http`, `https`, and local `about:` navigation are accepted by the WebView client. Cleartext `http://` text entered in the address bar is upgraded to HTTPS. Custom schemes are canceled rather than passed to Android's Activity Manager.

Links with `target=_blank` stay in the same WebView because multiple-window support is disabled.

## Permissions

The manifest requests exactly one Android permission:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

There are intentionally no permissions for location, camera, microphone, contacts, files/media, Bluetooth, notifications, phone state, SMS, accounts, or package visibility.

## Build

Requirements:

- JDK 17
- Gradle 9.5.0
- Android SDK platform 36
- Android Build Tools 36.0.0

Build locally:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The included GitHub Actions workflow builds both debug and release APK artifacts on pushes to `main`, pull requests, and manual workflow runs.

## Architecture

The app is a single Java `Activity` using the platform `android.webkit.WebView` API plus the official `androidx.webkit:webkit:1.17.0` compatibility library. The Jetpack layer is used for isolated WebView profiles, complete browsing-data deletion, and explicit privacy controls that are not exposed by the base framework API.

## Known trade-offs

- Strict mode breaks many JavaScript-heavy sites. Use COMPAT mode only when needed.
- Blocking all downloads/file pickers intentionally makes upload/download workflows unavailable.
- Blocking external URI schemes means phone calls, email links, native payment apps, OAuth app hand-offs, and deep links will not leave the browser.
- The built-in tracker list is intentionally small and auditable; it is not a substitute for a continuously maintained filter-list engine.
- WebView itself is supplied by the Android system, so its engine and fingerprint surface depend on the user's installed Android System WebView/Chrome provider.

## Next security upgrades

For a stronger production browser, the next architectural step is a dedicated browser engine/profile layer (for example, a maintained Gecko/Chromium embedding stack), per-site permission controls, first-party isolation, a real filter-list engine, DNS-over-HTTPS with explicit trust policy, optional proxy/Tor routing, certificate pinning only where operationally appropriate, and reproducible signed releases.
