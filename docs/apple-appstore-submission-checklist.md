# Apple App Store Submission Checklist

The complete path from the current code base to the JukeANator iPhone app being live on the App Store, followed by the write-ups to give App Review. The Android equivalent is [google-appstore-submission-checklist.md](google-appstore-submission-checklist.md). Background on the Capacitor wrapper is in [MobileApp.md](MobileApp.md), and on payments in [WebPaymentsWithBraintree.md](WebPaymentsWithBraintree.md) and [per-location-accounts.md](per-location-accounts.md).

Items marked (done) are already in the code base. Apple's guideline numbers are cited as of November 2026; confirm the current wording at developer.apple.com/app-store/review/guidelines before submitting.

---

## Part 1: The case for approval

App Review will ask two questions about this app. The answers below are written to be pasted into App Store Connect, under App Review Information, Notes, and adapted as the app changes.

### 1a. Why song credits are paid through Braintree and not In-App Purchase

Guideline 3.1.1 requires In-App Purchase for digital content and features used inside the app. Guideline 3.1.3(e), "Goods and Services Outside of the App", says that purchases of physical goods or services consumed outside the app must use a payment method other than In-App Purchase. JukeANator's credits fall under 3.1.3(e):

- **The service is physical and happens at a venue.** A credit pays for a song to be played by a physical jukebox installed at a bar, restaurant or pool hall, through that venue's own sound system. The patron hears it in the room, not on the phone.
- **Credits are tied to one location.** Credits are bought for the location the patron has selected, and that location has one jukebox. The purchase screen says "Credits for *Location*. They can be used only at this location.", and the receipt email says the same. Credits cannot be used at any other location, transferred to another user, or exchanged for anything else.
- **Nothing digital is unlocked.** Credits do not stream, download or play music on the phone, and do not unlock any feature of the app. Browsing, search, playlists and favorites are free. Credits are spent only to add a song to that jukebox's queue (or to move it up).
- **It is the same service as the coin and card slot.** The same jukebox takes cash in its bill acceptor and cards in its own card reader (Nayax). Each Add Funds purchase is recorded in that location's financial ledger in the same way as a swipe on the card reader, and the bar owner is paid from it in the same way.
- **The jukebox can require the patron to be there.** A venue can turn on a geo-fence, so a song can be queued only from a phone that is at the venue (Part 1b).

**Suggested App Review note:**

> JukeANator is the companion app for JukeANator jukeboxes: physical jukeboxes installed at bars, restaurants and pool halls. A patron selects the venue they are at, browses that jukebox's music library, buys song credits for that venue, and picks songs. The jukebox then plays them through the venue's sound system.
>
> Song credits are a payment for a physical service delivered at the venue. They are bought for one venue, can be used only on that venue's jukebox, and are spent only to queue songs on it. They do not stream, download or play any music on the device, and do not unlock any content or feature in the app. Under guideline 3.1.3(e), payments for services consumed outside the app use a payment method other than In-App Purchase, so we process them with Braintree (PayPal). Every purchase is validated by our server before any credits are granted, and is recorded in the venue's accounting alongside the jukebox's cash and card revenue.
>
> The attached video shows the whole flow at a real venue: selecting the venue, buying credits, picking a song, the song appearing in the jukebox's queue, and the jukebox playing it.
>
> Demo account: (email) / (password). The demo account is set up for the location "(demo location name)", which is not geo-fenced, so it can be used away from the venue.

**Wording to avoid,** because it weakens the 3.1.3(e) argument:
- "wallet", "balance top-up", "coins" or "currency" for credits. "Song credits" or "song plays" are the right terms.
- "Music streaming" or "music player" in the listing or the notes.
- A purchase screen that does not name the venue.

### 1b. Why the app is more than a website in a wrapper

Guideline 4.2 ("Minimum Functionality") rejects apps that are only a repackaged website. The app is built with Capacitor, so its screens come from the same code as the Web/Mobile UI. The case rests on what the app does on the phone that the website cannot, or cannot do as well. Each item below says whether it is in place today. **The items marked "to build" are what will carry the 4.2 argument, so build at least the first three before the first submission.**

