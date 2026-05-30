<#
    Shared Android/SDK helpers for the UI-testing harness.
    Dot-source this file:  . "$PSScriptRoot\_Android.ps1"

    Provides:
      Get-AndroidSdk                       -> SDK root path
      Get-Adb                              -> path to adb.exe
      Get-Emulator                         -> path to emulator.exe
      Get-EmulatorSerialForAvd <avdName>   -> "emulator-5554" or $null
      Wait-AndroidBoot <serial> [-TimeoutSec n]
#>

function Get-AndroidSdk {
    foreach ($c in @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android\Sdk'))) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    # Fall back to local.properties sdk.dir.
    $lp = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')) 'local.properties'
    if (Test-Path $lp) {
        $line = Get-Content $lp | Where-Object { $_ -match '^\s*sdk\.dir=' }
        if ($line) {
            $dir = ($line -replace '^\s*sdk\.dir=', '').Trim() -replace '\\\\', '\' -replace '\\:', ':'
            if (Test-Path $dir) { return $dir }
        }
    }
    throw "Android SDK not found (set ANDROID_HOME or local.properties sdk.dir)."
}

function Get-Adb { Join-Path (Get-AndroidSdk) 'platform-tools\adb.exe' }
function Get-Emulator { Join-Path (Get-AndroidSdk) 'emulator\emulator.exe' }

function Get-EmulatorSerialForAvd {
    param([Parameter(Mandatory = $true)][string]$AvdName)
    $adb = Get-Adb
    $devices = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '^emulator-\d+\s+device' }
    foreach ($d in $devices) {
        $serial = ($d -split '\s+')[0]
        # No 2>$null: redirecting a native exe's stderr in Win PS 5.1 wraps it in a terminating
        # ErrorRecord under ErrorActionPreference=Stop. Let stderr flow; gate on output instead.
        $name = (& $adb -s $serial emu avd name | Select-Object -First 1)
        if ($name -and $name.Trim() -eq $AvdName) { return $serial }
    }
    return $null
}

function Wait-AndroidBoot {
    param([Parameter(Mandatory = $true)][string]$Serial, [int]$TimeoutSec = 240)
    # A freshly-listed emulator is often still 'offline', so adb shell briefly returns
    # "device not found". Tolerate that and keep polling until sys.boot_completed flips to 1.
    $adb = Get-Adb
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        while ((Get-Date) -lt $deadline) {
            $booted = (& $adb -s $Serial shell getprop sys.boot_completed | Out-String).Trim()
            if ($booted -eq '1') {
                & $adb -s $Serial shell input keyevent 82 | Out-Null  # dismiss keyguard
                return
            }
            Start-Sleep -Milliseconds 2000
        }
    } finally {
        $ErrorActionPreference = $prev
    }
    throw "Device $Serial did not finish booting within ${TimeoutSec}s."
}
