#ifndef SourceDir
#error SourceDir is required
#endif
[Setup]
AppId=RehabSaaSClient.Development
AppName=Rehab SaaS Client DEV
AppVersion=2.0.0-dev
VersionInfoVersion=2.0.0.0
AppPublisher=Rehab SaaS Development
DefaultDirName={localappdata}\Programs\RehabSaaSClient-DEV
DefaultGroupName=Rehab SaaS Client DEV
PrivilegesRequired=lowest
OutputDir={#OutputDir}
OutputBaseFilename={#OutputName}
Compression=lzma2
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
UninstallDisplayIcon={app}\RehabSaaSClient-DEV.exe
[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs
[Icons]
Name: "{group}\Rehab SaaS Client DEV"; Filename: "{app}\RehabSaaSClient-DEV.exe"
[Run]
Filename: "{app}\RehabSaaSClient-DEV.exe"; Description: "Open SaaS development connector"; Flags: nowait postinstall skipifsilent
