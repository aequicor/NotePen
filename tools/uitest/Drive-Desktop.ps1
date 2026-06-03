# Helper to drive the NotePen desktop window without computer-use.
# Usage:
#   Drive-Desktop.ps1 -Action focus
#   Drive-Desktop.ps1 -Action capture -Out shot.png
#   Drive-Desktop.ps1 -Action click -X 18 -Y 301        # client-relative px (physical)
#   Drive-Desktop.ps1 -Action doubleClick -X 18 -Y 301
#   Drive-Desktop.ps1 -Action drag -X 200 -Y 500 -X2 700 -Y2 520 -DurationMs 600
#   Drive-Desktop.ps1 -Action scroll -X 500 -Y 500 -Delta -720
#   Drive-Desktop.ps1 -Action type -Text "C:\tmp\fixture.pdf"
#   Drive-Desktop.ps1 -Action key -Key "{ENTER}"
#   Drive-Desktop.ps1 -Action minimize
param(
  [ValidateSet('focus','capture','click','doubleClick','drag','scroll','type','key','rect','minimize','maximize','restore','hide','show')] [string]$Action = 'focus',
  [int]$X = 0, [int]$Y = 0,
  [int]$X2 = 0, [int]$Y2 = 0,
  [int]$DurationMs = 400,
  [int]$Delta = -120,
  [string]$Text = '',
  [string]$Key = '',
  [string]$Out = "$PSScriptRoot\out\desktop\drive.png",
  [string]$Title = 'NotePen'
)

