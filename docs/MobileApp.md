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

## App icon and splash screen

The icon is the "J" from the JukeANator wordmark and the splash screen is the whole wordmark, both on black (the Web/Mobile UI's background). Both come from `src/main/resources/static/images/JukeANatorLogo.png`. The website's `android-chrome-*.png` and `apple-touch-icon.png` were not usable: their grey checkerboard background is painted into the image, not real transparency.

- `mobile/scripts/make-icon-sources.ps1` builds the source images in `mobile/assets`: `icon-only.png` (full icon), `icon-foreground.png` and `icon-background.png` (Android adaptive icon layers), `splash.png` and `splash-dark.png`. It isolates the J by masking out the neighboring letters, with pixel coordinates specific to that logo file.
- `npm run assets -- --android` (or `-- --ios` on the Mac) runs `@capacitor/assets`, which generates every icon and splash size into the native projects.
- Android 12 and later do not show `splash.png`; they show the app icon on the color `windowSplashScreenBackground` (set to black in `mobile/android/app/src/main/res/values/styles.xml`).

To change the artwork, replace the files in `mobile/assets` (or edit and re-run the script), then run the `npm run assets` command again and rebuild the app. The command overwrites `mobile/android/app/src/main/AndroidManifest.xml` formatting; that change is harmless.

## Native features

- **Geo-fence location.** The web UI reads the position with `navigator.geolocation`. In the Android app, Capacitor's WebView asks the patron for the location permissions declared in `AndroidManifest.xml`. iOS needs `NSLocationWhenInUseUsageDescription` in `Info.plist`.
- **Voice search.** A microphone button in the search bars uses the phone's own speech recognizer through the `@capacitor-community/speech-recognition` plugin. `app.js` calls it through Capacitor's native bridge (`Capacitor.nativePromise`), because the UI loads no bundler or `@capacitor/core`, and shows the button only when the plugin is in the build. On Android the plugin adds the microphone permission itself. iOS needs `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription` in `Info.plist`. After pulling a change to `mobile/package.json`, run `npm install` in `mobile` before `npm run android`.

## Still to do

- iOS icons and splash screens: on the Mac, `npm run assets -- --ios` after `npx cap add ios`.
- Native plugins: a barcode scanner for jukebox QR codes, and push notifications. The geo-fence already works in the Android app through the WebView's location access (`AndroidManifest.xml` declares the location permissions); iOS needs `NSLocationWhenInUseUsageDescription` in `Info.plist` after `npx cap add ios`.
- Store submission: see [apple-appstore-submission-checklist.md](apple-appstore-submission-checklist.md) and [google-appstore-submission-checklist.md](google-appstore-submission-checklist.md).
- Verify each Add Funds payment method inside the app's WebView: card entry (Hosted Fields) first; PayPal, Venmo and Google Pay may need native plugins.
- Store accounts: Apple Developer Program and Google Play Console.
- Once the store listings exist, set `mobile-app.ios-store-url` and `mobile-app.android-store-url` in the production `application.yml` so the `/get-app` QR code sends phones to the stores.
