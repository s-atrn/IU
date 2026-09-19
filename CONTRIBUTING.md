# Contributing to IU

Thanks for your interest in contributing to IU.

IU started as a personal project to make wireless file transfers between a PC and Android devices more convenient. It is open source so others can use it, suggest improvements, report problems, and contribute code.

## Getting Started

Clone the repository:

```bash
git clone git@github.com:s-atrn/IU.git
cd IU
```

The project is divided into separate Android and Windows components:

```text
IU/
├── android/
├── windows/
├── README.md
└── CONTRIBUTING.md
```

## Prerequisites

### Android

You will need:

* Android SDK
* Java Development Kit (JDK)
* Android Studio is recommended for Android development

The project includes the Gradle wrapper, so you do **not** need to install Gradle separately.

### Windows

You will need:

* Python
* PyInstaller - `pip install pyinstaller`
* **[Inno Setup](https://jrsoftware.org/isdl.php)** — required to build the Windows installer

Make sure Python and PyInstaller are available from your terminal before building.

## Building IU

### Android APK

From the project root, run:

```powershell
cd android
.\gradlew.bat assembleDebug
```

The generated APK will be located in:

```text
android\app\build\outputs\apk\debug\
```

For a release build:

```powershell
.\gradlew.bat assembleRelease
```

The release APK will be located in:

```text
android\app\build\outputs\apk\release\
```

### Windows Application

From the project root, run:

```powershell
cd windows
python -m PyInstaller --onefile --noconsole --name IU iu.py
```

The packaged Windows application will be generated in:

```text
windows\dist\
```

### Windows Installer

**Inno Setup is required for this step.**

After building the Windows application, run:

```powershell
cd windows
iscc IU.iss
```

Or, in Windows Explorer, just right click `IU.iss` -> compile

The installer will be generated according to the output configuration defined in `IU.iss`.

## Making Changes

Keep changes focused on the problem you are trying to solve.

Examples:

* Android functionality or UI → `android/`
* Windows functionality → `windows/`

Avoid mixing unrelated changes into the same commit.

## Testing

Please test changes on the platform they affect.

For changes involving file transfers, test both sides of the connection where possible.

Important functionality to check includes:

* Single-file transfers
* Multiple-file transfers
* Duplicate filenames
* Device discovery
* Android share sheet integration
* Android Quick Settings tile
* Windows system-tray functionality
* Windows startup behavior

## Pull Requests

When submitting a pull request:

1. Explain what you changed.
2. Explain why the change was made.
3. Mention anything that requires additional testing.
4. Keep the pull request focused on the relevant change.

Screenshots, logs, or other useful information are welcome when they help explain a change or demonstrate a fix.

## Issues

If you find a bug or have an idea for a feature, open an issue.

For bug reports, include:

* What you were trying to do
* What you expected to happen
* What actually happened
* The device and platform involved
* Steps to reproduce the issue, if possible

For feature requests, explain the problem the feature would solve as well as your proposed solution.

## Generated Files

Do not commit generated build output, caches, installers, APKs, EXEs, or other files excluded by `.gitignore`.

Compiled application packages are distributed through GitHub Releases.

## Code Style

There is currently no strict formal style guide for IU.

Keep code readable, reasonably simple, and consistent with the surrounding code. Avoid unnecessary dependencies or complexity when a simpler solution is sufficient.

## Discussion

If you are unsure about an approach, open an issue or start a discussion before making a large change.

Ideas, experimentation, and alternative approaches are welcome. IU is still evolving, and there may be better ways to solve problems than the current implementation.

