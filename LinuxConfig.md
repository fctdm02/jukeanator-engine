# Linux Configuration

This guide sets up a Linux Mint PC to run JukeANator as a standalone or slave jukebox. For a Windows PC, see [WindowsConfig.md](WindowsConfig.md). For master mode and the optional MySQL database, see [MasterConfig.md](MasterConfig.md). For building from source and running the tests, see [README.md](README.md).

The sections below are in the order they are normally done:

1. Prerequisite: Java 25
2. Prerequisite: media player
3. Install JukeANator
4. ELO ET2002L touchscreen (only if this touchscreen is used)
5. eGalax touchscreen (only if this touchscreen is used, for example in a converted TouchTunes Maestro)
6. Kiosk Mode Settings
7. Auto-Start JukeANator at Power-On

## Prerequisite: Java 25

The application needs a **Java 25** runtime (64-bit). The instructions below install the distribution's OpenJDK 25 package. A full JRE is needed, not a "headless" one, because the kiosk UI uses Swing.

There are two ways to provide Java:

- **Installed Java (described below):** install Java 25 once and make it the default. Then remove the `export PATH="$PWD/jdk-25.0.2/bin:$PATH"` line from `go.sh` (see "Install JukeANator").
- **Bundled JDK:** unzip a JDK 25 (the Eclipse Temurin `.tar.gz` for Linux x64, from https://adoptium.net/temurin/releases/?version=25) next to the WAR and keep that line in `go.sh`. The archive unpacks to a versioned folder such as `jdk-25.0.2+10`; rename it to `jdk-25.0.2`, or change the folder name in the script to match. Nothing has to be installed or made the default.

### Step 1: Install

Install OpenJDK 25 from the Mint repositories (`openjdk-25-jre` is the full JRE; do not use `openjdk-25-jre-headless` on its own):

```
sudo apt update
sudo apt install openjdk-25-jre
```

If `apt` reports that the package cannot be found (older Mint releases may not include Java 25), use the bundled JDK described above instead.

### Step 2: Make it the default and verify

```
java -version
```

`java -version` must report version 25. If it reports an older version, choose Java 25 from the list this command shows (type its number and press Enter):

```
sudo update-alternatives --config java
```

Then run `java -version` again.

## Prerequisite: media player

The application needs a media player installed before it can play songs. It is chosen with `song-player.player-type` in the external `application.yml`. On Linux the player is VLC media player (`vlc`): the application drives VLC through its libraries (vlcj), so VLC 3.x must be installed with the same bitness as the JDK (64-bit). `detached-vlc` (a name carried over from older installs) behaves exactly like `vlc`.

The bundled default is `winamp`, which is Windows only. On Linux the `winamp` setting is ignored and VLC is used, so VLC must be installed; set `player-type: vlc` anyway so the configuration matches what actually runs.

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

## Install JukeANator

### Step 1: Get the WAR

Copy `jukeanator-engine-0.0.1-SNAPSHOT.war` to the directory the application will run from (for example `/home/kiosk/jukeanator`). To build it from source, run this in the project directory (no separate Maven install is needed; see [README.md](README.md) for details):

```
./mvnw clean package -DskipTests
```

This creates `target/jukeanator-engine-0.0.1-SNAPSHOT.war`.

### Step 2: Set the environment variables (once)

Keys and secrets are never put in the launch script. Set them once as permanent environment variables for the account that runs the application, so copies of the script can be shared without exposing them.

Standalone and slave installs need these variables (master mode needs more; see [MasterConfig.md](MasterConfig.md)):

- `DISCOGS_CONSUMER_KEY`: Discogs API consumer key (song metadata / internet search)
- `DISCOGS_CONSUMER_SECRET`: Discogs API consumer secret
- `JWT_SECRET`: signing key for web login tokens, 32+ random characters (see "Generate a JWT secret" below). If unset, the app still runs but web logins do not survive a restart

A variable only takes effect if the external `application.yml` (in `--app.config-dir`) keeps that property in its `${VARIABLE:default}` form. A literal value written there overrides it.

**Generate a JWT secret.**

```
openssl rand -base64 48
```

It prints a random 64-character value to use as `JWT_SECRET`.

Open `~/.profile` in a text editor (`nano ~/.profile`) and add at the end:

```
# JukeANator
export DISCOGS_CONSUMER_KEY="INSERT_VALUE_HERE"
export DISCOGS_CONSUMER_SECRET="INSERT_VALUE_HERE"
export JWT_SECRET="INSERT_VALUE_HERE"
```

Then run `chmod 600 ~/.profile` so other accounts cannot read it, and log out and back in. Check one in a new terminal with `echo "$JWT_SECRET"`. (Use an editor rather than `echo ... >> ~/.profile`, which leaves the secret in the shell history. If the app runs as a systemd service, put the same lines without `export` in a `chmod 600` file and reference it with `EnvironmentFile=` in the unit.)

### Step 3: Create go.sh

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

Then make it executable with `chmod +x go.sh`. Remove the `export PATH=...` line if Java 25 is installed rather than bundled (see "Prerequisite: Java 25").

On first run the application seeds `~/kiosk/config/application.yml` (with `app.data-dir` pre-set to `~/kiosk/data`) if it does not already exist, then loads its settings from it. That is the external `application.yml` referred to throughout this guide.

Note the argument order: JVM options (`-D...`, `--enable-native-access=...`) come *before* `-jar <file>`, and everything after the jar filename is passed to the application. Putting `--app.config-dir` before `-jar` makes the Java launcher reject it as an unrecognized option.

### Optional: MySQL database

By default the application stores its data as files under `--app.data-dir`. To store it in MySQL instead, see [MasterConfig.md](MasterConfig.md).

## ELO ET2002L touchscreen

The ET2002L is a 20" Elo desktop touch monitor. Its touch controller connects to the PC with either a **USB** cable or a **serial** (RS-232, 9-pin) cable; dual-interface controllers switch to USB only when a USB cable is plugged in. Connect only one of the two.

- **USB:** the screen appears as a standard HID touch device. No Elo driver is needed.
- **Serial:** the operating system cannot detect it by itself. Either the built-in kernel driver (recommended below) or the Elo Linux serial driver is used.

Connect the monitor's video cable as well, and make the touchscreen the primary display if the PC has more than one.

These steps assume the default Cinnamon desktop on X11. Install the tools used below once:

```
sudo apt update
sudo apt install xinput xinput-calibrator xserver-xorg-input-evdev evtest
```

### Step 1: Connect the touch controller

**USB:** plug in the USB cable. Tapping the screen should already move the mouse pointer; no driver and no `inputattach` are needed. To confirm that the kernel found the controller (Elo's USB vendor id is `04e7`), run:

```
lsusb | grep -i 04e7
```

Then skip to Step 2.

**Serial:** Linux Mint already includes a kernel driver for Elo serial controllers (the `elo` module), so nothing has to be downloaded. The kernel does not attach it to the serial port by itself, however: the `inputattach` utility does that, and a systemd service runs it at every boot. Current PCs have no serial (RS-232) port, so the serial cable is plugged into an RS-232 to USB adapter, and the adapter into a USB port on the PC.

*Find the serial port.* Plug in the adapter and list the serial ports the kernel found:

```
sudo dmesg | grep -i tty
```

The adapter shows up as `/dev/ttyUSB0` (or `/dev/ttyUSB1` and so on if more than one adapter is connected). The commands below use `/dev/ttyUSB0`; substitute the actual port where needed.

*Prepare the adapter.* Two Mint services can interfere with an adapter. `brltty` (Braille display support) claims adapters with a CH340/CH341 chip, so the port appears in `dmesg` and then disconnects at once; ModemManager may send modem commands to any new serial port. If no Braille display or cellular modem is used, remove `brltty`, disable ModemManager, and then unplug and reconnect the adapter:

```
sudo apt remove brltty
sudo systemctl disable --now ModemManager
```

*Install inputattach.*

```
sudo apt install inputattach
```

*Test the connection.* Run `inputattach` in the foreground. It loads the `elo` kernel module and talks to the controller in Elo's 10-byte SmartSet protocol (used by IntelliTouch controllers) at 9600 baud:

```
sudo inputattach --elotouch /dev/ttyUSB0
```

Leave it running and, in a second terminal, confirm that the touchscreen was registered:

```
sudo dmesg | tail
```

A line such as `input: Elo Serial TouchScreen` should appear. Tapping the screen should now move the pointer; for a closer look, run `sudo evtest`, choose the Elo device, and touch the screen: coordinate events should be printed.

If nothing registers, the controller uses a different Elo protocol. Stop the command with `Ctrl+C` and try each of these in turn:

```
# (Standard 10-byte Elo SmartSet — most common)
sudo inputattach --elotouch  /dev/ttyUSB0

# (4-byte Elo protocol)
sudo inputattach --elo4002 /dev/ttyUSB0

# (6-byte Elo protocol)
sudo inputattach --elo271-140 /dev/ttyUSB0

sudo inputattach --elo261-281 /dev/ttyUSB0
```

These are Elo's 4-byte, 6-byte and 3-byte protocols, named after the controller models that use them. Their short forms are `-elo4b`, `-elo6b` and `-elo3b` (and `-elo` for `--elotouch`), with a single dash. To see the exact option names supported by the installed version, run `inputattach --help | grep -i elo`. If the controller has been set to a speed other than 9600 baud, add `--baud 19200` (or that speed) before the protocol option, for example `sudo inputattach --baud 19200 --elotouch /dev/ttyUSB0`. Stop the test with `Ctrl+C` once a working option is found.

*Attach it automatically at every boot.* Create a systemd service:

```
sudo nano /etc/systemd/system/elo-touch.service
```

```
[Unit]
Description=Elo serial touchscreen
After=dev-ttyUSB0.device
Requires=dev-ttyUSB0.device

[Service]
Type=simple
ExecStart=/usr/bin/inputattach --elotouch /dev/ttyUSB0
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

In the `ExecStart` line, use the port and options (protocol, and `--baud` if needed) that worked in the test. If the port is not `/dev/ttyUSB0`, change the `dev-ttyUSB0.device` lines to match (for example `dev-ttyUSB1.device` for `/dev/ttyUSB1`). Then load, enable and start the service:

```
sudo systemctl daemon-reload
sudo systemctl enable --now elo-touch.service
```

Check that it is running with `systemctl status elo-touch.service`.

*Alternative: Elo's own driver.* Elo also publishes its own Linux serial driver with configuration and calibration utilities: see Elo's Linux driver downloads at https://elosupport.elotouch.com/hc/en-us/articles/33699338540183-Linux-Touch-Drivers and follow the installation instructions included with it. Consider it only if the kernel driver does not recognise the controller, or if Elo-specific features (such as right-click emulation or touch beep settings) are needed. The `inputattach` method is simpler and keeps working across kernel updates without reinstalling anything. Use either that driver or `inputattach`, not both.

### Step 2: Check the device name

```
xinput list
```

Note the name of the Elo entry (for example `Elo TouchSystems ...` or `Elo Serial TouchScreen`). If the screen does not respond at all, `sudo evtest` lists the input devices and shows whether touches arrive.

### Step 3: Use the evdev driver for the touchscreen

Mint's default input driver ignores the calibration that `xinput_calibrator` produces, so assign the touchscreen to the `evdev` driver instead. Install the driver and the calibration tool, create the configuration file, then log out and back in (or reboot).

```
sudo apt install xserver-xorg-input-evdev xinput-calibrator
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

### Step 4: Calibrate

```
xinput_calibrator
```

Tap each of the four targets as it appears. When it finishes, the tool prints a `Section "InputClass" ... EndSection` block. Add the `Option "Calibration" ...` line it prints (and `Option "SwapAxes" ...` if it prints one) to the block in `/etc/X11/xorg.conf.d/99-elo-touch.conf` (open it with `sudo nano`), so it reads, for example, as shown below. Then log out and back in (or reboot); the calibration then applies at every login. If the X or Y direction is reversed, add `Option "InvertX" "on"` or `Option "InvertY" "on"`, or rerun `xinput_calibrator`.

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

## eGalax touchscreen

Many unbranded touchscreens, including the one in a converted TouchTunes Maestro, use a touch controller made by EETI (eGalax). The Windows driver for it is named "eGalaxTouch". Linux Mint does not need that driver: the kernel already includes drivers for eGalax controllers, so nothing has to be downloaded. The eGalax controller board sits behind the screen and connects to the PC with a serial (RS-232, 9-pin) cable. The kernel's `egalax_ts_serial` module drives it, and the `inputattach` utility attaches that module to the serial port. The kernel does not do this by itself, so a systemd service runs `inputattach` at every boot. Current PCs have no serial (RS-232) port, so the serial cable is plugged into an RS-232 to USB adapter, and the adapter into a USB port on the PC.

Calibration made with the eGalax utility on Windows is stored by that utility, not in the controller, so it does not carry over. The screen must be calibrated again on Linux (Step 4).

These steps assume the default Cinnamon desktop on X11. Install the tools used below once:

```
sudo apt update
sudo apt install xinput xinput-calibrator xserver-xorg-input-evdev evtest inputattach
```

### Step 1: Connect the touch controller

First confirm that the Mint kernel includes the eGalax serial driver. This command must print the module's details, not an error:

```
modinfo egalax_ts_serial
```

*Find the serial port.* Plug in the RS-232 cable and list the serial ports the kernel found:

```
sudo dmesg | grep -i tty
```

The adapter shows up as `/dev/ttyUSB0` (or `/dev/ttyUSB1` and so on if more than one adapter is connected). The commands below use `/dev/ttyUSB0`; substitute the actual port where needed. Remove `brltty` and disable ModemManager as described under "ELO ET2002L touchscreen", Step 1 (*Prepare the adapter*), so that they do not take over the adapter.

*Optional: check that the controller sends data.* Set the port to 9600 baud and print the raw bytes it receives, then touch the screen. Lines of hexadecimal data should appear with each touch. Stop it with `Ctrl+C`:

```
sudo stty -F /dev/ttyUSB0 9600 raw
sudo cat /dev/ttyUSB0 | xxd
```

If nothing appears, the port is wrong, the cable is not connected, or the controller is set to a different speed (see "If the serial touchscreen does not respond" below).

*Test the connection.* Run `inputattach` in the foreground. It loads the `egalax_ts_serial` kernel module and talks to the controller at 9600 baud:

```
sudo inputattach --eetiegalax /dev/ttyUSB0
```

Leave it running and, in a second terminal, confirm that the touchscreen was registered:

```
sudo dmesg | tail
```

A line such as `input: EETI eGalaxTouch Serial TouchScreen` should appear. Tapping the screen should now move the pointer (possibly to the wrong place until it is calibrated). For a closer look, run `sudo evtest`, choose the eGalax device, and touch the screen: coordinate events should be printed. Stop the test with `Ctrl+C`. The short form of the option is `-eeti`, with a single dash.

*Attach it automatically at every boot.* Create a systemd service:

```
sudo nano /etc/systemd/system/egalax-touch.service
```

```
[Unit]
Description=eGalax serial touchscreen
After=dev-ttyUSB0.device
Requires=dev-ttyUSB0.device

[Service]
Type=simple
ExecStart=/usr/bin/inputattach --eetiegalax /dev/ttyUSB0
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

If the port is not `/dev/ttyUSB0`, change it in the `ExecStart` line and in the `dev-ttyUSB0.device` lines (for example `dev-ttyUSB1.device` for `/dev/ttyUSB1`). Then load, enable and start the service:

```
sudo systemctl daemon-reload
sudo systemctl enable --now egalax-touch.service
```

Check that it is running with `systemctl status egalax-touch.service`.

*If the serial touchscreen does not respond.* Check the following:

- The port is correct and no other program holds it. `sudo fuser /dev/ttyUSB0` prints nothing when the port is free.
- The adapter stays connected. If `sudo dmesg | grep -i ttyUSB` shows it connecting and then disconnecting at once, `brltty` is still installed.
- The controller runs at 9600 baud, its factory default. If it was set to another speed, add `--baud 19200` (or that speed) before the protocol option, for example `sudo inputattach --baud 19200 --eetiegalax /dev/ttyUSB0`, or set it back to 9600 with the eGalax utility on a Windows PC.
- The installed `inputattach` supports eGalax: `inputattach --help | grep -i eeti` must print a line.

### Step 2: Check the device name

```
xinput list
```

Note the name of the eGalax entry (normally `EETI eGalaxTouch Serial TouchScreen`). If the screen does not respond at all, `sudo evtest` lists the input devices and shows whether touches arrive.

### Step 3: Use the evdev driver for the touchscreen

Mint's default input driver ignores the calibration that `xinput_calibrator` produces, so assign the touchscreen to the `evdev` driver instead. Create the configuration file, then log out and back in (or reboot):

```
sudo mkdir -p /etc/X11/xorg.conf.d
sudo nano /etc/X11/xorg.conf.d/99-egalax-touch.conf
```

```
Section "InputClass"
    Identifier "eGalax touchscreen"
    MatchProduct "eGalax"
    MatchDevicePath "/dev/input/event*"
    Driver "evdev"
EndSection
```

`MatchProduct` matches any device whose name contains the given text. If the name noted in Step 2 does not contain `eGalax`, use part of that name instead.

### Step 4: Calibrate

```
xinput_calibrator
```

Tap each of the four targets as it appears. When it finishes, the tool prints a `Section "InputClass" ... EndSection` block. Add the `Option "Calibration" ...` line it prints (and `Option "SwapAxes" ...` if it prints one) to the block in `/etc/X11/xorg.conf.d/99-egalax-touch.conf` (open it with `sudo nano`), so it reads, for example, as shown below. Then log out and back in (or reboot); the calibration then applies at every login. If the X or Y direction is reversed, add `Option "InvertX" "on"` or `Option "InvertY" "on"`, or rerun `xinput_calibrator`.

```
Section "InputClass"
    Identifier "eGalax touchscreen"
    MatchProduct "eGalax"
    MatchDevicePath "/dev/input/event*"
    Driver "evdev"
    Option "Calibration" "102 1945 102 1945"
    Option "SwapAxes" "0"
EndSection
```

The driver reports positions from 0 to 2047 on each axis, so the calibration values fall in that range (the example values above are typical).

### Step 5: Two monitors (only if the PC has more than one display)

Touches are spread over all displays until the touchscreen is tied to its own output. Find the output name (for example `HDMI-1`):

```
xrandr --listmonitors
```

Then map the touch device to it, using the device name from Step 2:

```
xinput map-to-output "EETI eGalaxTouch Serial TouchScreen" HDMI-1
```

This lasts until logout. To apply it at every login, add the same command in **Menu**, then **Startup Applications** (click **+**, then **Custom command**, and set a delay of a few seconds).

## Kiosk Mode Settings

A jukebox must stay on and unlocked at all times. The application shows its own screensaver when idle, so the operating system must never turn off the display, never start its own screensaver, never lock the screen after a timeout, and never sleep or hibernate. The steps below turn all of these off and sign the kiosk account in automatically after a reboot or power cut.

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
- `idle-dim-battery false`: the screen is not dimmed when idle (this only matters on a PC with a battery).

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

As an extra safeguard, add a startup entry in **Menu**, then **Startup Applications** (click **+**, then **Custom command**) with this command (with DPMS disabled above, `xset -dpms` only prints a harmless warning):

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

`xset q` must show `timeout:  0` under **Screen Saver**, and must end with `Server does not have the DPMS Extension` (because Step 2 removes DPMS entirely) or, if Step 2 was skipped, `DPMS is Disabled`. The second command must print `uint32 0`.

If the session is ever switched to Wayland at the login screen, Step 2 and the `xset` startup entry have no effect; Steps 1 and 3 still apply.

## Auto-Start JukeANator at Power-On

With the steps below, switching the PC on ends with JukeANator running full screen on top of everything: the PC boots, signs the kiosk account in automatically (Step 4 under "Kiosk Mode Settings"), and the sign-in starts `go.sh`.

### Step 1: Power on after a power cut (BIOS/UEFI)

So the PC also starts when the cabinet's power is switched on or comes back after an outage, enter the BIOS/UEFI setup (usually by pressing `Del`, `F2` or `F10` right after power-on) and set the power-loss option to **Power On**. Its name depends on the manufacturer, for example **Restore on AC Power Loss**, **AC Back**, **After Power Failure** or **AC Recovery**. Save and exit.

### Step 2: Keep the jukebox window on top

The application already fills the screen in full-screen mode, and by default it also keeps other windows from appearing over it. That is controlled by this setting, which is `true` by default. Check that the external `application.yml` (in `--app.config-dir`) does not set it to `false`, which can be the case in a copy seeded by an older version:

```
user-interface:
  always-on-top: true
```

### Step 3: Start go.sh at sign-in

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

### Verify

Switch the PC off at the wall or cabinet switch, then switch it on again. Without touching anything, it must boot, sign in, and show JukeANator full screen.
