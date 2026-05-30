<#
.SYNOPSIS
    Boot an Android target (emulator AVD or attached device), install the debug APK, and launch NotePen.

.DESCRIPTION
    Resolves a target serial:
      * -Serial given        -> use it.
      * -Avd given           -> reuse the running emulator for that AVD, else boot it and wait.
      * neither              -> if exactly one device is attached, use it; otherwise list and stop.

    Installs the app (prebuilt debug APK by default; -Build re-runs :installDebug first) and launches
    the launcher activity via `monkey` (no activity-name coupling).

.PARAMETER Serial   Explicit adb serial (e.g. emulator-5554 or a device id). Required when several are attached.
.PARAMETER Avd      AVD name to boot/reuse (e.g. NotePen_Tablet_API_36_1).
.PARAMETER ApkPath  APK to install (default app/byCompose/android/build/outputs/apk/debug/android-debug.apk).
.PARAMETER AppId    Launchable application id (default ru.kyamshanov.notepen.debug).
.PARAMETER Build    Run `./gradlew :app:byCompose:android:assembleDebug` to refresh the APK first.
.PARAMETER NoInstall Skip install (app already present); just (re)launch.

.EXAMPLE
    ./Start-AndroidTarget.ps1 -Serial emulator-5554
.EXAMPLE
    ./Start-AndroidTarget.ps1 -Avd NotePen_Tablet_API_36_1
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$Avd,
    [string]$ApkPath,
    [string]$AppId = 'ru.kyamshanov.notepen.debug',
    [switch]$Build,
    [switch]$NoInstall
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_Android.ps1"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$adb = Get-Adb
if (-not $ApkPath) {
    $ApkPath = Join-Path $repoRoot 'app\byCompose\android\build\outputs\apk\debug\android-debug.apk'
}

# --- Resolve serial -------------------------------------------------------------------------------
if (-not $Serial -and $Avd) {
    $Serial = Get-EmulatorSerialForAvd -AvdName $Avd
    if ($Serial) {
        Write-Host "AVD '$Avd' already running as $Serial."
    } else {
        Write-Host "Booting AVD '$Avd'..."
        $emu = Get-Emulator
        Start-Process -FilePath $emu -ArgumentList @('-avd', $Avd, '-no-snapshot-load') -WindowStyle Minimized | Out-Null
        $deadline = (Get-Date).AddSeconds(120)
        while (-not $Serial -and (Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 2
            $Serial = Get-EmulatorSerialForAvd -AvdName $Avd
        }
        if (-not $Serial) { throw "AVD '$Avd' did not appear in adb within 120s." }
        Write-Host "AVD '$Avd' came up as $Serial."
    }
}
if (-not $Serial) {
    $attached = & $adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' } |
        ForEach-Object { ($_ -split '\s+')[0] }
    if ($attached.Count -eq 1) {
        $Serial = $attached[0]
        Write-Host "Using the only attached device: $Serial"
    } else {
        Write-Host "Multiple/zero devices attached. Pass -Serial or -Avd. Attached:"
        $attached | ForEach-Object { Write-Host "  $_" }
        throw "Ambiguous target."
    }
}

Wait-AndroidBoot -Serial $Serial

# --- Install --------------------------------------------------------------------------------------
if (-not $NoInstall) {
    if ($Build) {
        Write-Host "Building debug APK..."
        & (Join-Path $repoRoot 'gradlew.bat') ':app:byCompose:android:assembleDebug'
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed." }
    }
    if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath  (use -Build to produce it)." }
    Write-Host "Installing $ApkPath -> $Serial"
    & $adb -s $Serial install -r -d $ApkPath
    if ($LASTEXITCODE -ne 0) { throw "adb install failed." }
}

# --- Launch ---------------------------------------------------------------------------------------
Write-Host "Launching $AppId on $Serial"
& $adb -s $Serial shell monkey -p $AppId -c android.intent.category.LAUNCHER 1 | Out-Null
Start-Sleep -Milliseconds 1500
# Capture full dumpsys into a variable first; piping straight into Select-Object -First would
# terminate adb's pipe early and leak exit code 255.
$dump = & $adb -s $Serial shell dumpsys window
$focus = ($dump | Select-String 'mCurrentFocus' | Select-Object -First 1)
Write-Host "Launched. Current focus: $focus"
Write-Host "Serial: $Serial"
$global:LASTEXITCODE = 0
return $Serial
