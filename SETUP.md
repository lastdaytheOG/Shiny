# Setup Instructions

This document provides instructions for setting up the Shiny Music project for development.

## Prerequisites

- Android Studio (latest version recommended)
- Android SDK (API level as specified in `build.gradle.kts`)
- JDK 21
- Git

## Initial Setup

### 1. Clone the Repository

```bash
git clone https://github.com/lastdaytheOG/Shiny.git
cd Shiny
```

### 2. Configure Local Properties

Create a `local.properties` file from the template:

```bash
cp local.properties.template local.properties
```

Edit `local.properties` and set your Android SDK path:

```properties
sdk.dir=/path/to/your/android/sdk
```

**Example paths:**

- macOS: `/Users/username/Library/Android/sdk`
- Linux: `/home/username/Android/sdk`
- Windows: `C:\\Users\\username\\AppData\\Local\\Android\\sdk`

### 3. Configure Firebase (Optional)

Firebase is used for analytics and crash reporting. If you want to use these features:

1. Create a Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Add an Android app to your Firebase project
3. Download the `google-services.json` file
4. Place it in the `app/` directory

**Note:** If you skip Firebase setup, the app will still build and run, but analytics and crash reporting will be disabled.

### 4. Configure Release Signing (Optional)

Release builds are signed with Shiny's own key (PKCS12, alias `shiny-release`). CI decodes it from the `SHINY_KEYSTORE_BASE64` and `SHINY_KEYSTORE_PASSWORD` repository secrets. To sign a release locally, point these environment variables at your copy of the keystore, kept outside the repository:

```bash
export SHINY_KEYSTORE_PATH=/path/outside/the/repo/shiny-release.jks
export SHINY_KEYSTORE_PASSWORD=your_keystore_password
```

Without them, release builds stop at signing. Debug builds don't need them. Never commit the keystore or its password.

### 5. Build the Project

Open the project in Android Studio or build from the command line.

Shiny Music supports FOSS and GMS build variants.

```bash
# Debug build
./gradlew assembleUniversalGmsDebug

# Release build (requires signing configuration)
./gradlew assembleUniversalGmsRelease
```

*(On Windows, use `.\gradlew.bat` instead of `./gradlew`)*

## Important Files

### Confidential Files (Never commit these)

- `local.properties` - Contains your local SDK path
- `app/google-services.json` - Contains Firebase credentials
- `*.keystore` - Contains signing keys for release builds
- `gradle.properties` - May contain signing credentials

These files are already listed in `.gitignore` and should never be committed to version control.

### Template Files (Safe to commit)

- `local.properties.template` - Template for local properties
- `app/google-services.json` - Optional Firebase configuration

## Troubleshooting

### Build Fails with "SDK location not found"

Make sure you've created `local.properties` with the correct SDK path.

### Firebase-related Build Errors

If you're not using Firebase, you can still build the standard debug variant without `app/google-services.json` — Firebase features will simply be disabled:

```bash
./gradlew assembleUniversalGmsDebug
```

### Gradle Sync Issues

Try cleaning and rebuilding:

```bash
./gradlew clean
./gradlew build
```

## Contributing

Please read [CONTRIBUTING.md](CONTRIBUTING.md) for details on our code of conduct and the process for submitting pull requests.

## License

This project is licensed under the GNU General Public License v3.0 - see the [LICENSE](LICENSE) file for details.