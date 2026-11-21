# Read Me First

The following was discovered as part of building this project:

* The original package name 'com.djt.jukeanator-engine' is invalid and this project uses 'com.djt.jukeanator_engine' instead.

# Documentation

This file is for developers: building the application with Maven, running it from the source tree, and running the tests. Installing and configuring a jukebox PC is covered in separate guides:

- [WindowsConfig.md](WindowsConfig.md): Windows prerequisites (Java, media player), launch script, touchscreen, kiosk settings and auto-start.
- [LinuxConfig.md](LinuxConfig.md): the same for Linux Mint.
- [MasterConfig.md](MasterConfig.md): the MySQL database and master mode setup.
- [docs/MobileApp.md](docs/MobileApp.md): how the iOS/Android app wraps the Web/Mobile UI (see also "Mobile App Packaging/Deployment" below).

# Building the Application

Build the WAR from the project directory with the Maven wrapper (no separate Maven install is needed). On Linux:

```
./mvnw clean package -DskipTests
```

On Windows:

```
mvn clean package -DskipTests
```

In PowerShell, use `.\mvnw.cmd` instead of `mvnw.cmd`.

This creates `target/jukeanator-engine-0.0.1-SNAPSHOT.war`. `-DskipTests` skips the tests, some of which need a local MySQL server (see "Live Integration Tests"). To deploy the WAR on a jukebox PC, see [WindowsConfig.md](WindowsConfig.md) or [LinuxConfig.md](LinuxConfig.md).

# Deploying to the Production Server

The `prod` Maven profile builds the WAR, runs the tests, and deploys it to the production master instance in one step (Windows, from the project directory):

```
mvnw clean deploy -Pprod
```

To deploy without the tests (for example, a change to the static web pages only), add the `skip-tests` profile:

```
mvnw clean deploy -Pprod,skip-tests
```

In PowerShell, use `.\mvnw.cmd`. In Eclipse, create a Maven Run Configuration with goals `clean deploy` and profile `prod` (or `prod,skip-tests` to skip the tests).

What it does:

- `clean`, `test` and `package` run as usual. A failing test stops the build before anything is deployed.
- The standard `install` and `deploy` steps (copying the WAR to the local Maven repository and publishing it to a Maven repository server) are skipped; this project has no repository server.
- In the `deploy` phase, Maven runs [deploy/deploy-prod.ps1](deploy/deploy-prod.ps1) `-SkipBuild`. It uploads the WAR over SSH and runs [deploy/jukeanator-deploy.sh](deploy/jukeanator-deploy.sh) on the server, which checks the upload, backs up the database, switches to the new WAR, restarts the application, and puts the previous WAR back if the new one does not start within four minutes. Any failure fails the Maven build.

Requirements:

- SSH key access to the server under the name `jukeanator-prod` in `%USERPROFILE%\.ssh\config` (see "Production Server (Linux VPS)", Step 1, in [MasterConfig.md](MasterConfig.md)). No passwords or secrets are used, and none are stored in git.
- Windows PowerShell and the Windows OpenSSH client (`ssh`, `scp`), both included in Windows 10 and 11.

To deploy to another server, set the `JUKEANATOR_DEPLOY_HOST` environment variable to that server's name in the SSH config. Plain `mvnw deploy` without `-Pprod` never contacts the server. The same deploy can be run without Maven with `powershell -ExecutionPolicy Bypass -File deploy\deploy-prod.ps1`. Details, the manual steps and rolling back are under "Updating the application" in [MasterConfig.md](MasterConfig.md).

# Running the Application

The application needs a Java 25 runtime and a media player (VLC, or Winamp on Windows). Installing both is described in [WindowsConfig.md](WindowsConfig.md) and [LinuxConfig.md](LinuxConfig.md). The environment variables it reads (`DISCOGS_CONSUMER_KEY`, `DISCOGS_CONSUMER_SECRET`, `JWT_SECRET`, and the database variables when MySQL is used) are described there and in [MasterConfig.md](MasterConfig.md).

