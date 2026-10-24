# Building Shiny

Everything needed to get from a fresh clone to an APK on your phone.

## You need

- **JDK 21**
- **Android SDK** with platform 36. Android Studio installs it for you.
- **Git**

## 1. Get the code

```bash
git clone https://github.com/lastdaytheOG/Shiny.git
cd Shiny
```

## 2. Point Gradle at the SDK

```bash
cp local.properties.template local.properties
```

Open `local.properties` and set `sdk.dir`:

| System | Usual location |
| :-- | :-- |
| Windows | `C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` |
| macOS | `/Users/<you>/Library/Android/sdk` |
| Linux | `/home/<you>/Android/Sdk` |

Android Studio writes this file by itself when you open the project.

## 3. Pick a build

A build is named `assemble` + ABI + variant + type.

| Part | Choices |
| :-- | :-- |
| ABI | `Universal` (every phone), `Arm64`, `Armeabi`, `X86`, `X86_64` |
| Variant | `Foss` (no Google services) or `Gms` (Google sign-in, Firebase) |
| Type | `Debug` or `Release` |

To start, build the one that needs nothing else:

```bash
./gradlew assembleUniversalFossDebug
```

The APK lands in `app/build/outputs/apk/`. On Windows use `gradlew.bat`. For the emulator, `X86_64` builds are smaller and faster to install.

## 4. Optional: the `gms` variant

`gms` builds add Google sign-in for Shiny Social, crash reports and playback-health events. Without a `google-services.json` a `gms` build still compiles, but those features have nothing to talk to. To make them work, use your own Firebase project:

1. Create a project in the [Firebase console](https://console.firebase.google.com/).
2. Add two Android apps: `com.shiny.music` and `com.shiny.music.debug`.
3. Add the SHA-1 of the key you sign with to each, or Google sign-in will fail.
4. Download `google-services.json` into `app/`.

## 5. Optional: release builds

Release builds are signed, and stop at the signing step without a key. Create your own keystore, keep it outside the repository, and tell Gradle where it is:

```bash
export SHINY_KEYSTORE_PATH=/somewhere/outside/the/repo/my-release.jks
export SHINY_KEYSTORE_PASSWORD=...
./gradlew assembleUniversalGmsRelease
```

A build signed with your key can't be installed over an official Shiny release; Android treats it as a different app's update. Uninstall the official one first, or use a debug build, which installs alongside it.

## Files that never go in a commit

`local.properties`, `app/google-services.json`, any `*.jks` or `*.keystore`, and their passwords. `.gitignore` already covers them.

## When it doesn't build

| Message | Fix |
| :-- | :-- |
| `SDK location not found` | `local.properties` is missing, or `sdk.dir` is wrong |
| Google sign-in fails in your build | The SHA-1 of your signing key isn't registered in your Firebase project (step 4) |
| Unsupported class file version, or a toolchain error | Gradle is running on the wrong Java. Set `JAVA_HOME` to a JDK 21 |
| Out of memory | Close other apps, or lower `org.gradle.jvmargs` in `gradle.properties` |
| Something stale after switching branches | `./gradlew clean`, then build again |

Next: [CONTRIBUTING.md](CONTRIBUTING.md) if you plan to send a change.
