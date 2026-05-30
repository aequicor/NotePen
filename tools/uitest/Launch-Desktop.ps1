<#
.SYNOPSIS
    Launch the NotePen desktop app (./gradlew runDesktop) and wait for its window to appear.

.DESCRIPTION
    Starts the Gradle desktop run in the background (logging to a file) and polls for the JBR app
    window titled "NotePen". On success prints the window handle + screen bounds, which the
    computer-use driver and Capture-DesktopAnim.ps1 use.

    NOTE for Claude Code: drive this window via computer-use by granting `java.exe` (the JBR dev
    window) — NOT the installed "NotePen" app. See _Win32.ps1 / README.md.

.PARAMETER TimeoutSec
    How long to wait for the window (default 240; first run also compiles).

.PARAMETER LogFile
    Where the Gradle output is written (default tools/uitest/out/desktop/run.log).

.EXAMPLE
    ./Launch-Desktop.ps1
#>
[CmdletBinding()]
param(
    [int]$TimeoutSec = 240,
    [string]$LogFile
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\_Win32.ps1"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
if (-not $LogFile) { $LogFile = Join-Path $PSScriptRoot 'out\desktop\run.log' }
$logDir = Split-Path -Parent $LogFile
if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Force -Path $logDir | Out-Null }

$existing = Get-UiTestWindow -TitleLike 'NotePen'
if ($existing) {
    Write-Host "NotePen window already open (hwnd=$($existing.Hwnd))."
    $existing | Format-List | Out-String | Write-Host
    return $existing
}

$gradlew = Join-Path $repoRoot 'gradlew.bat'
Write-Host "Starting: $gradlew runDesktop  (log -> $LogFile)"
$proc = Start-Process -FilePath $gradlew -ArgumentList 'runDesktop' -WorkingDirectory $repoRoot `
    -RedirectStandardOutput $LogFile -RedirectStandardError "$LogFile.err" -WindowStyle Hidden -PassThru
Write-Host "Gradle PID = $($proc.Id). Waiting up to ${TimeoutSec}s for the app window..."

$deadline = (Get-Date).AddSeconds($TimeoutSec)
while ((Get-Date) -lt $deadline) {
    if ($proc.HasExited) {
        Write-Warning "Gradle exited early (code $($proc.ExitCode)). Tail of log:"
        if (Test-Path $LogFile) { Get-Content $LogFile -Tail 25 | Write-Host }
        if (Test-Path "$LogFile.err") { Get-Content "$LogFile.err" -Tail 25 | Write-Host }
        throw "runDesktop exited before a window appeared."
    }
    $win = Get-UiTestWindow -TitleLike 'NotePen'
    if ($win) {
        Write-Host "App window is up."
        $win | Format-List | Out-String | Write-Host
        Write-Host "Gradle PID $($proc.Id) keeps running while the app is open; stop it with: Stop-Process -Id $($proc.Id)"
        return $win
    }
    Start-Sleep -Milliseconds 1500
}
throw "Timed out after ${TimeoutSec}s waiting for the NotePen window. See $LogFile."
