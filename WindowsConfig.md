# Windows Configuration

This guide sets up a Windows PC to run JukeANator as a standalone or slave jukebox. For a Linux Mint PC, see [LinuxConfig.md](LinuxConfig.md). For master mode and the optional MySQL database, see [MasterConfig.md](MasterConfig.md). For building from source and running the tests, see [README.md](README.md).

The sections below are in the order they are normally done:

1. Prerequisite: Java 25
2. Prerequisite: media player
3. Install JukeANator
4. ELO ET2002L touchscreen (only if this touchscreen is used)
5. eGalax touchscreen (only if this touchscreen is used, for example in a converted TouchTunes Maestro)
6. Kiosk Mode Settings
7. Auto-Start JukeANator at Power-On
8. Network Security (before connecting to a location's Wi-Fi)

## Prerequisite: Java 25

The application needs a **Java 25** runtime (64-bit). The instructions below install Eclipse Temurin 25 (a free OpenJDK build from Adoptium). A full JRE is needed, not a "headless" one, because the kiosk UI uses Swing.

There are two ways to provide Java:

- **Installed Java (described below):** install Java 25 once and make it the default. Then remove the `set path=jdk-25.0.2\bin;%PATH%` line from `GO.CMD` (see "Install JukeANator").
- **Bundled JDK:** unzip a JDK 25 (the Temurin `.zip`, from the download page below) next to the WAR and keep that line in `GO.CMD`. The archive unpacks to a versioned folder such as `jdk-25.0.2+10`; rename it to `jdk-25.0.2`, or change the folder name in the script to match. Nothing has to be installed or made the default.

### Step 1: Download

Open https://adoptium.net/temurin/releases/?version=25 and choose **Operating System:** Windows, **Architecture:** x64, **Package Type:** JRE. Download the **.msi** installer.

### Step 2: Install

Run the `.msi`. On the **Custom Setup** page, make sure these features are set to **Will be installed on local hard drive**:

- **Add to PATH** (on by default)
- **Set JAVA_HOME variable** (off by default)
- **Associate .jar** (optional)

Finish the installer. It installs to a versioned folder such as `C:\Program Files\Eclipse Adoptium\jre-25.0.2.10-hotspot`.

### Step 3: Make it the default and verify

Open a **new** Command Prompt and run:

```
java -version
where java
```

`java -version` must report version 25. If it reports an older version, another Java is earlier in the `PATH`. `where java` lists them in the order Windows uses them. To fix it, press `Win+R`, run `rundll32 sysdm.cpl,EditEnvironmentVariables`, select **Path** under **System variables**, click **Edit**, and move the `Eclipse Adoptium\jre-25...\bin` entry to the top with **Move Up**. Also check that `JAVA_HOME` points to the Temurin 25 folder (without `\bin`). Open a new Command Prompt and check again.

## Prerequisite: media player

The application needs a media player installed before it can play songs. It is chosen with `song-player.player-type` in the external `application.yml`:

- `vlc`: VLC media player. The application drives VLC through its libraries (vlcj), so VLC 3.x must be installed with the same bitness as the JDK (64-bit). `detached-vlc` (a name carried over from older installs) behaves exactly like `vlc`.
- `winamp`: Winamp. `song-player.winamp-exe-path` must point to `winamp.exe`.

The bundled default is `winamp`.

If `player-type` is `vlc` but VLC cannot be loaded (for example, it is not installed, or only the 32-bit build is), the application switches to Winamp if it can find `winamp.exe`, and rewrites `player-type: winamp` and `winamp-exe-path` in the external `application.yml` so later starts use Winamp directly. After installing VLC, set `player-type` back to `vlc` to use it.

### VLC

1. Download the **64-bit** Windows installer of VLC 3.x from https://www.videolan.org/vlc/ (the 32-bit build cannot be loaded by a 64-bit JDK).
2. Run the installer with the default options, which install to `C:\Program Files\VideoLAN\VLC`, where the application finds it automatically.

Then, in `application.yml`, set:

```
song-player:
  player-type: vlc
```

### Winamp

1. Download the Winamp installer from https://www.winamp.com/ and run it with the default options. It installs to `C:\Program Files (x86)\Winamp`.
2. Start Winamp once, finish any first-run prompts, then close it.

Then, in `application.yml`, set (the path only needs changing if Winamp was installed elsewhere):

```
song-player:
  player-type: winamp
  winamp-exe-path: C:\\Program Files (x86)\\Winamp\\winamp.exe
```

## Install JukeANator

### Step 1: Get the WAR

Copy `jukeanator-engine-0.0.1-SNAPSHOT.war` to the directory the application will run from (for example `C:\kiosk\jukeanator`). To build it from source, run this in a Command Prompt in the project directory (no separate Maven install is needed; see [README.md](README.md) for details):

```
mvnw.cmd clean package -DskipTests
```

This creates `target\jukeanator-engine-0.0.1-SNAPSHOT.war`.

### Step 2: Set the environment variables (once)

Keys and secrets are never put in the launch script. Set them once as permanent environment variables for the account that runs the application, so copies of the script can be shared without exposing them.

Standalone and slave installs need these variables (master mode needs more; see [MasterConfig.md](MasterConfig.md)):

- `DISCOGS_CONSUMER_KEY`: Discogs API consumer key (song metadata / internet search)
- `DISCOGS_CONSUMER_SECRET`: Discogs API consumer secret
- `JWT_SECRET`: signing key for web login tokens, 32+ random characters (see "Generate a JWT secret" below). If unset, the app still runs but web logins do not survive a restart

A variable only takes effect if the external `application.yml` (in `--app.config-dir`) keeps that property in its `${VARIABLE:default}` form. A literal value written there overrides it.

**Generate a JWT secret.** Windows does not include `openssl`, so in a Command Prompt run:

```
powershell -Command "$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)"
```

It prints a random 64-character value to use as `JWT_SECRET`.

In a Command Prompt, run:

```
setx DISCOGS_CONSUMER_KEY "INSERT_VALUE_HERE"
setx DISCOGS_CONSUMER_SECRET "INSERT_VALUE_HERE"
setx JWT_SECRET "INSERT_VALUE_HERE"
```

This sets them for the current user. To set them for all users instead, run an **Administrator** Command Prompt and add `/M` to each command. The values apply only to programs started afterwards; check one in a new window with `echo %JWT_SECRET%`. They can also be edited in a dialog: press `Win+R` and run `rundll32 sysdm.cpl,EditEnvironmentVariables`.

### Step 3: Create GO.CMD

Create `GO.CMD` in the same directory as the WAR and the `jdk-25.0.2` folder:

```
cd /d "%~dp0"
set path=jdk-25.0.2\bin;%PATH%
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config --app.data-dir=C:\kiosk\data
```

The first line switches to the folder containing `GO.CMD`, so it also works when started from a shortcut or the Startup folder. Remove the second line if Java 25 is installed rather than bundled (see "Prerequisite: Java 25").

On first run the application seeds `C:\kiosk\config\application.yml` (with `app.data-dir` pre-set to `C:\kiosk\data`) if it does not already exist, then loads its settings from it. That is the external `application.yml` referred to throughout this guide.

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`) come *before* `-jar <file>`, and everything after the jar filename is passed to the application. Putting `--app.config-dir` before `-jar` makes the Java launcher reject it as an unrecognized option.

### Optional: MySQL database

By default the application stores its data as files under `--app.data-dir`. To store it in MySQL instead, see [MasterConfig.md](MasterConfig.md).

## ELO ET2002L touchscreen

The ET2002L is a 20" Elo desktop touch monitor. Its touch controller connects to the PC with either a **USB** cable or a **serial** (RS-232, 9-pin) cable; dual-interface controllers switch to USB only when a USB cable is plugged in. Connect only one of the two.

- **USB:** the screen appears as a standard HID touch device. No Elo driver is needed.
- **Serial:** Windows cannot detect it by itself, so the Elo serial driver is required.

Connect the monitor's video cable as well, and make the touchscreen the primary display if the PC has more than one.

### USB

1. Plug in the USB cable. Windows installs its built-in touch driver within a few seconds.
2. If touches land in the wrong place, open **Control Panel**, then **Tablet PC Settings** (or search the Start menu for "Calibrate the screen for pen or touch input"), and click **Calibrate**. On a PC with two monitors, click **Setup** there first and tap the touchscreen when asked, so Windows knows which display it belongs to.

### Serial

Current PCs have no serial (RS-232) port, so the touchscreen's serial cable is plugged into an RS-232 to USB adapter, and the adapter into a USB port on the PC.

1. Find the COM port: plug in the adapter, open **Device Manager** and expand **Ports (COM & LPT)**. The adapter appears as a new entry (often COM3 or higher). If it shows under **Other devices** with a warning icon instead, install the driver for the adapter's chip (FTDI, Prolific, Silicon Labs or WCH CH340) from the adapter maker's site.
2. Download the Elo **Windows serial touch driver** from Elo's support site, https://elosupport.elotouch.com/ (search for "Windows touch driver"), choosing the build for your Windows version.
3. Run the installer, choose **Serial** as the interface, and pick the COM port found in step 1. The controller runs at 9600 baud by default.
4. Reboot when prompted.
5. Run the **Elo Config** utility the driver installs, and use its **Align** (calibration) function: tap each target as it appears.

## eGalax touchscreen

Many unbranded touchscreens, including the one in a converted TouchTunes Maestro, use a touch controller made by EETI (eGalax). The controller board sits behind the screen and connects to the PC with a serial (RS-232, 9-pin) cable. Windows cannot detect a serial touchscreen by itself, so the eGalaxTouch driver is required.

Current PCs have no serial (RS-232) port, so the touchscreen's serial cable is plugged into an RS-232 to USB adapter, and the adapter into a USB port on the PC.

Connect the monitor's video cable as well, and make the touchscreen the primary display if the PC has more than one.

### Step 1: Connect the adapter

1. Plug the touchscreen's serial cable into the adapter, and the adapter into a USB port.
2. Find the COM port: open **Device Manager** and expand **Ports (COM & LPT)**. The adapter appears as a new entry (often COM3 or higher). If it shows under **Other devices** with a warning icon instead, install the driver for the adapter's chip (FTDI, Prolific, Silicon Labs or WCH CH340) from the adapter maker's site.

Keep the adapter in the same USB port from now on. Windows can give it a different COM number in another port, and the touchscreen then stops responding until the driver is pointed at the new port.

### Step 2: Install the eGalaxTouch driver

The driver is the eGalaxTouch package for single-touch devices, version 5.13 (folder `eGalaxTouch_5.13.0.14220-Release-150820-ForSingleTouchDevice`). A copy is kept in `C:\kiosk`; newer versions are available from the Downloads page of EETI's site, https://www.eeti.com/.

1. Run `setup.exe` from the driver folder as an administrator (right-click it, then **Run as administrator**).
2. Accept the licence. When the installer asks which interface drivers to install, select the **RS232** interface. Leave the other options at their defaults.
3. Accept the default install folder (`C:\Program Files\eGalaxTouch`) and finish the installer.
4. Reboot when prompted.

### Step 3: Find the controller

1. Open the **eGalaxTouch** utility (from the Start menu, or the desktop shortcut the installer creates).
2. On the **General** tab, the controller should be listed as a serial (RS232) touchscreen controller on the adapter's COM port. Tapping the screen should move the pointer, possibly to the wrong place until it is calibrated.
3. If no controller is listed, click **Add** (or **Search**) on the **General** tab and choose the adapter's COM port. The controller runs at 9600 baud by default.

If the controller is still not found, check in Device Manager that the adapter is present on the expected COM port, and that no other program (such as a modem or terminal program) has the port open.

### Step 4: Calibrate

1. In the **eGalaxTouch** utility, open the **Tools** tab and click **4 Points Calibration**.
2. Tap and hold each target until it moves to the next corner, without touching anywhere else on the screen.
3. If touches are still off near the edges, run **Linearization** (9 or 25 points) on the same tab after the 4-point calibration.

Use only the eGalaxTouch calibration. Do not also calibrate with Windows' own **Tablet PC Settings**, as the two adjustments add together and the result is wrong.

On a PC with two monitors, use the **Display** tab of the utility to tie the touchscreen to the jukebox display before calibrating.

## Kiosk Mode Settings

A jukebox must stay on and unlocked at all times. The application shows its own screensaver when idle, so Windows must never turn off the display, never start its own screensaver, never lock the screen after a timeout, and never sleep or hibernate. The steps below turn all of these off and sign the kiosk account in automatically after a reboot or power cut.

### Step 1: Use a local account (no Microsoft account)

Windows 11 pushes setup towards signing in with a Microsoft (Outlook) account. A kiosk does not need one; a local account works offline, has no online sign-in, and is easier to sign in automatically.

**New installation:** Microsoft keeps removing the setup workarounds for skipping the Microsoft account (such as `oobe\bypassnro`, and on recent builds `start ms-cxh:localonly`, both typed at the network screen after `Shift+F10`). They may still work on some builds, but the reliable method is to create the installation USB stick with Rufus (https://rufus.ie). After choosing the Windows 11 ISO and clicking **Start**, Rufus offers a **Windows User Experience** dialog: tick **Remove requirement for an online Microsoft account** and **Create a local account with username**, and enter the kiosk account name. Setup then creates that local account and never asks for a Microsoft account.

**Existing installation signed in with a Microsoft account:** add a separate local account for the jukebox and keep the Microsoft account as the administrator for maintenance. In an **Administrator** Command Prompt (choose your own name and password):

```
net user kiosk "INSERT_VALUE_HERE" /add
net user kiosk /passwordchg:no /expires:never
powershell -Command "Set-LocalUser -Name kiosk -PasswordNeverExpires $true"
```

The last command stops the password from expiring. Without it, Windows can expire the password after 42 days, and automatic sign-in (Step 6) then stops working. (`/expires:never` only controls when the account itself expires.)

The account can also be created in **Settings**, then **Accounts**, then **Other users**, then **Add account**: choose **I don't have this person's sign-in information**, then **Add a user without a Microsoft account**. Run the `powershell` command above afterwards as well.

Sign in as the kiosk account once, then do the remaining steps (and the environment variables under "Install JukeANator") while signed in as that account. When an Administrator Command Prompt is needed, Windows asks for the administrator account's password.

Do the steps below while signed in as the kiosk account. Run the commands in an **Administrator** Command Prompt, except those in Step 4: run those in a normal (not Administrator) Command Prompt. Step 4 changes `HKCU`, the settings of the user running the prompt, and an Administrator prompt opened from a standard account runs as the administrator account, so the change would be made to that account instead of the kiosk account.

### Step 2: Never turn off the display, sleep or hibernate

```
powercfg /change monitor-timeout-ac 0
powercfg /change monitor-timeout-dc 0
powercfg /change standby-timeout-ac 0
powercfg /change standby-timeout-dc 0
powercfg /change hibernate-timeout-ac 0
powercfg /change hibernate-timeout-dc 0
powercfg /hibernate off
```

A value of `0` means never. The `-dc` lines only matter on a PC with a battery. The same settings are in **Settings**, then **System**, then **Power** (set "Turn off my screen" and "Make my device sleep" to **Never**).

### Step 3: Never require a sign-in on wake

```
powercfg /setacvalueindex SCHEME_CURRENT SUB_NONE CONSOLELOCK 0
powercfg /setdcvalueindex SCHEME_CURRENT SUB_NONE CONSOLELOCK 0
powercfg /setactive SCHEME_CURRENT
```

Then, in **Settings**, then **Accounts**, then **Sign-in options**:

- Set "If you've been away, when should Windows require you to sign in again?" to **Never**.
- Turn off **Dynamic lock** (it locks the PC when a paired phone moves away).

### Step 4: Turn off the Windows screensaver

Run these in a normal (not Administrator) Command Prompt, signed in as the kiosk account:

```
reg add "HKCU\Control Panel\Desktop" /v ScreenSaveActive /t REG_SZ /d 0 /f
reg add "HKCU\Control Panel\Desktop" /v ScreenSaverIsSecure /t REG_SZ /d 0 /f
```

The same setting is in **Settings**, then **Personalization**, then **Lock screen**, then **Screen saver**: choose **(None)** and untick **On resume, display logon screen**.

### Step 5: Remove any inactivity lock policy

Windows can be told by policy to lock after a period of inactivity. It is not set on a normal home PC, but this makes sure it is off:

```
reg add "HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Policies\System" /v InactivityTimeoutSecs /t REG_DWORD /d 0 /f
```

### Step 6: Sign in automatically (skip the login screen)

So the jukebox comes back by itself after a reboot or power cut, sign the kiosk account in automatically. Either method below works; use one.

**Method A: netplwiz.** Windows 11 hides the option until passwordless sign-in is turned off. In **Settings**, then **Accounts**, then **Sign-in options**, turn off **For improved security, only allow Windows Hello sign-in for Microsoft accounts on this device**. If that switch is not shown, run this in an Administrator Command Prompt instead:

```
reg add "HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\PasswordLess\Device" /v DevicePasswordLessBuildVersion /t REG_DWORD /d 0 /f
```

Then press `Win+R`, run `netplwiz`, select the kiosk account, untick **Users must enter a user name and password to use this computer**, click **OK**, and enter the kiosk account's password twice.

**Method B: Microsoft Sysinternals Autologon.** Download Autologon from https://learn.microsoft.com/sysinternals/downloads/autologon and unzip it. Run `Autologon64.exe`, enter the kiosk account's user name, `.` (a dot) as the domain, and its password, and click **Enable**. It stores the password encrypted, and works even when the netplwiz checkbox is missing.

Reboot to confirm the PC goes straight to the kiosk account's desktop.

### Step 7: Suppress welcome screens and notifications

Windows periodically shows full-screen "Let's finish setting up your device" pages and pop-up notifications, which can cover the jukebox. In **Settings**, then **System**, then **Notifications**:

- Turn **Notifications** off.
- Under **Additional settings**, untick **Show the Windows welcome experience after updates and when signed in to show what's new and suggested**, **Suggest ways to get the most out of Windows and finish setting up this device**, and **Get tips and suggestions when using Windows**.

### Step 8: Stop automatic updates and restarts

Windows 11 has no switch in Settings that turns updates off permanently, but on **Windows 11 Pro** (and Enterprise or Education) the Windows Update policies can. With these settings Windows never downloads, installs or restarts for updates on its own; updates are installed only when someone clicks **Check for updates** in **Settings**, then **Windows Update**. In an Administrator Command Prompt:

```
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate\AU" /v NoAutoUpdate /t REG_DWORD /d 0 /f
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate\AU" /v AUOptions /t REG_DWORD /d 2 /f
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate\AU" /v NoAutoRebootWithLoggedOnUsers /t REG_DWORD /d 1 /f
reg add "HKLM\SOFTWARE\Policies\Microsoft\Windows\WindowsUpdate" /v ExcludeWUDriversInQualityUpdate /t REG_DWORD /d 1 /f
```

What they do:

- `AUOptions 2`: notify only; nothing is downloaded or installed until it is started manually.
- `NoAutoRebootWithLoggedOnUsers 1`: never restart automatically while an account is signed in (which, with automatic sign-in, is always).
- `ExcludeWUDriversInQualityUpdate 1`: Windows Update never replaces the drivers (graphics, touchscreen, audio) that are known to work.

To block updates completely instead of only notifying, change the first command to `/d 1` (`NoAutoUpdate 1`). The same settings are in the Group Policy Editor (`Win+R`, then `gpedit.msc`) under **Computer Configuration**, then **Administrative Templates**, then **Windows Components**, then **Windows Update**.

Restart the PC. The Windows Update page in **Settings** then shows **Some update policies are managed by your organization**, which confirms the policies are active. Also, in **Settings**, then **Windows Update**, then **Advanced options**, turn off **Get me up to date** and **Receive updates for other Microsoft products**, and turn off **Get the latest updates as soon as they're available** on the main Windows Update page.

**Windows 11 Home** may ignore these policies. On Home, use **Pause updates** (up to 5 weeks at a time) on the Windows Update page and set **Active hours** under **Advanced options** to cover the hours the jukebox is in use, or upgrade the PC to Pro.

Turning off automatic updates also stops security fixes, so install them by hand during a maintenance visit every month or two (Microsoft Defender virus definitions keep updating on their own and never need a restart).

### Verify

Reboot, then run:

```
powercfg /query SCHEME_CURRENT SUB_VIDEO VIDEOIDLE
powercfg /query SCHEME_CURRENT SUB_SLEEP STANDBYIDLE
```

Both must show `Current AC Power Setting Index: 0x00000000`.

## Auto-Start JukeANator at Power-On

With the steps below, switching the PC on ends with JukeANator running full screen on top of everything: the PC boots, signs the kiosk account in automatically (Step 6 under "Kiosk Mode Settings"), and the sign-in starts `GO.CMD`.

### Step 1: Power on after a power cut (BIOS/UEFI)

So the PC also starts when the cabinet's power is switched on or comes back after an outage, enter the BIOS/UEFI setup (usually by pressing `Del`, `F2` or `F10` right after power-on) and set the power-loss option to **Power On**. Its name depends on the manufacturer, for example **Restore on AC Power Loss**, **AC Back**, **After Power Failure** or **AC Recovery**. Save and exit.

### Step 2: Keep the jukebox window on top

The application already fills the screen in full-screen mode, and by default it also keeps other windows from appearing over it. That is controlled by this setting, which is `true` by default. Check that the external `application.yml` (in `--app.config-dir`) does not set it to `false`, which can be the case in a copy seeded by an older version:

```
user-interface:
  always-on-top: true
```

### Step 3: Start GO.CMD at sign-in

Add a shortcut to `GO.CMD` in the kiosk account's Startup folder, while signed in as the kiosk account:

- Press `Win+R`, type `shell:startup` and press Enter. The Startup folder opens.
- Right-click inside it, choose **New**, then **Shortcut**, click **Browse**, select `GO.CMD`, click **Next**, name it `JukeANator`, and click **Finish**.
- Right-click the new shortcut, choose **Properties**, set **Run** to **Minimized**, and click **OK**. The console window then starts minimized on the taskbar instead of in front of the jukebox (its log output is still there for troubleshooting).

Check that the entry is enabled in **Settings**, then **Apps**, then **Startup** (it is listed as `JukeANator` or `GO`). Windows starts Startup folder items a few seconds after the desktop appears.

The `cd /d "%~dp0"` line at the top of `GO.CMD` (see "Install JukeANator") is required here, because programs started from the Startup folder do not run in the folder that holds the WAR.

### Verify

Switch the PC off at the wall or cabinet switch, then switch it on again. Without touching anything, it must boot, sign in, and show JukeANator full screen.

## Network Security

At a location the jukebox is usually on the bar's Wi-Fi, often an open guest network shared with anyone nearby. The jukebox never needs to accept a connection from that network: it only makes outgoing connections, to the master (`app.master-instance-url`) and the license server (`license.server-url`), the same way a web browser does. Patrons use the Web/Mobile UI on the master, not on the jukebox. The steps below make the PC accept nothing from the network. Do them before the jukebox is connected to a location's Wi-Fi.

### Step 1: Mark the Wi-Fi network as Public

Windows treats a **Public** network as untrusted: network discovery and file and printer sharing are off, and Windows Firewall blocks incoming connections. In **Settings**, then **Network & internet**, then **Wi-Fi**, click the network's properties and set **Network profile type** to **Public network**. Do this for every Wi-Fi network the jukebox joins. To check it, run this in PowerShell; every network must show `Public`:

```
Get-NetConnectionProfile | Select-Object Name, NetworkCategory
```

### Step 2: Accept connections from this PC only

By default the application listens on every network connection (port 8080). Add these lines to the end of the external `application.yml` (in `--app.config-dir`), then restart the application:

```
server:
  address: 127.0.0.1
```

The jukebox screen talks to the application inside the same program, so nothing else needs port 8080. The templates [docs/application-slave-mode.yml](docs/application-slave-mode.yml) and [docs/application-standalone-mode.yml](docs/application-standalone-mode.yml) already contain these lines. Leave them out only for a standalone jukebox whose Web/Mobile UI must be opened from phones on the location's own network.

### Step 3: Make MySQL local-only (only if MySQL is installed)

The MySQL installer listens on every network connection and adds firewall rules (`Port 3306` and `Port 33060`) that allow incoming connections on all networks, including public ones. Skip this step if the application stores its data as files (no MySQL).

Open the MySQL settings file in Notepad as Administrator. It is `my.ini` in `C:\ProgramData\MySQL\MySQL Server 8.4` (or `MySQL Server 8.0` for an older install; `C:\ProgramData` is hidden, so type the path into the address bar). Under the `[mysqld]` line, add:

```
bind-address=127.0.0.1
mysqlx-bind-address=127.0.0.1
```

Save the file. Then, in an **Administrator** PowerShell, find the MySQL service name (`MySQL84` for 8.4, `MySQL80` for 8.0), restart it, and remove the two firewall rules:

```
Get-Service MySQL*
Restart-Service MySQL84
Get-NetFirewallRule -DisplayName "Port 3306", "Port 33060" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
```

Use the service name the first command printed in the second command.

### Step 4: Remove the Java firewall exceptions

When a Java program first listens on the network, Windows asks whether to allow it, and clicking **Allow** creates inbound firewall rules named **Java(TM) Platform SE binary** or **OpenJDK Platform binary**. After Step 2 they are not needed. In an **Administrator** PowerShell:

```
Get-NetFirewallRule -Direction Inbound | Where-Object { $_.DisplayName -in "Java(TM) Platform SE binary", "OpenJDK Platform binary" } | Remove-NetFirewallRule
```

If Windows asks again whether to allow Java on a network, click **Cancel**.

### Step 5: Turn off Remote Desktop

In **Settings**, then **System**, then **Remote Desktop**, make sure **Remote Desktop** is **Off**. File and printer sharing is already blocked on a Public network (Step 1).

### Step 6: Use HTTPS to reach the master

On an open network, others nearby can read unencrypted traffic, including the jukebox's location API key. Once the master has its HTTPS certificate, set the master address in the external `application.yml` to its `https://` form:

```
app:
  master-instance-url: https://www.jukeanator.com
```

Never use an `http://` master address on a location's network. `license.server-url` is already `https://www.jukeanator.com` by default.

### Step 7: Keep the Wi-Fi connection up

- Connect the jukebox to a network without a sign-in ("accept the terms") page. The jukebox cannot click through one, and many networks show it again every few days. A staff network, a network just for the jukebox, or a small 4G/5G router dedicated to the jukebox is more dependable than an open guest network.
- When joining the network, tick **Connect automatically**.
- In **Device Manager**, open **Network adapters**, double-click the Wi-Fi adapter, and on the **Power Management** tab untick **Allow the computer to turn off this device to save power**.

The application reconnects to the master on its own after the connection drops.

### Step 8: Keep up with security updates

"Kiosk Mode Settings", Step 8 stops automatic Windows updates. On a public network, installing security updates by hand every month or two matters more. Leave Microsoft Defender on.

### Verify

With the application running, run this in a Command Prompt:

```
netstat -ano | findstr LISTENING | findstr ":8080 :3306 :33060"
```

Every line must show `127.0.0.1` (or `[::1]`) as the local address, never `0.0.0.0` or `[::]`.
