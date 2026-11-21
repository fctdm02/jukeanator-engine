# JukeANator Mobile App (Capacitor)

The iOS and Android apps are a Capacitor wrapper around the same Web/Mobile UI the server serves at `/app/`. There is one UI codebase: `src/main/resources/static/app`. The `mobile` folder only holds the wrapper project.

## How it works

- `mobile/scripts/sync-web.mjs` copies `src/main/resources/static/app` into `mobile/www` and replaces `www/js/config.js` with one that sets `apiBaseUrl` to the master server (default `https://www.jukeanator.com`).
- Capacitor bundles `mobile/www` into the native apps. The bundled page is loaded from the device itself (origin `capacitor://localhost` on iOS, `https://localhost` on Android), so `app.js` prefixes every REST call, the `/ws` websocket and every server-hosted image with `apiBaseUrl`.
- The server allows those two origins on `/api/**` via `corsConfigurationSource()` in `SecurityConfig`. The list can be overridden with the property `app.cors.allowed-origins` (comma-separated). The `/ws` SockJS endpoint already allows any origin (see `WebSocketConfig`).
- In the browser, `config.js` keeps `apiBaseUrl` empty, so nothing changes for web users.
- `mobile/www` and `mobile/node_modules` are generated and git-ignored. Never edit files in `www`; edit the web UI under `src/main/resources/static/app` and re-run the sync.
- The generated `mobile/android` and `mobile/ios` folders are native projects and should be committed (Capacitor's recommendation), because app icons, permissions and signing settings are edited there.

## Prerequisites

- Node.js, current LTS release (version 22 or newer), from nodejs.org. Capacitor's CLI requires it.
- Android: Android Studio (includes the Android SDK and an emulator). Works on Windows.
- iOS: a Mac with Xcode. iOS apps can only be built and signed on macOS (or through a cloud build service such as Codemagic or Ionic Appflow).
- The server running the CORS change in `SecurityConfig` must be deployed to production before the app can talk to it.

## One-time setup

Run these from the `mobile` folder after installing Node.js. The first command installs the latest Capacitor packages and records their versions in `package.json`.

```
npm install @capacitor/core @capacitor/android @capacitor/ios
npm install -D @capacitor/cli
npm run sync-web
npx cap add android
```

On the Mac, also run:

```
npx cap add ios
```

## Day-to-day workflow

After changing the web UI, copy it into the native projects and open the IDE:

```
npm run android
```

or on the Mac:

```
npm run ios
```

Both run `sync-web`, then `cap sync` (copies `www` into the native projects and updates plugins), then open Android Studio or Xcode, where the app is run on an emulator or a connected phone.

To point a build at a different master (for example a test server), set `JUKEANATOR_API_BASE_URL` for that run. In PowerShell:

```
$env:JUKEANATOR_API_BASE_URL = 'https://test.example.com'; npm run sync
```

## Testing notes

- The Android app's page origin is `https://localhost`, so it cannot call a plain `http://` server (the WebView blocks it as mixed content). Test against an HTTPS server such as production, after the CORS change is deployed there.
- The app identifier is `com.jukeanator.app` (`appId` in `mobile/capacitor.config.json`), used as both the iOS bundle ID and the Android application ID. It becomes permanent once an app is uploaded to either store, so never change it.
- Email verification links open `https://www.jukeanator.com/app/?verify=...` in the phone's browser, not in the app. After verifying, the user signs in inside the app as usual. Opening those links directly in the app (App Links / Universal Links) is a later enhancement.

## Still to do

- App icons and splash screens (the `@capacitor/assets` tool generates every size from one source image).
- Native plugins: Geolocation (geo-fence), a barcode scanner for jukebox QR codes, and push notifications.
- Verify each Add Funds payment method inside the app's WebView: card entry (Hosted Fields) first; PayPal, Venmo and Google Pay may need native plugins.
- Store accounts: Apple Developer Program and Google Play Console.
- Once the store listings exist, set `mobile-app.ios-store-url` and `mobile-app.android-store-url` in the production `application.yml` so the `/get-app` QR code sends phones to the stores.
