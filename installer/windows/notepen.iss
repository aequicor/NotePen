; Inno Setup script for NotePen (Windows).
;
; Wraps the Compose app-image produced by
;   :app:byCompose:desktop:createReleaseDistributable
; into a per-user installer. jpackage's TargetFormat.Exe was dropped because
; jpackage cannot add a final "Launch NotePen" checkbox nor an opt-in shortcut
; checkbox; Inno Setup provides both ([Run] postinstall + [Tasks]).
;
; Overridable defines (passed from CI via ISCC /D...); the defaults let a dev
; compile locally straight after createReleaseDistributable:
;   AppVersion - product version, e.g. 1.2.3
;   AppDir     - path to the built app-image folder (contains NotePen.exe)
;   SetupIcon  - path to the .ico used for the installer wizard

#ifndef AppVersion
  #define AppVersion "0.0.0-dev"
#endif
#ifndef AppDir
  #define AppDir "..\..\app\byCompose\desktop\build\compose\binaries\main-release\app\NotePen"
#endif
#ifndef SetupIcon
  #define SetupIcon "..\..\app\byCompose\desktop\icons\app_icon.ico"
#endif

#define AppName "NotePen"
#define AppExe "NotePen.exe"
#define AppPublisher "KYamshanov"

[Setup]
; Stable AppId so future versions upgrade in place instead of installing twice.
AppId={{B7E4D2A1-9C3F-4E58-A6D2-1F0B8C7E5A43}
AppName={#AppName}
AppVersion={#AppVersion}
AppPublisher={#AppPublisher}
; per-user install => no administrator rights; {auto*} resolve to user locations.
PrivilegesRequired=lowest
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExe}
SetupIconFile={#SetupIcon}
OutputDir=Output
OutputBaseFilename={#AppName}-{#AppVersion}
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"

[Files]
Source: "{#AppDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExe}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExe}"; Description: "{cm:LaunchProgram,{#AppName}}"; Flags: nowait postinstall skipifsilent

; Register NotePen as an "Open with" handler for .pdf (parity with the former
; jpackage fileAssociation). Per-user => HKCU; the app receives the file path as
; argv[0] of main(), matching jpackage's behaviour. Does not hijack the default.
[Registry]
Root: HKCU; Subkey: "Software\Classes\NotePen.pdf"; ValueType: string; ValueName: ""; ValueData: "PDF Document"; Flags: uninsdeletekey
Root: HKCU; Subkey: "Software\Classes\NotePen.pdf\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\{#AppExe},0"
Root: HKCU; Subkey: "Software\Classes\NotePen.pdf\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#AppExe}"" ""%1"""
Root: HKCU; Subkey: "Software\Classes\.pdf\OpenWithProgids"; ValueType: string; ValueName: "NotePen.pdf"; ValueData: ""; Flags: uninsdeletevalue
