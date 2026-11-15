# Read Me First
The following was discovered as part of building this project:

* The original package name 'com.djt.jukeanator-engine' is invalid and this project uses 'com.djt.jukeanator_engine' instead.

# Building/Running the Application
```
mvn clean package -DskipTests
```

Keys and secrets are never put in the launch script. Set them once as permanent environment variables for the account that runs the application, so copies of the script can be shared without exposing them.

Standalone and slave installs need these variables (master mode needs more -- see [Master Mode environment variables](#master-mode-environment-variables)):

- `DISCOGS_CONSUMER_KEY`: Discogs API consumer key (song metadata / internet search)
- `DISCOGS_CONSUMER_SECRET`: Discogs API consumer secret
- `JWT_SECRET`: signing key for web login tokens, 32+ random characters (generate one with
  `openssl rand -base64 48`). If unset, the app still runs but web logins do not survive a restart

A variable only takes effect if the external `application.yml` (in `--app.config-dir`) keeps that property in its `${VARIABLE:default}` form. A literal value written there overrides it.

## Windows

### Step 1: Set the environment variables (once)

In a Command Prompt, run:

```
setx DISCOGS_CONSUMER_KEY "INSERT_VALUE_HERE"
setx DISCOGS_CONSUMER_SECRET "INSERT_VALUE_HERE"
setx JWT_SECRET "INSERT_VALUE_HERE"
```

This sets them for the current user. To set them for all users instead, run an **Administrator** Command Prompt and add `/M` to each command. The values apply only to programs started afterwards; check one in a new window with `echo %JWT_SECRET%`. They can also be edited in a dialog: `Win+R` -> `rundll32 sysdm.cpl,EditEnvironmentVariables`.

### Step 2: Create GO.CMD

Create `GO.CMD` in the same directory as the WAR and the `jdk-25.0.2` folder:

```
cd /d "%~dp0"
set path=jdk-25.0.2\bin;%PATH%
set classpath=.;
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config --app.data-dir=C:\kiosk\data
```

The first line switches to the folder containing `GO.CMD`, so it also works when started from a shortcut or the Startup folder.

## Linux (Mint)

### Step 1: Set the environment variables (once)

Open `~/.profile` in a text editor (`nano ~/.profile`) and add at the end:

```
# JukeANator
export DISCOGS_CONSUMER_KEY="INSERT_VALUE_HERE"
export DISCOGS_CONSUMER_SECRET="INSERT_VALUE_HERE"
export JWT_SECRET="INSERT_VALUE_HERE"
```

Then run `chmod 600 ~/.profile` so other accounts cannot read it, and log out and back in. Check one in a new terminal with `echo "$JWT_SECRET"`. (Use an editor rather than `echo ... >> ~/.profile`, which leaves the secret in the shell history. If the app runs as a systemd service, put the same lines without `export` in a `chmod 600` file and reference it with `EnvironmentFile=` in the unit.)

### Step 2: Create go.sh

Create `go.sh` in the same directory as the WAR and the `jdk-25.0.2` folder:

```
#!/usr/bin/env bash
# One-time step to make this script executable:  chmod +x go.sh
# Then launch it with:                           ./go.sh

# Run from the directory containing this script (and the WAR / JDK)
cd "$(dirname "$0")"

export PATH="$PWD/jdk-25.0.2/bin:$PATH"
exec java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir="$HOME/kiosk/config" --app.data-dir="$HOME/kiosk/data"
```

Then make it executable with `chmod +x go.sh`.

# Prerequisite: Java 25

The application needs a **Java 25** runtime (64-bit). The instructions below install Eclipse Temurin 25 (a free OpenJDK build from Adoptium) on Windows, and the distribution's OpenJDK 25 package on Linux Mint. A full JRE is needed, not a "headless" one, because the kiosk UI uses Swing.

There are two ways to provide Java:

- **Installed Java (described below):** install Java 25 once and make it the default. Then remove the `set path=jdk-25.0.2\bin;%PATH%` line from `GO.CMD`, or the `export PATH="$PWD/jdk-25.0.2/bin:$PATH"` line from `go.sh`.
- **Bundled JDK:** unzip a JDK 25 into a `jdk-25.0.2` folder next to the WAR and keep those lines in the scripts. Nothing has to be installed or made the default.

## Windows

### Step 1: Download

Open https://adoptium.net/temurin/releases/?version=25 and choose **Operating System:** Windows, **Architecture:** x64, **Package Type:** JRE. Download the **.msi** installer.

### Step 2: Install

Run the `.msi`. On the **Custom Setup** page, make sure these features are set to **Will be installed on local hard drive**:

- **Add to PATH**
- **Set JAVA_HOME variable** (off by default)
- **Associate .jar** (optional)

Finish the installer. It installs to `C:\Program Files\Eclipse Adoptium\jre-25...`.

### Step 3: Make it the default and verify

Open a **new** Command Prompt and run:

```
java -version
where java
```

`java -version` must report version 25. If it reports an older version, another Java is earlier in the `PATH`. `where java` lists them in the order Windows uses them. To fix it, press `Win+R`, run `rundll32 sysdm.cpl,EditEnvironmentVariables`, select **Path** under **System variables**, click **Edit**, and move the `Eclipse Adoptium\jre-25...\bin` entry to the top with **Move Up**. Also check that `JAVA_HOME` points to the Temurin 25 folder (without `\bin`). Open a new Command Prompt and check again.

## Linux (Mint)

Install OpenJDK 25 from the Mint repositories:

```
sudo apt update && sudo apt install openjdk-25-jre
```

# Prerequisite: media player

The application needs a media player installed before it can play songs. It is chosen with `song-player.player-type` in the external `application.yml`:

- `vlc` or `detached-vlc`: VLC media player, on any operating system. The application drives VLC through its libraries (vlcj), so VLC 3.x must be installed with the same bitness as the JDK (64-bit).
- `winamp`: Winamp, Windows only. `song-player.winamp-exe-path` must point to `winamp.exe`.

The bundled default is `winamp`. On Linux, change it to `vlc`, otherwise no song plays.

## Windows

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

## Linux (Mint)

Install VLC from the Mint repositories (not the Flatpak or Snap version: those are sandboxed and their libraries cannot be loaded by the application):

```
sudo apt update
sudo apt install vlc
```

Check it with `vlc --version` (it should report 3.x). Then, in `application.yml`, set:

```
song-player:
  player-type: vlc
```

# ELO ET2002L touchscreen

The ET2002L is a 20" Elo desktop touch monitor. Its touch controller connects to the PC with either a **USB** cable or a **serial** (RS-232, 9-pin) cable; dual-interface controllers switch to USB only when a USB cable is plugged in. Connect only one of the two.

- **USB:** the screen appears as a standard HID touch device. No Elo driver is needed on Windows or Linux Mint.
- **Serial:** the operating system cannot detect it by itself. On Windows the Elo serial driver is required; on Linux Mint either the built-in kernel driver (recommended below) or the Elo Linux serial driver is used.

Connect the monitor's video cable as well, and make the touchscreen the primary display if the PC has more than one.

## Windows

### USB

1. Plug in the USB cable. Windows installs its built-in touch driver within a few seconds.
2. If touches land in the wrong place, open **Control Panel**, then **Tablet PC Settings**, and click **Calibrate**. On a PC with two monitors, click **Setup** there first and tap the touchscreen when asked, so Windows knows which display it belongs to.

### Serial

1. Download the Elo **Windows serial touch driver** from Elo's support site, https://elosupport.elotouch.com/ (search for "Windows touch driver"), choosing the build for your Windows version.
2. Run the installer, choose **Serial** as the interface, and pick the COM port the cable is plugged into (usually COM1). The controller always runs at 9600 baud.
3. Reboot when prompted.
4. Run the **Elo Config** utility the driver installs, and use its **Align** (calibration) function: tap each target as it appears.

## Linux (Mint)

These steps assume the default Cinnamon desktop on X11. Install the tools used below once:

```
sudo apt update
sudo apt install xinput xinput-calibrator xserver-xorg-input-evdev evtest
```

### Step 1: Connect the touch controller

**USB:** plug in the USB cable and check that the kernel found it (Elo's USB vendor id is `04e7`):

```
lsusb | grep -i 04e7
```

Tapping the screen should already move the mouse pointer. Skip to Step 2.

**Serial:** the kernel's `elo` driver handles Elo serial controllers but has to be attached to the serial port. Find the port (usually `/dev/ttyS0`; a USB-to-serial adapter shows up as `/dev/ttyUSB0`):

```
sudo dmesg | grep -i tty
```

Install `inputattach` and test the connection (stop it with `Ctrl+C` afterwards):

```
sudo apt install inputattach
sudo inputattach --elotouch /dev/ttyS0
```

While it runs, tapping the screen should move the pointer. If nothing happens, try the other Elo protocols: `--elo4b`, `--elo6b` or `--elo3b` in place of `--elotouch`.

To attach it automatically at every boot, create a systemd service:

```
sudo nano /etc/systemd/system/elo-touch.service
```

```
[Unit]
Description=Elo serial touchscreen
After=multi-user.target

[Service]
Type=simple
ExecStart=/usr/bin/inputattach --elotouch /dev/ttyS0
Restart=always

[Install]
WantedBy=multi-user.target
```

Use the port and protocol option that worked in the test, then enable and start it:

```
sudo systemctl enable --now elo-touch.service
```

(Alternatively, Elo publishes its own Linux serial driver with a calibration tool: see Elo's Linux driver downloads at https://elosupport.elotouch.com/hc/en-us/articles/33699338540183-Linux-Touch-Drivers and follow the installation instructions included with it. Use either that driver or `inputattach`, not both.)

### Step 2: Check the device name

```
xinput list
```

Note the name of the Elo entry (for example `Elo TouchSystems ...` or `Elo Serial TouchScreen`). If the screen does not respond at all, `sudo evtest` lists the input devices and shows whether touches arrive.

### Step 3: Use the evdev driver for the touchscreen

Mint's default input driver ignores the calibration that `xinput_calibrator` produces, so assign the touchscreen to the `evdev` driver instead:

```
sudo mkdir -p /etc/X11/xorg.conf.d
sudo nano /etc/X11/xorg.conf.d/99-elo-touch.conf
```

```
Section "InputClass"
    Identifier "Elo touchscreen"
    MatchProduct "Elo"
    MatchDevicePath "/dev/input/event*"
    Driver "evdev"
EndSection
```

Log out and back in (or reboot).

### Step 4: Calibrate

```
xinput_calibrator
```

Tap each of the four targets as it appears. When it finishes, the tool prints a `Section "InputClass" ... EndSection` block. Add the `Option "Calibration" ...` line it prints (and `Option "SwapAxes" ...` if it prints one) to the block in `/etc/X11/xorg.conf.d/99-elo-touch.conf`, so it reads for example:

```
Section "InputClass"
    Identifier "Elo touchscreen"
    MatchProduct "Elo"
    MatchDevicePath "/dev/input/event*"
    Driver "evdev"
    Option "Calibration" "3929 167 3864 185"
    Option "SwapAxes" "0"
EndSection
```

Log out and back in (or reboot); the calibration then applies at every login. If the X or Y direction is reversed, add `Option "InvertX" "on"` or `Option "InvertY" "on"`, or rerun `xinput_calibrator`.

### Step 5: Two monitors (only if the PC has more than one display)

Touches are spread over all displays until the touchscreen is tied to its own output. Find the output name (for example `HDMI-1`):

```
xrandr --listmonitors
```

Then map the touch device to it, using the device name from Step 2:

```
xinput map-to-output "Elo TouchSystems ..." HDMI-1
```

This lasts until logout. To apply it at every login, add the same command in **Menu**, then **Startup Applications** (click **+**, then **Custom command**, and set a delay of a few seconds).

# Kiosk Mode Settings

A jukebox must stay on and unlocked at all times. The application shows its own screensaver when idle, so the operating system must never turn off the display, never start its own screensaver, never lock the screen after a timeout, and never sleep or hibernate. The steps below turn all of these off and sign the kiosk account in automatically after a reboot or power cut.

## Windows

### Step 1: Use a local account (no Microsoft account)

Windows 11 pushes setup towards signing in with a Microsoft (Outlook) account. A kiosk does not need one; a local account works offline, has no online sign-in, and is easier to sign in automatically.

**New installation:** Microsoft keeps removing the setup workarounds for skipping the Microsoft account (such as `oobe\bypassnro`, and on recent builds `start ms-cxh:localonly`, both typed at the network screen after `Shift+F10`). They may still work on some builds, but the reliable method is to create the installation USB stick with Rufus (https://rufus.ie). After choosing the Windows 11 ISO and clicking **Start**, Rufus offers a **Windows User Experience** dialog: tick **Remove requirement for an online Microsoft account** and **Create a local account with username**, and enter the kiosk account name. Setup then creates that local account and never asks for a Microsoft account.

**Existing installation signed in with a Microsoft account:** add a separate local account for the jukebox and keep the Microsoft account as the administrator for maintenance. In an **Administrator** Command Prompt (choose your own name and password):

```
net user kiosk "INSERT_VALUE_HERE" /add
net user kiosk /passwordchg:no /expires:never
```

The same can be done in **Settings**, then **Accounts**, then **Other users**, then **Add account**: choose **I don't have this person's sign-in information**, then **Add a user without a Microsoft account**.

Sign in as the kiosk account once, then do the remaining steps (and the environment variables under "Building/Running the Application") while signed in as that account. When an Administrator Command Prompt is needed, Windows asks for the administrator account's password.

Run the commands in the steps below in an **Administrator** Command Prompt while signed in as the kiosk account (the screensaver settings are per user).

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

**Method B: Microsoft Sysinternals Autologon.** Download Autologon from https://learn.microsoft.com/sysinternals/downloads/autologon , run `Autologon64.exe`, enter the kiosk account's user name, `.` (a dot) as the domain, and its password, and click **Enable**. It stores the password encrypted, and works even when the netplwiz checkbox is missing.

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

Restart the PC. **Settings**, then **Windows Update**, then shows **Some update policies are managed by your organization**, which confirms the policies are active. Also, in **Settings**, then **Windows Update**, then **Advanced options**, turn off **Get me up to date** and **Receive updates for other Microsoft products**, and turn off **Get the latest updates as soon as they're available** on the main Windows Update page.

**Windows 11 Home** may ignore these policies. On Home, use **Pause updates** (up to 5 weeks at a time) on the Windows Update page and set **Active hours** under **Advanced options** to cover the hours the jukebox is in use, or upgrade the PC to Pro.

Turning off automatic updates also stops security fixes, so install them by hand during a maintenance visit every month or two (Microsoft Defender virus definitions keep updating on their own and never need a restart).

### Verify

Reboot, then run:

```
powercfg /query SCHEME_CURRENT SUB_VIDEO VIDEOIDLE
powercfg /query SCHEME_CURRENT SUB_SLEEP STANDBYIDLE
```

Both must show `Current AC Power Setting Index: 0x00000000`.

## Linux (Mint)

These steps assume the default Cinnamon desktop on X11. They work in three layers: Cinnamon's own idle and power settings, the X11 display power management (DPMS) underneath it, and systemd suspend as the final safety net.

### Step 1: Cinnamon idle, screensaver and power settings

Run these as the account that runs the application, **without** `sudo` (they are per-user settings):

```
gsettings set org.cinnamon.desktop.session idle-delay 0
gsettings set org.cinnamon.desktop.screensaver lock-enabled false
gsettings set org.cinnamon.settings-daemon.plugins.power sleep-display-ac 0
gsettings set org.cinnamon.settings-daemon.plugins.power sleep-inactive-ac-timeout 0
gsettings set org.cinnamon.settings-daemon.plugins.power sleep-inactive-ac-type 'nothing'
gsettings set org.cinnamon.settings-daemon.plugins.power lock-on-suspend false
gsettings set org.cinnamon.settings-daemon.plugins.power idle-dim-battery false
```

What they do:

- `idle-delay 0`: the session is never considered idle, so the Cinnamon screensaver and lock never start.
- `lock-enabled false`: the screen is never locked.
- `sleep-display-ac 0`: the display is never turned off on mains power.
- `sleep-inactive-ac-timeout 0` and `sleep-inactive-ac-type 'nothing'`: the PC never suspends when idle.
- `lock-on-suspend false`: no lock screen if the PC is ever woken from sleep.

The same settings are in **System Settings**, then **Screensaver** (set "Delay before starting the screensaver" to **Never** and turn off both lock options) and **System Settings**, then **Power Management** (set "Turn off the screen when inactive" and "Suspend when inactive" to **Never**).

### Step 2: Disable X11 screen blanking and DPMS system-wide

This covers anything Cinnamon misses, including the login screen:

```
sudo mkdir -p /etc/X11/xorg.conf.d
sudo nano /etc/X11/xorg.conf.d/10-no-blanking.conf
```

```
Section "ServerFlags"
    Option "BlankTime"   "0"
    Option "StandbyTime" "0"
    Option "SuspendTime" "0"
    Option "OffTime"     "0"
EndSection

Section "Extensions"
    Option "DPMS" "Disable"
EndSection
```

As an extra safeguard, add a startup entry in **Menu**, then **Startup Applications** (click **+**, then **Custom command**) with this command:

```
sh -c "sleep 10; xset s off; xset s noblank; xset -dpms"
```

### Step 3: Block suspend and hibernate at the system level

```
sudo systemctl mask sleep.target suspend.target hibernate.target hybrid-sleep.target
```

Nothing can then put the PC to sleep: not an idle timer, the menu, or a stray power-key event. To undo it later, run the same command with `unmask` in place of `mask`.

### Step 4 (optional): Sign in automatically

So the jukebox comes back by itself after a reboot, skip the login screen and sign the kiosk account in automatically. Either use the GUI or edit the configuration file.

**GUI:** open **System Settings**, then **Login Window**, then **Users**, set **Automatic login** to the kiosk account, and leave the delay at 0.

**Configuration file:** open the LightDM configuration:

```
sudo nano /etc/lightdm/lightdm.conf
```

Under the `[Seat:*]` section (add the section if it is missing), add these lines, using the account's user name in place of `kiosk`:

```
[Seat:*]
autologin-user=kiosk
autologin-user-timeout=0
```

Reboot to confirm the PC goes straight to the desktop. The account keeps its password for `sudo` and remote (SSH) access.

**Avoid the keyring prompt:** with automatic sign-in, no password is typed, so the account's login keyring (where saved passwords are kept) stays locked. Anything that reads a saved password, such as a saved Wi-Fi password or a web browser, then shows an "Unlock login keyring" prompt over the jukebox screen. To prevent it:

- **Wi-Fi:** open the network's settings, and on the **Details** tab tick **Make available to other users**. The password is then stored system-wide instead of in the keyring.
- **Keyring in general:** open **Passwords and Keys** (Seahorse), right-click the **Login** keyring, choose **Change Password**, enter the current password, and leave the new password blank. The keyring then never needs unlocking. Saved passwords are then stored unencrypted on disk, which is acceptable for a PC in a locked cabinet.

### Verify

Reboot, then run:

```
xset q
gsettings get org.cinnamon.desktop.session idle-delay
```

`xset q` must show `timeout:  0` under **Screen Saver** and `DPMS is Disabled` under **DPMS**, and the second command must print `uint32 0`.

If the session is ever switched to Wayland at the login screen, Step 2 and the `xset` startup entry have no effect; Steps 1 and 3 still apply.

# Auto-Start JukeANator at Power-On

With the steps below, switching the PC on ends with JukeANator running full screen on top of everything: the PC boots, signs the kiosk account in automatically (Step 6 for Windows or Step 4 for Linux Mint under Kiosk Mode Settings), and the sign-in starts `GO.CMD` or `go.sh`.

## Step 1: Power on after a power cut (BIOS/UEFI)

So the PC also starts when the cabinet's power is switched on or comes back after an outage, enter the BIOS/UEFI setup (usually by pressing `Del`, `F2` or `F10` right after power-on) and set the power-loss option to **Power On**. Its name depends on the manufacturer, for example **Restore on AC Power Loss**, **AC Back**, **After Power Failure** or **AC Recovery**. Save and exit.

## Step 2: Keep the jukebox window on top

The application already fills the screen in full-screen mode, and by default it also keeps other windows from appearing over it. That is controlled by this setting, which is `true` by default. Check that the external `application.yml` (in `--app.config-dir`) does not set it to `false`, which can be the case in a copy seeded by an older version:

```
user-interface:
  always-on-top: true
```

## Windows

Add a shortcut to `GO.CMD` in the kiosk account's Startup folder, while signed in as the kiosk account:

- Press `Win+R`, type `shell:startup` and press Enter. The Startup folder opens.
- Right-click inside it, choose **New**, then **Shortcut**, click **Browse**, select `GO.CMD`, click **Next**, name it `JukeANator`, and click **Finish**.
- Right-click the new shortcut, choose **Properties**, set **Run** to **Minimized**, and click **OK**. The console window then starts minimized on the taskbar instead of in front of the jukebox (its log output is still there for troubleshooting).

Check that the entry is enabled in **Settings**, then **Apps**, then **Startup** (it is listed as `JukeANator` or `GO`). Windows starts Startup folder items a few seconds after the desktop appears.

The `cd /d "%~dp0"` line at the top of `GO.CMD` (see "Building/Running the Application") is required here, because programs started from the Startup folder do not run in the folder that holds the WAR.

## Linux (Mint)

Add `go.sh` to the kiosk account's autostart entries, while signed in as the kiosk account. Either use **Menu**, then **Startup Applications**, click **+**, then **Custom command**, enter the full path to `go.sh` as the command (for example `/home/kiosk/jukeanator/go.sh`; `~` is not expanded here), and set **Startup delay** to 10 seconds. Or create the entry file directly:

```
mkdir -p ~/.config/autostart
nano ~/.config/autostart/jukeanator.desktop
```

```
[Desktop Entry]
Type=Application
Name=JukeANator
Exec=/home/kiosk/jukeanator/go.sh
X-GNOME-Autostart-enabled=true
X-GNOME-Autostart-Delay=10
```

Use the real path to `go.sh` on the `Exec=` line. `go.sh` must be executable (`chmod +x go.sh`). The 10-second delay gives the sound system and the display settings time to start before the application does. The environment variables in `~/.profile` are loaded at sign-in, so the application started this way sees them.

## Verify

Switch the PC off at the wall or cabinet switch, then switch it on again. Without touching anything, it must boot, sign in, and show JukeANator full screen.

# Optional: MySQL database

By default the application stores its data as files under `--app.data-dir` (`app.repository-type: filesystem`). It can store it in MySQL instead (`app.repository-type: jpa`); master mode always uses MySQL. MySQL 8.0 or later is required (the schema uses the `utf8mb4_0900_ai_ci` collation); 8.4 LTS is recommended.

The application creates and updates its tables itself (Flyway, on startup), so only an empty database and a user are needed. It does not copy existing filesystem data into MySQL: after switching, the database starts empty.

## Windows

### Step 1: Download

Open https://dev.mysql.com/downloads/mysql/ , select version **8.4 LTS** and **Microsoft Windows**, and download the **MSI Installer** (x64). The "No thanks, just start my download" link skips the Oracle account sign-up.

### Step 2: Install and configure

Run the `.msi` and choose the **Typical** setup. At the end, leave **Run MySQL Configurator** ticked and click **Finish**. In the configurator:

- **Type and Networking:** keep **Development Computer** (or **Server Computer** for a master), port **3306**.
- **Accounts and Roles:** set a strong password for the MySQL `root` account and keep it somewhere safe.
- **Windows Service:** keep **Configure MySQL Server as a Windows Service** and **Start the MySQL Server at System Startup** ticked.
- Click **Execute**, then **Finish**.

### Step 3: Create the database and user

Open **MySQL 8.4 Command Line Client** from the Start menu, enter the `root` password, and run (choose your own password in place of `INSERT_VALUE_HERE`):

```
CREATE DATABASE IF NOT EXISTS jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### Step 4: Set the password environment variable

In a Command Prompt, set the same password, then open a new Command Prompt (as in Step 1 of "Building/Running the Application"):

```
setx DB_PASSWORD "INSERT_VALUE_HERE"
```

`DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`) and `DB_USERNAME` (default `jukeanator`) only need setting if MySQL runs on another host or port, or a different database or user name is used.

## Linux (Mint)

### Step 1: Install

```
sudo apt update
sudo apt install mysql-server
```

The service starts automatically and is enabled at boot. Check it with `systemctl status mysql`.

### Step 2: Secure the installation

```
sudo mysql_secure_installation
```

Answer **Y** to removing anonymous users, disallowing remote root login, and removing the test database. On Mint, the MySQL `root` account signs in through `sudo` rather than a password.

### Step 3: Create the database and user

Open the MySQL prompt:

```
sudo mysql
```

and run (choose your own password in place of `INSERT_VALUE_HERE`):

```
CREATE DATABASE IF NOT EXISTS jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'INSERT_VALUE_HERE';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';
FLUSH PRIVILEGES;
EXIT;
```

### Step 4: Set the password environment variable

Add this line to `~/.profile` with the same password, then log out and back in (as in Step 1 of "Building/Running the Application"):

```
export DB_PASSWORD="INSERT_VALUE_HERE"
```

`DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`) and `DB_USERNAME` (default `jukeanator`) only need setting if MySQL runs on another host or port, or a different database or user name is used.

## Switch the application to MySQL

In the external `application.yml` (in `--app.config-dir`), set:

```
app:
  repository-type: jpa
```

Restart the application. On the first start it creates the tables; the log shows Flyway applying `V1__init_schema`. To go back to file storage, set `repository-type: filesystem` again.

To check the connection without the application, sign in as the new user and list the tables (after the first start). On Windows, run it from `C:\Program Files\MySQL\MySQL Server 8.4\bin`:

```
mysql -u jukeanator -p jukeanator -e "SHOW TABLES;"
```

# To Start for Claude Code
```
mvnw spring-boot:run -DskipTests "-Dspring-boot.run.arguments=--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config"
```

Note: JVM options (`-D...`, `--enable-native-access=...`, etc.) must come *before* `-jar <file>`; anything after the jar filename is passed to the application instead.

## Externalized config (e.g. kiosk deployments)

By default the app seeds a `config/application.yml` (and sibling `data/`) next to the WAR on first run and reads overrides from there. To point it at a config directory somewhere else instead, pass `--app.config-dir` as a program argument (after the jar filename):

```
java --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 -jar jukeanator-engine-0.0.1-SNAPSHOT.war --app.config-dir=C:\kiosk\config

or, for Eclipse launch configurations, specify these in program arguments:

--app.data-dir=C:\kiosk\data --app.config-dir=C:\kiosk\config
```

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`, etc.) come *before* `-jar <file>`; everything after the jar filename is passed to the application instead -- putting `--app.config-dir` before `-jar` makes the JVM launcher itself reject it as an unrecognized option.

On first run this seeds `C:\kiosk\config\application.yml` (with `app.data-dir` pre-wired to a sibling `C:\kiosk\data`) if it doesn't already exist, then loads overrides from it -- same seeding behavior as the WAR-relative default, just rooted at the given directory instead.

# How to run the JFC/Swing GUI Functional Tests
```
mvn test -Pgui-tests
```

# Master Mode

## Master Mode environment variables

Set these permanently, the same way as the standalone/slave variables (step 1 under [Windows](#windows) or [Linux (Mint)](#linux-mint)). Variables shown with a default fall back to it when unset; the ones marked **required** have no usable default for a production master.

- `DISCOGS_CONSUMER_KEY` (default `dummy`): **Required.** Discogs API consumer key
- `DISCOGS_CONSUMER_SECRET` (default `dummy`): **Required.** Discogs API consumer secret
- `JWT_SECRET` (default blank: random key per run): **Required.** Signing key for web login
  tokens, 32+ random characters. When unset, every restart signs out all web users and tokens from
  other instances / `GeneratePostmanJwtToken` are rejected
- `DB_URL` (default `jdbc:mysql://localhost:3306/jukeanator`): JDBC URL of the MySQL database
- `DB_USERNAME` (default `jukeanator`): MySQL user
- `DB_PASSWORD` (default `password`): **Required.** MySQL password -- never leave the default in
  production
- `DB_DRIVER` (default `com.mysql.cj.jdbc.Driver`): JDBC driver class (normally left unset)
- `SMTP_HOST` (default `smtp.gmail.com`): outbound mail server
- `SMTP_PORT` (default `587`): outbound mail port (STARTTLS)
- `SMTP_USERNAME` (default blank): **Required.** SMTP login
- `SMTP_PASSWORD` (default blank): **Required.** SMTP password (for Gmail, an app password)
- `MAIL_FROM_ADDRESS` (default `receipt.from-address`, then the built-in address): sender address
  for verification emails, receipts and Financial Ledger summaries
- `BRAINTREE_MERCHANT_ID` (default blank): **Required.** Braintree merchant ID
- `BRAINTREE_PUBLIC_KEY` (default blank): **Required.** Braintree public key
- `BRAINTREE_PRIVATE_KEY` (default blank): **Required.** Braintree private key

The database variables apply whenever `app.repository-type` is `jpa`. The Braintree keys must match `braintree.environment` in `application.yml` (`sandbox` or `production`), which is set there rather than through an environment variable.

Windows example (one `setx` per variable, then open a new Command Prompt):

```
setx JWT_SECRET "INSERT_VALUE_HERE"
setx DB_PASSWORD "INSERT_VALUE_HERE"
setx SMTP_USERNAME "INSERT_VALUE_HERE"
setx SMTP_PASSWORD "INSERT_VALUE_HERE"
setx BRAINTREE_MERCHANT_ID "INSERT_VALUE_HERE"
setx BRAINTREE_PUBLIC_KEY "INSERT_VALUE_HERE"
setx BRAINTREE_PRIVATE_KEY "INSERT_VALUE_HERE"
```

Linux example (append to `~/.profile`, keep it `chmod 600`, then log out and back in):

```
# JukeANator (master mode)
export JWT_SECRET="INSERT_VALUE_HERE"
export DB_URL="jdbc:mysql://localhost:3306/jukeanator"
export DB_USERNAME="jukeanator"
export DB_PASSWORD="INSERT_VALUE_HERE"
export SMTP_HOST="smtp.gmail.com"
export SMTP_PORT="587"
export SMTP_USERNAME="INSERT_VALUE_HERE"
export SMTP_PASSWORD="INSERT_VALUE_HERE"
export MAIL_FROM_ADDRESS="INSERT_VALUE_HERE"
export BRAINTREE_MERCHANT_ID="INSERT_VALUE_HERE"
export BRAINTREE_PUBLIC_KEY="INSERT_VALUE_HERE"
export BRAINTREE_PRIVATE_KEY="INSERT_VALUE_HERE"
```

## MySQL setup

Setting up MySQL database for first time use:

```
DROP DATABASE IF EXISTS jukeanator;

CREATE DATABASE jukeanator CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

DROP DATABASE IF EXISTS jukeanator_test;

CREATE DATABASE jukeanator_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';

GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost';

FLUSH PRIVILEGES;



CREATE DATABASE IF NOT EXISTS jukeanator;

CREATE USER IF NOT EXISTS 'jukeanator'@'%' IDENTIFIED BY 'password';
CREATE USER IF NOT EXISTS 'jukeanator'@'localhost' IDENTIFIED BY 'password';

GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'%';
GRANT ALL PRIVILEGES ON jukeanator.* TO 'jukeanator'@'localhost';

FLUSH PRIVILEGES;
```

# Live Integration Tests
Use the following:

```
sudo mysql -e "CREATE DATABASE IF NOT EXISTS jukeanator_test; 
GRANT ALL PRIVILEGES ON jukeanator_test.* TO 'jukeanator'@'localhost'; 
FLUSH PRIVILEGES;"
```

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.0.4/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.0.4/maven-plugin/build-image.html)
* [GraalVM Native Image Support](https://docs.spring.io/spring-boot/4.0.4/reference/packaging/native-image/introducing-graalvm-native-images.html)
* [Spring Boot Testcontainers support](https://docs.spring.io/spring-boot/4.0.4/reference/testing/testcontainers.html#testing.testcontainers)
* [Testcontainers MySQL Module Reference Guide](https://java.testcontainers.org/modules/databases/mysql/)
* [Spring Web](https://docs.spring.io/spring-boot/4.0.4/reference/web/servlet.html)
* [HTTP Client](https://docs.spring.io/spring-boot/4.0.4/reference/io/rest-client.html#io.rest-client.restclient)
* [JDBC API](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.0.4/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Flyway Migration](https://docs.spring.io/spring-boot/4.0.4/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway)
* [Elasticsearch](https://docs.spring.io/spring-boot/4.0.4/reference/data/nosql.html#data.nosql.elasticsearch)
* [WebSocket](https://docs.spring.io/spring-boot/4.0.4/reference/messaging/websockets.html)
* [Quartz Scheduler](https://docs.spring.io/spring-boot/4.0.4/reference/io/quartz.html)
* [Testcontainers](https://java.testcontainers.org/)
* [Java Mail Sender](https://docs.spring.io/spring-boot/4.0.4/reference/io/email.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Relational Data using JDBC with Spring](https://spring.io/guides/gs/relational-data-access/)
* [Managing Transactions](https://spring.io/guides/gs/managing-transactions/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Using WebSocket to build an interactive web application](https://spring.io/guides/gs/messaging-stomp-websocket/)

### Additional Links
These additional references should also help you:

* [Configure AOT settings in Build Plugin](https://docs.spring.io/spring-boot/4.0.4/how-to/aot.html)

## GraalVM Native Support

This project has been configured to let you generate either a lightweight container or a native executable.
It is also possible to run your tests in a native image.

### Lightweight Container with Cloud Native Buildpacks
If you're already familiar with Spring Boot container images support, this is the easiest way to get started.
Docker should be installed and configured on your machine prior to creating the image.

To create the image, run the following goal:

```
$ ./mvnw spring-boot:build-image -Pnative
```

Then, you can run the app like any other container:

```
$ docker run --rm -p 8080:8080 jukeanator-engine:0.0.1-SNAPSHOT
```

### Executable with Native Build Tools
Use this option if you want to explore more options such as running your tests in a native image.
The GraalVM `native-image` compiler should be installed and configured on your machine.

NOTE: GraalVM 25+ is required.

To create the executable, run the following goal:

```
$ ./mvnw native:compile -Pnative
```

Then, you can run the app as follows:
```
$ target/jukeanator-engine
```

You can also run your existing tests suite in a native image.
This is an efficient way to validate the compatibility of your application.

To run your existing tests in a native image, run the following goal:

```
$ ./mvnw test -PnativeTest
```


### Testcontainers support

This project uses [Testcontainers at development time](https://docs.spring.io/spring-boot/4.0.4/reference/features/dev-services.html#features.dev-services.testcontainers).

Testcontainers has been configured to use the following Docker images:

* [`mysql:latest`](https://hub.docker.com/_/mysql)

Please review the tags of the used images and set them to the same as you're running in production.

#### Docker setup for tests (Windows, Linux, macOS)

`mvn clean package` runs integration tests that use Testcontainers + MySQL -- the same DBMS the hosting provider runs in production. Docker must be installed and the daemon must be reachable.

- **Windows**: install [Docker Desktop](https://www.docker.com/products/docker-desktop/) and ensure it is running.
- **macOS**: install Docker Desktop, [Colima](https://github.com/abiosoft/colima), or [Rancher Desktop](https://rancherdesktop.io/).
- **Linux**: install Docker Engine and ensure your user can run Docker commands.

Quick verification:

```
docker version
docker run --rm mysql:latest --version
```

If you are using Colima on macOS, export Docker host before running Maven:

```
export DOCKER_HOST=unix://${HOME}/.colima/default/docker.sock
./mvnw clean package
```

Windows PowerShell equivalent:

```
docker version
docker run --rm mysql:latest --version
.\mvnw.cmd clean package
```

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
