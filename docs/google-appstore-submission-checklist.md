# Google Play Submission Checklist

The complete path from the current code base to the JukeANator Android app being live on Google Play, followed by the write-ups that justify it to Google's review. The iPhone equivalent is [apple-appstore-submission-checklist.md](apple-appstore-submission-checklist.md). Background on the Capacitor wrapper is in [MobileApp.md](MobileApp.md), and on payments in [WebPaymentsWithBraintree.md](WebPaymentsWithBraintree.md) and [per-location-accounts.md](per-location-accounts.md).

Items marked (done) are already in the code base. Google's policies are cited as of November 2026; confirm the current wording in the Play Console Policy Center before submitting.

---

## Part 1: The case for approval

Google Play has no free-form review notes like Apple's. These write-ups go in the App access instructions (Policy, App content), in any reply to a policy rejection or appeal, and, with small changes, in the store description.

### 1a. Why song credits are paid through Braintree and not Google Play Billing

Google's Payments policy requires Google Play Billing for digital goods and services, such as in-game currency, subscriptions and digital content. It explicitly does not apply to payments for physical goods or for physical services. JukeANator's credits are a physical service:

- **The service is physical and happens at a venue.** A credit pays for a song to be played by a physical jukebox installed at a bar, restaurant or pool hall, through that venue's own sound system. The patron hears it in the room, not on the phone.
- **Credits are tied to one location.** Credits are bought for the location the patron has selected, and that location has one jukebox. The purchase screen says "Credits for *Location*. They can be used only at this location.", and the receipt email says the same. Credits cannot be used at any other location, transferred to another user, or exchanged for anything else.
- **Nothing digital is unlocked.** Credits do not stream, download or play music on the phone, and do not unlock any feature of the app. Browsing, search, playlists and favorites are free. Credits are spent only to add a song to that jukebox's queue (or to move it up).
- **It is the same service as the coin and card slot.** The same jukebox takes cash in its bill acceptor and cards in its own card reader (Nayax). Each purchase in the app is recorded in that location's financial ledger in the same way as a swipe on the card reader, and the bar owner is paid from it in the same way.
- **The jukebox can require the patron to be there.** A venue can turn on a geo-fence, so a song can be queued only from a phone that is at the venue (Part 1b).

**Suggested write-up (for App access and any policy reply):**

> JukeANator is the companion app for JukeANator jukeboxes: physical jukeboxes installed at bars, restaurants and pool halls. A patron selects the venue they are at, browses that jukebox's music library, buys song credits for that venue, and picks songs. The jukebox then plays them through the venue's sound system.
>
> Song credits are a payment for a physical service delivered at the venue, so they are outside the scope of Google Play Billing under the Payments policy. They are bought for one venue, can be used only on that venue's jukebox, and are spent only to queue songs on it. They do not stream, download or play any music on the device, and do not unlock any content or feature in the app. Payments are processed by Braintree (PayPal) and validated by our server before any credits are granted. Each purchase is recorded in the venue's accounting alongside the jukebox's cash and card revenue.
>
> A video of the whole flow at a real venue (selecting the venue, buying credits, picking a song, the song appearing in the jukebox's queue, and the jukebox playing it): (link).
>
> To review the app: sign in with (email) / (password) and select the location "(demo location name)". That location is not geo-fenced and the account already has credits there, so it can be used away from the venue.

**Wording to avoid,** because it weakens the physical-service argument:
- "wallet", "balance top-up", "coins" or "currency" for credits. "Song credits" or "song plays" are the right terms.
- "Music streaming" or "music player" in the listing.
- A purchase screen that does not name the venue.

### 1b. Why the app is more than a website in a wrapper

Google's Spam and Minimum Functionality policy rejects apps whose main purpose is to show a website in a WebView, and apps that add nothing to it. The app is built with Capacitor, so its screens come from the same code as the Web/Mobile UI. The case rests on what the app does on the phone that the website cannot, or cannot do as well. Each item below says whether it is in place today. **The items marked "to build" are what will carry this argument, so build at least the first three before the first submission.**

- **GPS location for the geo-fence (in place).** A venue can require patrons to be at the venue to queue songs. The app reads the phone's GPS position when a song is queued, and sends it with the request, and the server checks it against the venue's location (`GeoFenceController`). This keeps remote users from taking over a bar's jukebox. `AndroidManifest.xml` declares the foreground location permissions, and Capacitor shows Android's own permission prompt the first time a geo-fenced location asks for the position. The native Capacitor Geolocation plugin is an optional later improvement.
- **Scanning the QR code on the jukebox (to build).** Each jukebox shows a QR code. Scanned with the app's camera scanner (for example `@capacitor-mlkit/barcode-scanning`, which uses Google's ML Kit), it should select that venue directly. Today the printed QR code points at the generic `/get-app` address; a per-location code (for example `/get-app?location=<id>`) plus scanner support in the app is needed.
- **Nearby venues (to build).** Sort the location picker by distance from the phone's GPS position, so the venue the patron is in appears first.
- **Voice search (built).** A microphone button inside the search bar turns speech into a search, using Android's own speech recognizer through a native plugin. The app's WebView does not offer Chrome's speech interface to web pages, so this is a native feature. See "Voice search" below.
- **Mobile keyboard for search (in place).** The search screen uses a search-type field, so the phone shows its search keyboard with a Search key (`enterkeyhint="search"`), and the keyboard's voice typing key (Gboard's microphone) works in it. The search box on the home screen is a plain text field and should get the same attributes.
- **Push notifications (to build).** For example "Your song is up next at *Venue*" and "Your song is playing now". The server already knows the moment each song starts (the live queue events), so this needs the Capacitor Push Notifications plugin, Firebase Cloud Messaging, and a sender on the server.
- **Live queue and now playing (in place).** The app keeps a live connection to the server and shows the jukebox's queue, the song playing and the patron's credit balance changing in real time.
- **Haptics (to build, small).** A short vibration when a song is queued or a purchase succeeds (Capacitor Haptics).
- **Opening email links in the app (to build).** Android App Links, so the account verification link opens the app instead of the browser. This needs `/.well-known/assetlinks.json` on the server, which `SecurityConfig` does not yet allow without signing in.
- **Native payment sheets (to verify).** Card entry works in the WebView. PayPal, Venmo and Google Pay switch to other apps or pop-up windows, so they may need native handling in the app, and Venmo cannot run inside a WebView at all; hide any method that does not work in the app before submitting.
- **Playing media on the phone (future, not implemented).** A later version is planned to play the patron's own media files on the phone. This would be a further native capability (a media playback service, the notification and lock-screen player). Keep it completely separate from credits: credits must never pay for anything played on the phone, or the physical-service argument above no longer holds.