- **GPS location for the geo-fence (in place in the web code; needs the iOS permission text).** A venue can require patrons to be at the venue to queue songs. The app reads the phone's GPS position when a song is queued, and sends it with the request, and the server checks it against the venue's location (`GeoFenceController`). This keeps remote users from taking over a bar's jukebox. Today this uses the WebView's location access; the native Capacitor Geolocation plugin is the better choice in the app (more accurate, with a proper permission prompt).
- **Scanning the QR code on the jukebox (to build).** Each jukebox shows a QR code. Scanned with the app's camera scanner (for example `@capacitor-mlkit/barcode-scanning`), it should select that venue directly. Today the printed QR code points at the generic `/get-app` address; a per-location code (for example `/get-app?location=<id>`) plus scanner support in the app is needed.
- **Nearby venues (to build).** Sort the location picker by distance from the phone's GPS position, so the venue the patron is in appears first.
- **Voice search (built; needs the iOS permission texts).** A microphone button inside the search bar turns speech into a search, using Apple's own speech recognition through a native plugin. Safari's speech interface is not available to pages inside the app's WebView, so this is a native feature. See "Voice search" below.
- **Mobile keyboard for search (in place).** The search screen uses a search-type field, so the phone shows its search keyboard with a Search key (`enterkeyhint="search"`), and the phone keyboard's own dictation key works in it. The search box on the home screen is a plain text field and should get the same attributes.
- **Push notifications (to build).** For example "Your song is up next at *Venue*" and "Your song is playing now". The server already knows the moment each song starts (the live queue events), so this needs the Capacitor Push Notifications plugin, Apple Push Notification service keys, and a sender on the server.
- **Live queue and now playing (in place).** The app keeps a live connection to the server and shows the jukebox's queue, the song playing and the patron's credit balance changing in real time.
- **Haptics (to build, small).** A short vibration when a song is queued or a purchase succeeds (Capacitor Haptics).
- **Opening email links in the app (to build).** Universal Links so the account verification link opens the app instead of Safari.
- **Native payment sheets (to verify).** Card entry works in the WebView. PayPal, Venmo and Google Pay switch to other apps or pop-up windows, so they may need native handling in the app, and Venmo cannot run inside a WebView at all; hide any method that does not work in the app before submitting. Apple Pay stays switched off (`APPLE_PAY_ENABLED = false`) until an Apple Pay merchant ID is set up.
- **Playing media on the phone (future, not implemented).** A later version is planned to play the patron's own media files on the phone. This would be a further native capability (background audio, the lock-screen player). Keep it completely separate from credits: credits must never pay for anything played on the phone, or the 3.1.3(e) argument above no longer holds.

**Suggested App Review note (add to the note in 1a):**

> The app uses the device's location to confirm that a patron is at the venue before queueing a song on a venue's jukebox (venues can turn this on to stop remote users controlling their jukebox), scans the QR code on the jukebox to connect to it, sorts venues by distance, supports voice search through the device's speech recognition, and sends a notification when the patron's song is about to play. The music library, queue and now-playing information update live from the jukebox.

Edit this note so that it lists only what the submitted build actually does. A reviewer who tries a listed feature and finds it missing will reject the app.

### Voice search

- **How it works.** A thin-line microphone sits at the right end of both search bars, on the home screen and on the search screen (`app.js`, "Voice search"; styles `.voice-search-btn` in `style.css`). Tapping it starts listening: the microphone turns orange inside a softly pulsing ring, the field reads "Listening…", and the words fill in as they are recognized. When the patron stops talking (about 1.5 seconds of silence), the search runs. Tapping the microphone again stops listening without searching and keeps the words heard so far. The home screen's microphone opens the search screen already listening.
- **Native in the app.** Inside the app it uses the phone's own speech recognizer through `@capacitor-community/speech-recognition` (in `mobile/package.json`). The UI is plain script with no bundler, so `app.js` calls the plugin through Capacitor's native bridge (`Capacitor.nativePromise` and `Capacitor.addListener`). The button appears only when the plugin is installed in the build.
- **In browsers.** Chrome, Edge and Safari use their built-in speech recognition (the Web Speech API). Browsers without it, such as Firefox, show no button. The phone keyboard's own dictation key works in the search field everywhere.
- **iOS setup still to do** (after `npx cap add ios`): `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription` in `Info.plist` (see the build checklist below). Without them iOS ends the app the first time the microphone is tapped. iOS asks the patron for both permissions on the first tap.
- **iOS listening does not stop on its own,** so `app.js` stops it after 1.5 seconds without new words, after 7 seconds if nothing is said, and after 15 seconds at most.

---

## Part 2: Checklist

### Accounts and agreements

- [ ] Get a D-U-N-S number for the company (free; can take a week or more). Required to enroll as an organization.
- [ ] Enroll in the Apple Developer Program as an organization (99 USD per year, developer.apple.com).
- [ ] In App Store Connect, accept the agreements under Business. The Paid Apps agreement is not needed for a free app with no In-App Purchase.
- [ ] Ask App Review about the payment model before building the submission: send the 1a note, with the video if it exists, through the App Review contact form, and keep the answer in writing.

### Server (production)

- [ ] (done) CORS allows the app origin `capacitor://localhost` (`SecurityConfig`, `app.cors.allowed-origins`).
- [ ] (done in code) Credits are tied to one location (V2 migration). Deploy it to production, after refunding or voiding any real test purchases in Braintree.
- [ ] Production Braintree credentials are set on the production server, and `braintree.environment` is `production`.
- [ ] A demo location for the reviewer: a real jukebox (or a slave that stays connected) with music, no geo-fence, and credits on the demo account at that location.
- [ ] The privacy policy (`https://www.jukeanator.com/privacy_policy.html`) mentions location, microphone and speech use (once those are used) and the payment processor.
- [ ] A support page or address that works (App Store Connect requires a support URL).

