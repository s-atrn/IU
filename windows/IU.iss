[Setup]
AppName=IU
AppVersion=1.0.0
AppPublisher=IU
DefaultDirName={autopf}\IU
DefaultGroupName=IU
OutputDir=installer
OutputBaseFilename=IU Setup
SetupIconFile=resources\iu.ico
UninstallDisplayIcon={app}\IU.exe
Compression=lzma
SolidCompression=yes
PrivilegesRequired=admin
ArchitecturesInstallIn64BitMode=x64compatible
DisableProgramGroupPage=yes

[Files]
Source: "dist\IU.exe"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\IU"; Filename: "{app}\IU.exe"; IconFilename: "{app}\IU.exe"
Name: "{autodesktop}\IU"; Filename: "{app}\IU.exe"; IconFilename: "{app}\IU.exe"
Name: "{userstartup}\IU"; Filename: "{app}\IU.exe"; IconFilename: "{app}\IU.exe"

[UninstallDelete]
Type: filesandordirs; Name: "{app}"