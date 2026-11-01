# Standalone Release & Versioning Rule

## Core Rules for APK Build & GitHub Deployment

1. **Versioning Scheme:**
   - **Version Name:** Always increment the patch version by +1 (e.g., `1.13.0` $\rightarrow$ `1.13.1` $\rightarrow$ `1.13.2` $\rightarrow$ `1.13.3` $\rightarrow$ `1.13.4` ...), unless a specific version name is requested by the user.
   - **Version Code:** Always increment `versionCode` by +1 with every release.
   - **Synced Files:**
     - `app/build.gradle.kts`: `versionCode` and `versionName`
     - Root `version_name`: Exact version name (e.g. `1.13.4`)
     - `apk/version_name`: Exact version name (e.g. `1.13.4`)
     - `AppUpdateManager.kt`: `DEFAULT_LATEST_VERSION_CODE` and `DEFAULT_LATEST_VERSION_NAME`

2. **APK Preservation & Naming in GitHub (`apk/` directory):**
   - **NEVER delete or replace previous version APKs.**
   - All previously built APKs must remain in the `apk/` folder permanently (e.g., `apk/1.13.3.apk`, `apk/1.13.4.apk`, etc.).
   - Each newly built APK is saved with its version name: `apk/<version_name>.apk`.
   - Also update `apk/the-boyz-latest.apk` as a convenience pointer to the latest build.

3. **In-App Updater Logic:**
   - The app checks GitHub's `apk/version_name` file.
   - If `remoteVersion > currentInstalledVersion`, the app displays **"Updates Available to download"**.
   - If `remoteVersion == currentInstalledVersion`, the app displays **"No updates available"**.
   - When downloading an update, the app dynamically constructs the versioned download URL:
     `https://github.com/devendrav2324-netizen/the-boyz/raw/main/apk/<remote_version_name>.apk`
   - Download history is recorded in local SQLite (`app_update_history` table) with exact version and timestamp.

4. **Release Checklist:**
   1. Update version code and name in `app/build.gradle.kts`, `version_name`, and `apk/version_name`.
   2. Run unit tests:
      ```powershell
      $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest
      ```
   3. Build APK:
      ```powershell
      $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
      ```
   4. Copy the compiled APK:
      ```powershell
      Copy-Item "app\build\outputs\apk\debug\app-debug.apk" -Destination "apk\<version_name>.apk"
      Copy-Item "app\build\outputs\apk\debug\app-debug.apk" -Destination "apk\the-boyz-latest.apk"
      ```
   5. Commit and push:
      ```powershell
      git add .
      git commit -m "feat(release): v<version_name> - <summary of changes>"
      git push origin main
      ```
