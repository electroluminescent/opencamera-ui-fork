# Open Camera UI Fork

UX-focused fork of [Open Camera](https://sourceforge.net/projects/opencamera/) by Mark Harman.
Licensed GPL v3 or later, same as upstream (see `gpl-3.0.txt`). Any APK you distribute must come
with this fork's source.

- Upstream base: v1.56.2 (`upstream` remote, `master` branch)
- Fork work: `ux` branch
- App ID: `net.sourceforge.opencamera.uifork` (installs alongside the official app)
- Fork version: `1.56.2-uifork.0.1.0`

## Build (Windows)

Toolchain lives in `C:\Android` (Temurin JDK 17, Android SDK 36, build-tools 36.0.0).

```
powershell -ExecutionPolicy Bypass -File C:\Android\build-fork.ps1
```

The script mirrors this folder to `C:\Android\build\open-camera-ui-fork` (Gradle is unreliable on the
network share), runs `assembleDebug`, and writes the log to `C:\Android\build.log`.

## Pull upstream updates

```
git fetch upstream
git merge upstream/master
```