**Suggested write-up (add to the one in 1a when replying to a policy question):**

> The app uses the device's location to confirm that a patron is at the venue before queueing a song on a venue's jukebox (venues can turn this on to stop remote users controlling their jukebox), scans the QR code on the jukebox to connect to it, sorts venues by distance, supports voice search through the device's speech recognition, and sends a notification when the patron's song is about to play. The music library, queue and now-playing information update live from the jukebox.

Edit it so that it lists only what the submitted build actually does.

### Voice search

- **How it works.** A thin-line microphone sits at the right end of both search bars, on the home screen and on the search screen (`app.js`, "Voice search"; styles `.voice-search-btn` in `style.css`). Tapping it starts listening: the microphone turns orange inside a softly pulsing ring, the field reads "Listening…", and the words fill in as they are recognized. When the patron stops talking (about 1.5 seconds of silence), the search runs. Tapping the microphone again stops listening without searching and keeps the words heard so far. The home screen's microphone opens the search screen already listening.
- **Native in the app.** Inside the app it uses the phone's own speech recognizer through `@capacitor-community/speech-recognition` (in `mobile/package.json`). The UI is plain script with no bundler, so `app.js` calls the plugin through Capacitor's native bridge (`Capacitor.nativePromise` and `Capacitor.addListener`). The button appears only when the plugin is installed in the build.
- **In browsers.** Chrome, Edge and Safari use their built-in speech recognition (the Web Speech API). Browsers without it, such as Firefox, show no button. The phone keyboard's own dictation key works in the search field everywhere.
- **Android permissions.** The plugin's own manifest adds `RECORD_AUDIO` and the `<queries>` entry for `android.speech.RecognitionService` (needed on Android 11 and later to find the phone's recognizer) to the app when it is built, so `AndroidManifest.xml` needs no change. Android asks the patron for the microphone on the first tap.
- **Where the audio goes.** Google's recognizer may send the audio to Google to recognize it, depending on the phone. Say so in the privacy policy and the Data safety form.

---

## Part 2: Checklist

### Accounts

- [ ] Decide between a personal and an organization developer account. An organization account needs a D-U-N-S number (free; can take a week or more). A new personal account must run a closed test with at least 12 testers for 14 days before it can publish to production.
- [ ] Create the Google Play Console account (25 USD one-time, play.google.com/console) and complete identity verification.
- [ ] Optional: ask Google Play developer support about the payment model before the first submission, and keep the answer in writing.

### Server (production)

- [ ] (done) CORS allows the app origin `https://localhost` (`SecurityConfig`, `app.cors.allowed-origins`).
- [ ] (done in code) Credits are tied to one location (V2 migration). Deploy it to production, after refunding or voiding any real test purchases in Braintree.
- [ ] Production Braintree credentials are set on the production server, and `braintree.environment` is `production`. For Google Pay, register in the Google Pay and Wallet Console, then switch `initGooglePay()` to `PRODUCTION` with the merchant id.
- [ ] A demo location for the reviewer: a real jukebox (or a slave that stays connected) with music, no geo-fence, and credits on the demo account at that location.
- [ ] An account deletion web page. Google requires a link, outside the app, where a user can ask for their account and data to be deleted. Either a page on the website that explains how to delete the account from the web UI's Account screen, or one that takes a request by email.
- [ ] The privacy policy (`https://www.jukeanator.com/privacy_policy.html`) mentions location, camera, microphone and speech use (once those are used) and the payment processor.