## From the source tree

On Windows, in a Command Prompt in the project directory:

```
mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config"
```

On Linux:

```
./mvnw spring-boot:run "-Dspring-boot.run.arguments=--app.data-dir=$HOME/kiosk/data --app.config-dir=$HOME/kiosk/config"
```

In PowerShell, use `.\mvnw.cmd` instead of `mvnw.cmd`. `spring-boot:run` compiles the code but does not run the tests, so `-DskipTests` is not needed.

## From the WAR

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config --app.data-dir=C:\kiosk\data
```

## Externalized config (e.g. kiosk deployments)

By default the app seeds a `config/application.yml` (and sibling `data/`) next to the WAR on first run and reads overrides from there. To point it at a config directory somewhere else instead, pass `--app.config-dir` as a program argument (after the jar filename):

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config
```

For an Eclipse launch configuration, put these in the **Program arguments** box instead:

```
--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config
```

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`, etc.) come *before* `-jar <file>`, and everything after the jar filename is passed to the application. Putting `--app.config-dir` before `-jar` makes the Java launcher reject it as an unrecognized option.

On first run this seeds `C:\kiosk\config\application.yml` (with `app.data-dir` pre-set to the sibling directory `C:\kiosk\data`) if it does not already exist, then loads overrides from it. This is the same seeding behavior as the WAR-relative default, just rooted at the given directory instead.

# Running the Tests

Run the unit and integration tests with the Maven wrapper. On Linux:

```
./mvnw test
```

On Windows:

```
mvnw.cmd test
```

`./mvnw clean package` (without `-DskipTests`) runs the same tests before building the WAR. Some of the tests need a local MySQL server; see "Live Integration Tests".

## Live Integration Tests

Some tests run against a real local MySQL server: `JukeanatorEngineApplicationTests`, the `MySql*ApplicationTests` classes, the `*RepositoryJpaImplTest` classes, `SongLibraryRepositoryEquivalenceTest`, the `MasterSlave*IntegrationTest` classes and the `WebUiMaster*LifecycleTest` classes. They run with the normal `mvnw test` and need:

- MySQL 8.0 or later on `localhost:3306` (installation is described under "MySQL database" in [MasterConfig.md](MasterConfig.md)).
- A `jukeanator_test` database that the `jukeanator` user can access. The tests reset its tables, so never point them at a database holding real data.
- The `jukeanator` user's password in `DB_PASSWORD`, or the password `password` if `DB_PASSWORD` is not set (see `src/test/resources/application-test.yml`).

If the database and user were already created under "Master mode MySQL setup" in [MasterConfig.md](MasterConfig.md), nothing more is needed. Otherwise, create them with one command.

Linux (Mint):

```
sudo mysql -e "CREATE DATABASE IF NOT EXISTS jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password'; GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; FLUSH PRIVILEGES;"
```

Windows (in a Command Prompt in `C:\Program Files\MySQL\MySQL Server 8.4\bin`; enter the `root` password when asked):

```
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci; CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password'; GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; FLUSH PRIVILEGES;"
```

If the `jukeanator` user already exists with another password, `CREATE USER IF NOT EXISTS` leaves that password unchanged; set `DB_PASSWORD` to it before running the tests.

## JFC/Swing GUI Functional Tests

These tests drive the real full-screen Swing UI, so they are excluded from the normal test run. The `gui-tests` profile runs every test and then the GUI tests (in a separate JVM), so the local MySQL test database is needed as for the normal tests (see "Live Integration Tests"). Run them on a machine with a display, and do not use the mouse or keyboard while they run. On Linux:

```
./mvnw test -Pgui-tests
```

On Windows:

```
mvnw.cmd test -Pgui-tests
```

# Getting Started

## Reference Documentation

For further reference, see the following:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.4/maven-plugin)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.4/reference/web/servlet.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.4/reference/io/rest-client.html#io.rest-client.restclient)
* [JDBC API](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.4/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [WebSocket](https://docs.spring.io/spring-boot/4.0.4/reference/messaging/websockets.html)
* [Quartz Scheduler](https://docs.spring.io/spring-boot/4.0.4/reference/io/quartz.html)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.4/reference/io/email.html)

## Guides

The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)

## Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM. While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent. To prevent this, the project POM contains empty overrides for these elements. If you switch to a different parent and want the inheritance, remove those overrides.

# Mobile App Packaging/Deployment

The JukeANator iPhone and Android apps are the same Web/Mobile UI that the server serves at `/app/` (`src/main/resources/static/app`), packaged as native apps with [Capacitor](https://capacitorjs.com/). There is one UI codebase; the `mobile` folder holds only the wrapper project. How the wrapper works is described in [docs/MobileApp.md](docs/MobileApp.md).

The app is built with the server URL `https://www.jukeanator.com` and the app ID `com.jukeanator.app`. The app ID is used as both the iOS bundle ID and the Android application ID, and it can never be changed once the app is uploaded to either store.

