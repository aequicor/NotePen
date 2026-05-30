<#
.SYNOPSIS
    Create a tablet AVD for NotePen UI testing WITHOUT avdmanager.

.DESCRIPTION
    This SDK install has no cmdline-tools (no avdmanager/sdkmanager), so we create the AVD the way
    the tools would: by writing the two config files. We clone the existing phone AVD's config.ini
    (same android-36.1 google_apis_playstore x86_64 system image) and override the geometry to a
    10" tablet: 1600x2560 @ 320dpi -> ~800dp smallest width, which triggers tablet (sw600dp+) layouts.

    Userdata/snapshots are created by the emulator on first boot. Idempotent: re-running is a no-op
    unless -Force is given (which deletes and recreates).

.PARAMETER Name        AVD name (default NotePen_Tablet_API_36_1).
.PARAMETER SourceAvd   Existing AVD whose config to clone (default Medium_Phone_API_36.1).
.PARAMETER Force       Recreate even if it already exists.

.EXAMPLE
    ./New-TabletAvd.ps1
    # then boot it:
    & "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd NotePen_Tablet_API_36_1
#>
[CmdletBinding()]
param(
    [string]$Name = 'NotePen_Tablet_API_36_1',
    [string]$SourceAvd = 'Medium_Phone_API_36.1',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$avdHome = Join-Path $env:USERPROFILE '.android\avd'
$srcIni = Join-Path $avdHome "$SourceAvd.ini"
if (-not (Test-Path $srcIni)) { throw "Source AVD ini not found: $srcIni" }
# Resolve the source .avd dir from the ini's path= line.
$srcAvdDir = ((Get-Content $srcIni | Where-Object { $_ -like 'path=*' }) -replace '^path=', '').Trim()
$srcConfig = Join-Path $srcAvdDir 'config.ini'
if (-not (Test-Path $srcConfig)) { throw "Source config.ini not found: $srcConfig" }

$targetIni = Join-Path $avdHome "$Name.ini"
$targetAvdDir = Join-Path $avdHome "$Name.avd"

if ((Test-Path $targetAvdDir) -or (Test-Path $targetIni)) {
    if (-not $Force) {
        Write-Host "AVD '$Name' already exists. Use -Force to recreate. Boot it with:"
        Write-Host "  & `"$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe`" -avd $Name"
        return
    }
    Write-Host "Removing existing AVD '$Name' (-Force)..."
    Remove-Item -Recurse -Force -ErrorAction SilentlyContinue $targetAvdDir
    Remove-Item -Force -ErrorAction SilentlyContinue $targetIni
}

New-Item -ItemType Directory -Force -Path $targetAvdDir | Out-Null

# Tablet overrides applied on top of the cloned phone config.
$overrides = [ordered]@{
    'AvdId'                  = $Name
    'avd.ini.displayname'    = 'NotePen Tablet API 36.1'
    'hw.lcd.width'           = '1600'
    'hw.lcd.height'          = '2560'
    'hw.lcd.density'         = '320'
    'skin.name'              = '1600x2560'
    'skin.path'              = '1600x2560'
    'hw.initialOrientation'  = 'landscape'
    'hw.device.name'         = 'Tablet'
    'hw.device.manufacturer' = 'Generic'
    'hw.ramSize'             = '4096'
}
$dropKeys = @('hw.device.hash2')  # device-profile hash would fight our explicit geometry

$applied = @{}
$lines = New-Object System.Collections.Generic.List[string]
foreach ($line in Get-Content $srcConfig) {
    $key = ($line -split '=', 2)[0]
    if ($dropKeys -contains $key) { continue }
    if ($overrides.Contains($key)) {
        $lines.Add("$key=$($overrides[$key])")
        $applied[$key] = $true
    } else {
        $lines.Add($line)
    }
}
# Append any overrides that weren't present in the source.
foreach ($k in $overrides.Keys) {
    if (-not $applied.ContainsKey($k)) { $lines.Add("$k=$($overrides[$k])") }
}

$targetConfig = Join-Path $targetAvdDir 'config.ini'
Set-Content -Path $targetConfig -Value $lines -Encoding ASCII

# The .ini pointer file that the emulator/adb use to discover the AVD.
$iniLines = @(
    'avd.ini.encoding=UTF-8',
    "path=$targetAvdDir",
    "path.rel=avd\$Name.avd",
    'target=android-36.1'
)
Set-Content -Path $targetIni -Value $iniLines -Encoding ASCII

Write-Host "Created tablet AVD '$Name' (1600x2560 @ 320dpi, ~800dp)."
Write-Host "Boot it with:"
Write-Host "  & `"$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe`" -avd $Name"
