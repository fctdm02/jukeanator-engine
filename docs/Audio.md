# Jukebox Audio Module

Cross-platform master volume + line-in monitoring for the jukebox app. Drop
the `com.jukebox.audio` package into your project (rename the base package
to match yours if needed — that's the only thing keeping this out of your
component scan) and wire it up per below.

## What's in here

```
MasterVolumeService            interface: getMasterVolume()/setMasterVolume(percent)
LineInService                  interface: line-in availability, signal detection,
                                volume, start/stop monitoring
SongQueueStateProvider         interface your SongQueueServiceImpl should implement
JukeboxAudioCoordinator        starts/stops line-in monitoring based on queue state

windows/WindowsMasterVolumeService    Core Audio via PowerShell + inline C# (no JNA needed)
windows/WindowsCoreAudioBridge        process plumbing for the above
linux/LinuxMasterVolumeService        pactl get/set-sink-volume (PipeWire-compatible)
mac/MacMasterVolumeService             osascript (AppleScript) output volume

lineinput/LineInServiceImpl     mixer discovery + thread lifecycle
lineinput/LineInMonitorTask     the actual capture -> gain -> playback loop
lineinput/MixerDiagnostics      run this on real hardware to find your line-in mixer name

config/AudioPlatformConfig      Spring @Configuration, picks OS impl via os.name
config/LineInProperties         @ConfigurationProperties (jukebox.audio.line-in.*)

web/AudioSettingsController     optional REST endpoints, delete if not needed
```

No new Maven dependencies are required — everything uses the JDK's
`javax.sound.sampled`, `ProcessBuilder`, and whatever Spring Boot starters
you already have.

## Integration steps

1. **Copy the package** into `src/main/java` (and the `.ps1` resource into
   `src/main/resources/audio/`), adjusting `com.jukebox.audio` to your real
   base package if it differs.

2. **Implement `SongQueueStateProvider`** on your existing
   `SongQueueServiceImpl`:

   ```java
   public class SongQueueServiceImpl implements SongQueueService, SongQueueStateProvider {
       ...
       @Override
       public boolean isQueueEmpty() { return queue.isEmpty(); }

       @Override
       public boolean isBackgroundMusicEnabled() { return enableBackgroundMusic; }
   }
   ```

   If you'd rather not touch that class, write a small `@Component` adapter
   that wraps it and implements the interface instead.

3. **Add `@EnableScheduling`** to your main `@SpringBootApplication` class —
   `JukeboxAudioCoordinator` polls every second to reconcile monitoring
   state. If you want instant reaction instead of a ≤1s delay, call
   `jukeboxAudioCoordinator.reconcile()` directly from
   `SongQueueServiceImpl` whenever a song is added/removed or
   `enableBackgroundMusic` is toggled — it's idempotent and cheap.

4. **Configure defaults** (optional) in `application.yml`:

   ```yaml
   jukebox:
     audio:
       line-in:
         default-volume: 75
         preferred-mixer-name: ""   # e.g. "Line In" — see step 5
   ```

5. **Run `MixerDiagnostics.main()` on the actual jukebox hardware** before
   relying on auto-detection. Java Sound's mixer names are driver-specific;
   auto-detection looks for names containing "line in", "aux", etc., and
   falls back to the first capture-capable device otherwise — which could
   be a built-in microphone instead of the line-in jack on some sound
   cards. If it picks the wrong one, set
   `jukebox.audio.line-in.preferred-mixer-name` to a substring of the
   correct mixer's name from the diagnostic output.

## How the behavior maps to your requirement

`JukeboxAudioCoordinator` starts `LineInService.startMonitoring()` exactly
when `isQueueEmpty() && !isBackgroundMusicEnabled()`, and stops it the
moment either condition flips (a song gets queued, or background music is
turned back on). While running, `LineInMonitorTask` continuously reads PCM
from the line-in capture device, scales sample amplitude by
`lineInVolume / 100` (software gain — exactly your configured value
regardless of what the hardware/driver supports), and writes it straight to
the default output device. `LineInService.isLineInReceivingSignal()` reports
whether the most recent captured block had non-trivial RMS amplitude, so you
can show "no signal" vs. "playing" in a UI if you want.

## Windows — the part to actually test

`WindowsMasterVolumeService` shells out to a bundled PowerShell script
(`windows-master-volume.ps1`) that uses `Add-Type` to declare the
`IMMDeviceEnumerator` / `IMMDevice` / `IAudioEndpointVolume` COM interfaces
inline and calls `GetMasterVolumeLevelScalar` / `SetMasterVolumeLevelScalar`
on the default playback endpoint — the same API Windows itself uses for the
volume slider. This avoids adding JNA as a dependency, but **it was written
against the documented Core Audio vtable layout and has not been run on a
real Windows machine** in this environment (no Windows sandbox available
here). Before shipping:

```
powershell -File windows-master-volume.ps1 -Action get
powershell -File windows-master-volume.ps1 -Action set -Volume 50
```

Run those manually first. If something's off, the C# vtable method order in
the script (`NotImpl1`, `NotImpl2`, etc.) is the most likely culprit —
those placeholders exist to keep COM method indices aligned with the real
interfaces while only the methods actually used are implemented. The Java
side (`WindowsCoreAudioBridge`) surfaces stderr in the exception message if
the script fails, which should make debugging straightforward.

`WindowsExecutionPolicy Bypass` is scoped to that one script invocation
only (`-ExecutionPolicy Bypass -File ...`), not a system-wide policy
change.

## Linux / macOS

Both `pactl` (Linux) and `osascript` (macOS) are standard CLI tools present
on essentially every desktop install of those OSes, so no extra setup is
needed beyond having PulseAudio/PipeWire running on Linux. If a target Linux
box runs bare PipeWire without the `pipewire-pulse` compatibility shim,
swap the `pactl` calls in `LinuxMasterVolumeService` for `wpctl` — noted
inline in that class.

## What's intentionally *not* done

- **Native line-in hardware volume control** (Windows/Linux capture-device
  gain via Core Audio/ALSA) was left out in favor of pure software gain in
  `LineInMonitorTask`. This was the approach recommended in your original
  consultation too — it guarantees identical, exact behavior across all
  three OSes instead of depending on inconsistent driver support
  (macOS in particular often has no exposed line-in gain control at all).
  If you do want native capture-device volume later, the same
  PowerShell/C# pattern used for master volume can target the capture
  endpoint (`eCapture` instead of `eRender` in the COM call) — happy to
  build that out if you need it.
- OS-level loopback routing (Windows "Listen to this device", PulseAudio/
  PipeWire loopback modules, macOS Loopback/Audio MIDI Setup) was not used,
  since it requires either manual per-machine configuration or extra
  installed software — the in-process Java capture/playback approach here
  needs zero external setup and behaves identically everywhere.