## Installation/Setup

This covers the Windows PC used to build the Android app. The iPhone app can only be built on a Mac (see step 7 of "End-to-End Steps" below).

### Step 1: Install Node.js

Node.js runs JavaScript-based tools on the PC, much like a JDK runs Java tools. Capacitor's command-line tool needs it, and its package manager `npm` plays the role Maven plays for Java. In PowerShell:

```
winget install OpenJS.NodeJS.LTS
```

Close that PowerShell window and open a new one so it picks up the new PATH, then check the install. It should print v22 or later:

```
node --version
```

Alternatively, download the "LTS" Windows installer from nodejs.org and accept every default.

If PowerShell reports "npm.ps1 cannot be loaded because running scripts is disabled on this system", Windows' default execution policy is blocking the `npm.ps1` and `npx.ps1` wrapper scripts. Either type `npm.cmd` and `npx.cmd` instead of `npm` and `npx` (no settings change), or allow locally installed scripts for your Windows account once with `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned` and open a new PowerShell window.

### Step 2: Install Android Studio

Android Studio is the IDE for Android apps (comparable to Eclipse). It also installs the Android SDK, the build tools and the phone emulator. In PowerShell:

```
winget install Google.AndroidStudio
```

Then complete its one-time setup:

1. Start Android Studio from the Start menu.
2. If it asks to import settings, choose "Do not import settings".
3. In the Setup Wizard, choose the "Standard" install type. Accept the license agreements (there are several: select each one in the list on the left and click Accept).
4. Click Finish and wait while it downloads the Android SDK (several GB).
5. When the welcome screen appears, setup is done. Do not create a project; Capacitor generates one in the next step.

Android Studio installs to `C:\Program Files\Android\Android Studio`, which is where Capacitor expects it, so no environment variables are needed.

If the Setup Wizard was skipped or cancelled, the SDK is never downloaded, and opening a project shows a "Select SDKs" dialog asking for the path to the Android SDK. Click Cancel, then open the SDK Manager (Tools, SDK Manager; or "More Actions", SDK Manager on the welcome screen). Next to "Android SDK Location", click Edit, keep the suggested location `%LOCALAPPDATA%\Android\Sdk` (that is, `C:\Users\<you>\AppData\Local\Android\Sdk`), click Next, accept the licenses, and click Finish to download it. Then run "Sync Project with Gradle Files" (the elephant icon at the top right).

### Step 3: Create the Android project

Run these once, in PowerShell, from the `mobile` folder. The two `npm install` commands download the current Capacitor release and record its version in `mobile/package.json`; `npx cap add android` generates the native project in `mobile/android`, which should be committed to git.

```
cd C:\dev-wks\projects\jukeanator-engine\mobile
npm install @capacitor/core @capacitor/android @capacitor/ios
npm install -D @capacitor/cli
npm run sync-web
npx cap add android
```

### Step 4: Open the project in Android Studio

From the `mobile` folder:

```
npm run android
```