### App features (Part 1b)

- [ ] Native geolocation: add `@capacitor/geolocation` and use it in `getGeoPosition()` when running in the app.
- [ ] QR code scanning of the jukebox, and per-location QR codes.
- [ ] Location picker sorted by distance.
- [ ] (done in code) Voice search. Test it on an iPhone once the `Info.plist` texts are in.
- [ ] Push notifications for "your song is up next".
- [ ] Haptics on queue and purchase.
- [ ] The home-screen search box uses `type="search"` and `enterkeyhint="search"`.
- [ ] Consider renaming the "Add Funds" tab to "Buy Song Credits": "funds" suggests a wallet.

### Build on the Mac

- [ ] A Mac with a current macOS and Xcode (Apple requires uploads from a current Xcode), or a cloud build service.
- [ ] From `mobile`: `npm install`, `npx cap add ios`, `npm run ios`. Commit `mobile/ios`.
- [ ] `npm run assets -- --ios` for the icons and splash screens.
- [ ] Xcode, Signing and Capabilities: select the team. Bundle ID `com.jukeanator.app` (permanent once uploaded).
- [ ] Capabilities: Push Notifications (when push is built), Associated Domains (when Universal Links are built).
- [ ] `Info.plist` usage strings, one for each permission the build uses, written in terms of what the patron gets:
  - `NSLocationWhenInUseUsageDescription`: for example "JukeANator uses your location to confirm you are at the venue when you queue a song, and to show nearby venues."
  - `NSCameraUsageDescription`: for example "JukeANator uses the camera to scan the QR code on a jukebox."
  - `NSMicrophoneUsageDescription` and `NSSpeechRecognitionUsageDescription`: for voice search.
- [ ] `ITSAppUsesNonExemptEncryption` set to `NO` (the app only uses standard HTTPS), so App Store Connect does not ask about export compliance on every build.
- [ ] Privacy manifest (`PrivacyInfo.xcprivacy`): Capacitor and each plugin ship their own; add the app's own if Xcode reports missing required-reason API declarations when archiving.
- [ ] Set the version and build number (increase the build number for every upload).

### Test on real iPhones

- [ ] Sign up, email verification, sign in, sign out, password change, Delete Account (guideline 5.1.1(v) requires in-app account deletion; done).
- [ ] Select a location; browse, search (keyboard Search key, keyboard dictation, and the microphone button: allow and refuse the permissions, a short and a long phrase, tapping the microphone again to stop); queue a song; see it in the live queue.
- [ ] Geo-fence at a fenced location: the location prompt appears with the usage string; queueing works at the venue and is refused away from it; a refused permission gives a clear message.
- [ ] Add Funds with each payment method that will be offered (card first). Hide the rest. Check that the receipt email arrives and names the location.
- [ ] Credits bought at one location do not show or work at another.
- [ ] Rotation, small and large iPhones, dark surroundings (the UI is dark), poor network (an offline location gives a clear message).
- [ ] TestFlight: upload a build (Product, Archive, Distribute App) and test it through TestFlight on at least one other person's phone.

### App Store Connect listing

- [ ] App name, subtitle, description and keywords. Describe a jukebox remote for venues; do not use "streaming".
- [ ] Category: Music (primary), Entertainment (secondary).
- [ ] Screenshots for the required iPhone sizes (6.9-inch at least), showing the venue picker, the location notice on the purchase screen, the queue and now playing.
- [ ] Privacy policy URL and support URL.
- [ ] App Privacy questionnaire. Data linked to the user: email address, name, purchase history, and precise location when the geo-fence is used. Location is used for app functionality, not tracking. The app does no tracking.
- [ ] Age rating questionnaire. Venues include bars, so answer the alcohol references question honestly; music lyrics may need "infrequent mature themes" or similar.
- [ ] Price: free.
- [ ] App Review Information: the notes from Part 1, the demo account, a contact phone number, and the video (attach, or link to it in the notes).
- [ ] The video (about 1 to 2 minutes, filmed at a venue): select the venue (or scan its QR code), buy credits (show the location notice), pick a song, show it in the jukebox's queue on its screen, and show the jukebox playing it.

### Submit and after approval

- [ ] Submit for review. If rejected under 3.1.1, reply in the Resolution Center citing 3.1.3(e) and the points in 1a; if rejected under 4.2, list the native features the build uses.
- [ ] After approval, set `mobile-app.ios-store-url` in the production `application.yml` and restart, so the `/get-app` QR codes send iPhones to the App Store.
- [ ] Every web UI change reaches app users only through a new app build and review.
