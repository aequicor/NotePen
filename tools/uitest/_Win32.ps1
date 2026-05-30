<#
    Shared Win32 helpers for the desktop side of the UI-testing harness.
    Dot-source this file:  . "$PSScriptRoot\_Win32.ps1"

    Provides:
      Get-UiTestWindow -TitleLike '*NotePen*'   -> [pscustomobject] @{ Hwnd; Title; X; Y; Width; Height }
#>

if (-not ([System.Management.Automation.PSTypeName]'UiTest.Win32').Type) {
    Add-Type @"
using System;
using System.Text;
using System.Collections.Generic;
using System.Runtime.InteropServices;

namespace UiTest {
    public class Win32 {
        [StructLayout(LayoutKind.Sequential)]
        public struct RECT { public int Left, Top, Right, Bottom; }

        [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT r);
        [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr hWnd);
        [DllImport("user32.dll", CharSet = CharSet.Unicode)] public static extern int GetWindowText(IntPtr hWnd, StringBuilder s, int n);
        [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr hWnd);
        [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
        [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
        [DllImport("user32.dll")] public static extern bool IsIconic(IntPtr hWnd);
        public const int SW_RESTORE = 9;

        public delegate bool EnumProc(IntPtr hWnd, IntPtr lParam);
        [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr lParam);

        public static List<IntPtr> Visible() {
            var list = new List<IntPtr>();
            EnumWindows((h, p) => { if (IsWindowVisible(h)) list.Add(h); return true; }, IntPtr.Zero);
            return list;
        }
        public static string Text(IntPtr h) {
            int n = GetWindowTextLength(h);
            if (n == 0) return "";
            var sb = new StringBuilder(n + 1);
            GetWindowText(h, sb, sb.Capacity);
            return sb.ToString();
        }
    }
}
"@
}

# Default is an EXACT match on "NotePen" — the desktop app sets `title = "NotePen"` (main.kt).
# A wildcard like '*NotePen*' would also match the IntelliJ IDEA project window
# ("NotePen – Commit: ..."), so prefer exact unless the caller overrides.
function Get-UiTestWindow {
    param([string]$TitleLike = 'NotePen')
    foreach ($h in [UiTest.Win32]::Visible()) {
        $title = [UiTest.Win32]::Text($h)
        # Guard against IDE-style titles ("NotePen – ...") even if a wildcard is passed.
        if ($title -like $TitleLike -and $title -notmatch '[–-]\s') {
            $r = New-Object UiTest.Win32+RECT
            if ([UiTest.Win32]::GetWindowRect($h, [ref]$r)) {
                $w = $r.Right - $r.Left
                $hgt = $r.Bottom - $r.Top
                return [pscustomobject]@{
                    Hwnd = $h; Title = $title; X = $r.Left; Y = $r.Top; Width = $w; Height = $hgt
                }
            }
        }
    }
    return $null
}

# Bring a window to the foreground, restoring it first if it's minimized (a minimized window sits at
# X/Y = -32000 and SetForegroundWindow alone won't restore it). Returns refreshed window bounds.
function Show-UiTestWindow {
    param([Parameter(Mandatory = $true)][IntPtr]$Hwnd, [string]$TitleLike = 'NotePen')
    if ([UiTest.Win32]::IsIconic($Hwnd)) {
        [void][UiTest.Win32]::ShowWindow($Hwnd, [UiTest.Win32]::SW_RESTORE)
        Start-Sleep -Milliseconds 300
    }
    [void][UiTest.Win32]::SetForegroundWindow($Hwnd)
    Start-Sleep -Milliseconds 300
    return (Get-UiTestWindow -TitleLike $TitleLike)
}