This copies the current web UI into the app (`npm run sync-web`), updates the native project (`cap sync`), and opens it in Android Studio. The first time, Android Studio runs a "Gradle sync" (Gradle is its build tool, similar to Maven) that takes several minutes; progress shows in the bottom status bar.

The first sync fails with "Incompatible Gradle JVM version" if Gradle runs on Java 25: the Gradle version in Capacitor's Android project (8.14.3) cannot run on Java 25, and current Android Studio releases bundle a JDK 25 (its `jbr` folder), as does this PC's own JDK. Gradle needs a JDK 21, which Android Studio can download once:

1. Open File, Settings, Build, Execution, Deployment, Build Tools, Gradle.
2. Open the "Gradle JDK" dropdown and choose "Download JDK...".
3. Set Version to 21 and Vendor to "Eclipse Temurin" (or "JetBrains Runtime"), keep the suggested location, and click Download.
4. When the download finishes, make sure the new JDK 21 is selected as "Gradle JDK", click OK, then click "Sync Project with Gradle Files" (the elephant icon at the top right).

This affects only the Android build; `JAVA_HOME` and the JDK used by Maven for the server stay unchanged.

### Step 5: Run the app

Before testing, the server the app talks to must have the CORS change in `SecurityConfig` deployed (see "Deploying to the Production Server" above). Without it the app opens but shows no music. The Android app cannot use a plain-http server such as `http://localhost:8080`, because its page runs from `https://localhost` and the WebView blocks the request as mixed content.

On a real Android phone (recommended; this PC has 8 GB of RAM, and Android Studio plus the emulator use most of it):

1. On the phone, open Settings, About phone, and tap "Build number" seven times to enable Developer options.
2. Open Settings, System, Developer options, and turn on "USB debugging".
3. Connect the phone by USB and tap "Allow" on the phone's prompt.
4. In Android Studio, select the phone in the device list at the top and click the green Run button.

On the emulator:

1. In Android Studio, open the Device Manager (phone icon on the right edge) and click "+", then "Create Virtual Device".
2. Pick a Pixel phone, choose the recommended system image (it downloads), and click Finish.
3. Select it in the device list and click the green Run button.

### Day-to-day workflow

After any change to the web UI under `src/main/resources/static/app`, run `npm run android` from the `mobile` folder again and click Run in Android Studio. Never edit files in `mobile/www`; that folder is regenerated on every sync. To build against a server other than production, set `JUKEANATOR_API_BASE_URL` for that run, for example in PowerShell:

```
$env:JUKEANATOR_API_BASE_URL = 'https://test.example.com'; npm run sync
```

## End-to-End Steps

The complete path from the current state to both apps being available in the stores. Steps marked "done" are already in the code base.

### 1. Prepare the web UI and server (done)

- The web UI reads the server URL from `src/main/resources/static/app/js/config.js` and uses it for all REST calls, the websocket and server-hosted images. It is empty for the browser.
- `SecurityConfig` allows cross-origin API calls from the app origins `capacitor://localhost` (iOS) and `https://localhost` (Android); override with the property `app.cors.allowed-origins`.
- The Capacitor project files are in `mobile` (`package.json`, `capacitor.config.json`, `scripts/sync-web.mjs`).

### 2. Deploy the server change to production

- Deploy with `mvnw clean deploy -Pprod` (see "Deploying to the Production Server"). The apps talk to `https://www.jukeanator.com`, so nothing works in them until production has the CORS change.

### 3. Build and test the Android app

- Follow "Installation/Setup" above.
- Test every screen against production: browsing, search, sign up, sign in, queueing songs, playlists, live queue updates, and the location picker.

### 4. Make the app work well on a phone