Add-Type -AssemblyName System.Windows.Forms
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @"
using System;
using System.Drawing;
using System.Drawing.Imaging;
using System.Runtime.InteropServices;
using System.Text;
public class Drv {
  [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L,T,R,B; }
  [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X,Y; }
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr l);
  [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
  [DllImport("user32.dll")] public static extern int GetWindowTextLength(IntPtr h);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder s, int n);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int n);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
  [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
  [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
  [DllImport("user32.dll")] public static extern void mouse_event(uint f, uint dx, uint dy, uint d, IntPtr e);
  const uint LEFTDOWN = 0x0002;
  const uint LEFTUP = 0x0004;
  const uint WHEEL = 0x0800;
  public delegate bool EnumProc(IntPtr h, IntPtr l);
  public static IntPtr Find(string title) {
    IntPtr found = IntPtr.Zero;
    EnumWindows((h,l)=>{
      int len=GetWindowTextLength(h); var sb=new StringBuilder(len+1); GetWindowText(h,sb,sb.Capacity);
      if (sb.ToString()==title) { found=h; return false; }
      return true;
    }, IntPtr.Zero);
    return found;
  }
  public static int[] ClientRectScreen(IntPtr h){
    RECT r; GetClientRect(h, out r);
    POINT tl; tl.X=r.L; tl.Y=r.T; ClientToScreen(h, ref tl);
    return new int[]{ tl.X, tl.Y, r.R-r.L, r.B-r.T };
  }
  public static void Click(int sx, int sy){
    SetCursorPos(sx, sy);
    System.Threading.Thread.Sleep(40);
    mouse_event(LEFTDOWN,0,0,0,IntPtr.Zero);
    System.Threading.Thread.Sleep(30);
    mouse_event(LEFTUP,0,0,0,IntPtr.Zero);
  }
  public static void DoubleClick(int sx, int sy){
    Click(sx, sy);
    System.Threading.Thread.Sleep(90);
    Click(sx, sy);
  }
  public static void Drag(int sx, int sy, int ex, int ey, int durationMs){
    int steps = Math.Max(8, Math.Min(80, durationMs / 12));
    SetCursorPos(sx, sy);
    System.Threading.Thread.Sleep(40);
    mouse_event(LEFTDOWN,0,0,0,IntPtr.Zero);
    for (int i = 1; i <= steps; i++) {
      double t = (double)i / steps;
      int x = sx + (int)Math.Round((ex - sx) * t);
      int y = sy + (int)Math.Round((ey - sy) * t);
      SetCursorPos(x, y);
      System.Threading.Thread.Sleep(Math.Max(1, durationMs / steps));
    }
    mouse_event(LEFTUP,0,0,0,IntPtr.Zero);
  }
  public static void Scroll(int sx, int sy, int delta){
    SetCursorPos(sx, sy);
    System.Threading.Thread.Sleep(40);
    mouse_event(WHEEL,0,0,(uint)delta,IntPtr.Zero);
  }
  public static void Capture(IntPtr h, string path){
    int[] c = ClientRectScreen(h);
    var bmp = new Bitmap(c[2], c[3], PixelFormat.Format32bppArgb);
    using(var g = Graphics.FromImage(bmp)){ g.CopyFromScreen(c[0], c[1], 0, 0, new Size(c[2], c[3])); }
    bmp.Save(path, ImageFormat.Png);
  }
}
"@

$h = [Drv]::Find($Title)
if ($h -eq [IntPtr]::Zero) { Write-Output "WINDOW NOT FOUND: $Title"; exit 1 }
[Drv]::ShowWindow($h, 5) | Out-Null   # SW_SHOW
Start-Sleep -Milliseconds 250
[Drv]::BringWindowToTop($h) | Out-Null
[Drv]::SetForegroundWindow($h) | Out-Null
Start-Sleep -Milliseconds 150
$c = [Drv]::ClientRectScreen($h)

switch ($Action) {
  'focus'   { Write-Output ("FOCUSED hwnd={0} client=({1},{2}) size={3}x{4}" -f $h,$c[0],$c[1],$c[2],$c[3]) }
  'rect'    { Write-Output ("client_origin=({0},{1}) size={2}x{3}" -f $c[0],$c[1],$c[2],$c[3]) }
  'capture' { New-Item -ItemType Directory -Force (Split-Path $Out) | Out-Null; [Drv]::Capture($h, $Out); Write-Output "SAVED $Out ($($c[2])x$($c[3]))" }
  'click'   { $sx=$c[0]+$X; $sy=$c[1]+$Y; [Drv]::Click($sx,$sy); Write-Output ("CLICK client=({0},{1}) screen=({2},{3})" -f $X,$Y,$sx,$sy) }
  'doubleClick' { $sx=$c[0]+$X; $sy=$c[1]+$Y; [Drv]::DoubleClick($sx,$sy); Write-Output ("DOUBLE_CLICK client=({0},{1}) screen=({2},{3})" -f $X,$Y,$sx,$sy) }
  'drag'    { $sx=$c[0]+$X; $sy=$c[1]+$Y; $ex=$c[0]+$X2; $ey=$c[1]+$Y2; [Drv]::Drag($sx,$sy,$ex,$ey,$DurationMs); Write-Output ("DRAG client=({0},{1})->({2},{3}) durationMs={4}" -f $X,$Y,$X2,$Y2,$DurationMs) }
  'scroll'  { $sx=$c[0]+$X; $sy=$c[1]+$Y; [Drv]::Scroll($sx,$sy,$Delta); Write-Output ("SCROLL client=({0},{1}) delta={2}" -f $X,$Y,$Delta) }
  'type'    { [System.Windows.Forms.SendKeys]::SendWait($Text); Write-Output ("TYPE chars={0}" -f $Text.Length) }
  'key'     { [System.Windows.Forms.SendKeys]::SendWait($Key); Write-Output ("KEY {0}" -f $Key) }
  'minimize' { [Drv]::ShowWindow($h, 6) | Out-Null; Write-Output "MINIMIZED hwnd=$h" }
  'maximize' { [Drv]::ShowWindow($h, 3) | Out-Null; Write-Output "MAXIMIZED hwnd=$h" }
  'restore' { [Drv]::ShowWindow($h, 9) | Out-Null; Write-Output "RESTORED hwnd=$h" }
  'hide'    { [Drv]::ShowWindow($h, 0) | Out-Null; Write-Output "HIDDEN hwnd=$h" }
  'show'    { [Drv]::ShowWindow($h, 5) | Out-Null; Write-Output "SHOWN hwnd=$h" }
}