### App features (Part 1b)

- [ ] (done) Location permissions in `mobile/android/app/src/main/AndroidManifest.xml`: `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`, foreground only (background location would need a separate declaration). GPS is declared optional, so Play still offers the app to devices without it. A refused permission tells the patron to allow location for JukeANator in the phone's Settings app. Optional later: `@capacitor/geolocation` in `getGeoPosition()`.
- [ ] QR code scanning of the jukebox (`CAMERA` permission), and per-location QR codes.
- [ ] Location picker sorted by distance.
- [ ] (done; tested on a Samsung Galaxy S24+ on 2026-11-25) Voice search. After pulling, run `npm install` and `npm run android` in `mobile`, then Sync Project with Gradle Files in Android Studio, so the plugin is in the build.
- [ ] Push notifications (Firebase project, `google-services.json`, `POST_NOTIFICATIONS` permission for Android 13 and later).
- [ ] Haptics on queue and purchase.
- [ ] The home-screen search box uses `type="search"` and `enterkeyhint="search"`.
- [ ] Consider renaming the "Add Funds" tab to "Buy Song Credits": "funds" suggests a wallet.

### Build

- [ ] (done) Capacitor project in `mobile/android`; application id `com.jukeanator.app` (permanent once uploaded); app icon and splash screen.
- [ ] (done) `targetSdkVersion` 36 (`mobile/android/variables.gradle`), which meets Google's target API level requirement for new apps. Check the requirement again before each yearly deadline.
- [ ] Set `versionCode` and `versionName` in `mobile/android/app/build.gradle`. Increase `versionCode` for every upload.
- [ ] `npm run android`, then in Android Studio, Build, Generate Signed App Bundle, Android App Bundle. Create the upload keystore when prompted, and keep it and its passwords outside git, backed up; losing it blocks future updates.
- [ ] Opt in to Play App Signing when creating the first release (Google keeps the app signing key; the keystore above is only the upload key).

### Test on real Android phones

- [ ] Sign up, email verification, sign in, sign out, password change, Delete Account (done in the app).
- [ ] Select a location; browse, search (keyboard Search key, keyboard voice typing, and the microphone button: allow and refuse the permission, a short and a long phrase, tapping the microphone again to stop); queue a song; see it in the live queue.
- [ ] Geo-fence at a fenced location: the Android location prompt appears; queueing works at the venue and is refused away from it; a refused permission gives a clear message.
- [ ] Add Funds with each payment method that will be offered (card first). Hide the rest. Check that the receipt email arrives and names the location.
- [ ] Credits bought at one location do not show or work at another.
- [ ] A small and a large phone, Android 7 (`minSdkVersion` 24) if a device is available, the system back button, poor network (an offline location gives a clear message).
- [ ] Internal testing track: upload the `.aab`, install it from Play on the test phones.
- [ ] Closed testing track: at least 12 testers for at least 14 days if the account is a new personal account.

### Play Console: App content (Policy section)

- [ ] Privacy policy URL.
- [ ] App access: "All or some functionality is restricted", with the demo account, the demo location, and the write-up from Part 1a.
- [ ] Ads: the app contains no ads.
- [ ] Content rating (IARC questionnaire). Venues include bars, so answer the alcohol references question honestly; music lyrics may contain mature language.
- [ ] Target audience: 18 and over, so the app is not subject to the Families policy.
- [ ] Data safety form. Collected and linked to the user: name, email address, purchase history, approximate and precise location (when the geo-fence is used), and voice audio if voice search sends audio off the phone (on-device recognition does not). Payment card details are collected by Braintree, not by the app. Data is encrypted in transit. Users can request deletion (the app's Delete Account, plus the web page above).
- [ ] Account deletion: the in-app path and the web page URL.
- [ ] Financial features declaration: the app is not a financial services app (it does not offer banking, loans, money transfer or crypto). Answer accordingly.
- [ ] Government, news and health declarations: not applicable.

### Store listing

- [ ] App name, short description (80 characters) and full description. Describe a jukebox remote for venues; do not use "streaming".
- [ ] Category: Music and Audio.
- [ ] App icon (512 x 512), feature graphic (1024 x 500), phone screenshots (at least 2; show the venue picker, the location notice on the purchase screen, the queue and now playing).
- [ ] Contact email and website; support URL.
- [ ] The video from Part 1a, uploaded to YouTube (unlisted is fine) for the replies to reviewers. Optionally a promo video for the listing.

### Release and after approval

- [ ] Promote the tested release to Production. A staged rollout (for example 20 percent first) limits the impact of a bad release.
- [ ] If rejected under the Payments policy, appeal citing the physical-services exception and the points in 1a; if rejected under Minimum Functionality, list the native features the build uses.
- [ ] After approval, set `mobile-app.android-store-url` in the production `application.yml` and restart, so the `/get-app` QR codes send Android phones to Google Play.
- [ ] Every web UI change reaches app users only through a new app build and review.
