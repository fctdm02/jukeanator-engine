<#
 .SYNOPSIS
    Get/set the Windows default playback device's master volume using
    the Core Audio API (IAudioEndpointVolume), via inline C# (Add-Type)
    so no compiled native DLL or external PowerShell module is required.

 .DESCRIPTION
    Invoked by WindowsCoreAudioBridge.java as:
      powershell -File windows-master-volume.ps1 -Action get
      powershell -File windows-master-volume.ps1 -Action set -Volume 75

    On "get", prints the current volume as an integer percentage (0-100)
    to stdout. On "set", applies the given percentage and prints "OK".

    NOTE: the IAudioEndpointVolume / IMMDevice / IMMDeviceEnumerator
    interfaces below are declared as partial COM interfaces - only the
    methods up through the ones actually used are listed, in their real
    vtable order, since C#/COM interop requires positions to line up.
    Methods after the ones we need are simply omitted (that's fine -
    only methods you SKIP IN THE MIDDLE would break the vtable mapping).
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("get", "set")]
    [string]$Action,

    [int]$Volume
)

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

[Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IAudioEndpointVolume
{
    int NotImpl1();
    int NotImpl2();
    int GetChannelCount(out uint pnChannelCount);
    int SetMasterVolumeLevel(float fLevelDB, Guid pguidEventContext);
    int SetMasterVolumeLevelScalar(float fLevel, Guid pguidEventContext);
    int GetMasterVolumeLevel(out float pfLevelDB);
    int GetMasterVolumeLevelScalar(out float pfLevel);
}

[Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IMMDevice
{
    int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams,
        [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
}

[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IMMDeviceEnumerator
{
    int NotImpl1();
    int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppEndpoint);
}

[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
public class MMDeviceEnumeratorComObject
{
}

public static class CoreAudioVolume
{
    // EDataFlow: eRender = 0
    // ERole: eMultimedia = 1
    public static IAudioEndpointVolume GetDefaultPlaybackEndpointVolume()
    {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
        IMMDevice device;
        int hr = enumerator.GetDefaultAudioEndpoint(0, 1, out device);
        Marshal.ThrowExceptionForHR(hr);

        Guid epvGuid = typeof(IAudioEndpointVolume).GUID;
        object epvObj;
        hr = device.Activate(ref epvGuid, 1 /* CLSCTX_INPROC_SERVER */, IntPtr.Zero, out epvObj);
        Marshal.ThrowExceptionForHR(hr);

        return (IAudioEndpointVolume)epvObj;
    }

    public static int GetVolumePercent()
    {
        var vol = GetDefaultPlaybackEndpointVolume();
        float level;
        int hr = vol.GetMasterVolumeLevelScalar(out level);
        Marshal.ThrowExceptionForHR(hr);
        return (int)Math.Round(level * 100.0f);
    }

    public static void SetVolumePercent(int percent)
    {
        var vol = GetDefaultPlaybackEndpointVolume();
        float scalar = Math.Max(0, Math.Min(100, percent)) / 100.0f;
        int hr = vol.SetMasterVolumeLevelScalar(scalar, Guid.Empty);
        Marshal.ThrowExceptionForHR(hr);
    }
}
"@

try {
    if ($Action -eq "get") {
        $pct = [CoreAudioVolume]::GetVolumePercent()
        Write-Output $pct
    }
    else {
        if ($PSBoundParameters.ContainsKey('Volume') -eq $false) {
            throw "Action 'set' requires -Volume <0-100>"
        }
        [CoreAudioVolume]::SetVolumePercent($Volume)
        Write-Output "OK"
    }
}
catch {
    [Console]::Error.WriteLine($_.Exception.ToString())
    exit 1
}
