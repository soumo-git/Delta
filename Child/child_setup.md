## Child Android App

Go to Firebase Console → **Project settings** → **Your apps** → **Android app**.

Add an Android app with package name `com.soumo.child`.

### Files to Update

- Download the generated `google-services.json` and place it at `Child/app/google-services.json`.
- Update `Child/app/src/main/java/com/soumo/child/configuration/AppConfig.kt` **DATABASE_URL** with your Firebase Realtime Database URL.

### Build & Install

1. Open Android Studio → **Open** → select `Child/`.

2. Let Gradle sync. Resolve any SDK prompts.

3. Connect the child device via USB.

4. Click **Run** (Shift + F10) or execute `./gradlew installDebug`.

5. Confirm the app installs (icon will hide after your stealth command).

---

That's it! You've successfully set up the child Android app. 🎉