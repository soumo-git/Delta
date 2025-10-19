## Parent Android App

Go to Firebase Console → **Project settings** → **Your apps** → **Android app**.

Add an Android app with package name `com.soumo.parentandroid`.

### Files to Update

- Download the generated `google-services.json` and place it at `ParentAndroid/app/google-services.json`.

- Update `ParentAndroid/app/src/main/java/com/soumo/parentandroid/AppConf.kt` **DATABASE_URL** with your Firebase Realtime Database URL.

- Update `ParentAndroid/app/src/main/java/com/soumo/parentandroid/AppConf.kt` **RENDER_API_URL** with your Render API URL.

### Build & Install

1. Open `ParentAndroid/` in Android Studio.

2. Sync Gradle. (Wait for it to finish, it can take a while)

3. Connect the parent device. (USB debugging must be enabled)

4. Run the app (debug build) or build releases via `./gradlew assembleRelease`. (Wait for it to finish, it can take a while)

5. Install the app on the parent device. (You can use adb or any other method)

---

Thats it! You have successfully set up the Parent Android app. 🎉