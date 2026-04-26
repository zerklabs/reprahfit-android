# Reprahfit

Android app for tracking outdoor recumbent cycling with GPS, heart rate monitoring, and Health Connect integration.

## Requirements

- **JDK 17** or higher
- **Android SDK** with:
  - Build Tools 36
  - Platform SDK 36 (Android 16)
  - Platform SDK 34 (minimum supported)
- **Android device** running Android 14 (API 34) or higher

## Setup

### Ubuntu

1. **Install JDK 17:**
   ```bash
   sudo apt update
   sudo apt install openjdk-17-jdk
   ```

2. **Install Android SDK** (via Android Studio or command line):

   **Option A: Android Studio (recommended)**
   - Download from https://developer.android.com/studio
   - Extract and run `studio.sh`
   - SDK Manager will install required components

   **Option B: Command line tools**
   ```bash
   # Download command line tools
   wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
   unzip commandlinetools-linux-*.zip -d ~/android-sdk

   # Set environment variables (add to ~/.bashrc)
   export ANDROID_HOME=$HOME/android-sdk
   export PATH=$PATH:$ANDROID_HOME/cmdline-tools/bin:$ANDROID_HOME/platform-tools

   # Install required SDK components
   sdkmanager --sdk_root=$ANDROID_HOME "platform-tools" "platforms;android-36" "platforms;android-34" "build-tools;36.0.0"
   ```

3. **Build the project:**
   ```bash
   ./gradlew assembleDebug
   ```

### Windows

1. **Install JDK 17:**
   - Download from https://adoptium.net/temurin/releases/?version=17
   - Run installer, ensure "Set JAVA_HOME" is checked

2. **Install Android Studio:**
   - Download from https://developer.android.com/studio
   - Run installer
   - Open SDK Manager (Tools > SDK Manager) and install:
     - Android SDK Platform 36
     - Android SDK Platform 34
     - Android SDK Build-Tools 36

3. **Build the project:**
   ```powershell
   .\gradlew.bat assembleDebug
   ```

## Building

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on connected device
./gradlew installDebug
```

Build outputs are in `app/build/outputs/apk/`.

## Device Setup

1. Enable Developer Options: Settings > About phone > tap "Build number" 7 times
2. Enable USB Debugging: Settings > Developer options > USB debugging
3. Connect via USB and authorize the computer when prompted

## Features

- GPS-based speed and distance tracking
- Bluetooth heart rate monitor support
- Calorie estimation with recumbent cycling adjustment
- Health Connect integration for syncing rides
- Simple and detailed dashboard views

## License

See [LICENSE](LICENSE) file.