- App icon and splash screen: create one 1024x1024 source image and generate every size with the `@capacitor/assets` tool.
- Payments: test Add Funds inside the app. Card entry (Braintree Hosted Fields) should work as-is; PayPal, Venmo and Google Pay may need native Capacitor plugins, because they switch to other apps or open pop-up windows. Production Braintree keys must be set on the server first.
- Native features, which make the app more useful and help pass Apple's review (Apple rejects apps that are only a website in a wrapper): the Capacitor Geolocation plugin for the geo-fence, a barcode scanner for jukebox QR codes, and push notifications (for example "your song is up next").
- Optional: open email verification links in the app instead of the phone's browser (Android App Links and iOS Universal Links).

### 5. Decide how purchased credits are presented

- Apple and Google exempt payments for physical services from in-app purchase (Apple guideline 3.1.3(e)), which is why Braintree can be used instead, avoiding the 15 to 30 percent store commission.
- Credits are currently one balance per user, usable at any location. A reviewer may consider that virtual currency, which would require Apple and Google in-app purchase. Options: keep the global balance and explain that credits only buy song plays on physical jukeboxes at a venue, or tie purchases to the current location in the app.
- Before building the store submission, describe the model to Apple (App Review contact or a pre-submission question) and get an answer in writing.

### 6. Create the store accounts

- Google Play Console: 25 USD one-time, at play.google.com/console. A personal account must run a closed test with at least 12 testers for 14 days before the app can be published; an organization account does not, but needs a D-U-N-S number.
- Apple Developer Program: 99 USD per year, at developer.apple.com. Enrolling as an organization requires a D-U-N-S number (free, can take a week or more to issue); start this early.
- Both stores require a privacy policy URL (`https://www.jukeanator.com/privacy_policy.html` exists) and an in-app way to delete the account (already in the Profile screen).

### 7. Build the iPhone app on a Mac

- Requirements: a Mac running a recent macOS with Xcode from the Mac App Store. Apple requires uploads to be built with a current Xcode, and each Xcode release needs a minimum macOS version, so check the Mac's macOS version (Apple menu, About This Mac) against the Xcode requirements on developer.apple.com. An older Mac that cannot run a current macOS cannot be used for uploads; a cloud build service (for example Codemagic or Ionic Appflow) is the alternative.
- On the Mac: install Node.js (LTS installer from nodejs.org), copy or clone this repository, then from the `mobile` folder run `npm install`, `npx cap add ios` and `npm run ios`. Commit the generated `mobile/ios` folder.
- In Xcode, sign in with the Apple Developer account (Xcode, Settings, Accounts), select the team under Signing and Capabilities, and run the app on an iPhone connected by USB or on the simulator.

### 8. Prepare the store listings

- App name, short and full description, category (Music or Entertainment), screenshots for the required phone sizes, the app icon, the privacy policy URL, and a support URL.
- Store privacy questionnaires: Google's Data safety form and Apple's App Privacy section. Data collected includes email address, purchase history and, when the geo-fence is used, location.
- Content rating questionnaires in both consoles.
- Apple review notes: explain that purchases pay for song plays on a physical jukebox at a venue, processed by Braintree, and attach a short video showing a song bought in the app playing on the jukebox. Provide a demo account the reviewer can sign in with.

### 9. Build signed release versions and upload

- Android: in Android Studio, Build, Generate Signed App Bundle, Android App Bundle. Create the upload keystore when prompted and store it and its passwords somewhere safe outside git; losing it blocks future updates. Upload the `.aab` file to a closed testing track in the Play Console, then promote it to production after testing.
- iPhone: in Xcode, Product, Archive, then Distribute App to App Store Connect. Test with TestFlight, then submit for review in App Store Connect.
- For every later release, increase the version (Android `versionCode` and `versionName` in `mobile/android/app/build.gradle`; iOS version and build number in Xcode).

### 10. After approval

- Set `mobile-app.ios-store-url` and `mobile-app.android-store-url` in the production `application.yml` (`/var/lib/jukeanator/config/application.yml`) and restart the application. The `/get-app` QR codes on the home page and marquees then send iPhones and Android phones to the right store, and everyone else to `/app/`.
- Web UI changes reach app users only after a new app version is built and published (step 9), because the UI is bundled into the app. Server-only changes take effect immediately.
