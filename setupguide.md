# 🚀 Project Delta Setup Guide

Welcome to the official setup manual for Project Delta — a multi-application parental monitoring platform. This guide walks you from a blank machine to a fully operational stack covering Firebase, the email microservice, Android builds, and the Electron desktop client.

## Project Overview

Project Delta is composed of four cooperating applications plus shared cloud services:

- Child Android app (`Child/`): Stealth agent running on the monitored device.
- Parent Android app (`ParentAndroid/`): Mobile dashboard alternative.
- Parent Electron desktop app (`ParentElectronApp/`): Desktop dashboard with WebRTC controls.
- Email microservice (`email-service/`): Sends OTP, password reset, and test emails via Gmail SMTP.
- Firebase project: Authentication, Realtime Database signaling, and shared state.

### Repository Layout

```text
Project Delta/
├── Child/                        # Kotlin Android project (monitored device).
├── ParentAndroid/                # Kotlin Android project (parent dashboard).
├── ParentElectronApp/            # Electron desktop client.
├── email-service/                # Node.js email microservice.
├── README.md                     # Product overview.
└── setupguide.md                 # Setup guide.
```

## Prepare Your Environment

### Operating System & Hardware

- Windows 10/11 (recommended to match build scripts) or macOS/Linux for server work.
- One Android 10+ device for the child app (physical recommended).
- One Android 10+ device for the parent Android app (optional if you use the Electron client).
- Sufficient disk space (at least 10 GB free) for Android Studio, SDKs, and build artifacts.

### Required Software

Install the following and confirm the versions:

```bash
node -v       # >= 18.x
npm -v
python --version
java -version # >= 11
git --version
```

- Node.js 18 LTS or newer (bundled npm is fine).
- Python 3.x (gradle plugin tooling compatibility).
- Java JDK 11 or 17 (Adoptium recommended).
- Android Studio (Flamingo or newer) with Android SDK 34+.
- Git client.
- Optional: Yarn, Visual Studio Code, or JetBrains IDEs.

### Accounts & Credentials

1. Google account with access to Firebase and Gmail (app password required for SMTP).

2. Render.com account.

### Android Tooling

1. Enable USB debugging on test devices.

2. Install manufacturer USB drivers as needed (Windows).

3. Confirm `adb devices` lists the hardware.

## Firebase Backend Setup

All applications share a single Firebase project.

### 1.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/).
2. Click **"Add project"**.
3. Enter project name: `Anything you want`.
4. Click **"Create project"**.
5. Click **"Continue"**.
6. Wait for the project to be created.

### 1.2 Enable Required Services

In your Firebase project dashboard:

1. **Authentication**: 

   - Go to **Authentication** → **Get started**.
   - Click **"Get started"**.
   - Enable **Email/Password** and **Anonymous** sign-in methods.
   - Click **"Save"**.

2. **Realtime Database**:
   - Go to **Realtime Database** → **Create database**.
   - Choose **"Start in test mode"** (change to production rules later).
   - Click **"Create database"**.

### 1.3 Set up this database rules: 

```javascript
{
  "rules": {
    ".read": true,   
    ".write": true,  

    "children": {
      "$childId": {
        ".read": true,
        ".write": "!data.exists()"
      }
    },

    "signals": {
      "$childId": {
        ".read": true,
        ".write": true
      }
    }
  }
}
```

> Update these rules with authenticated checks before going live.

### 1.4 Register Apps and Follow Next Steps

#### Web (Electron):

*You'll see a more detailed insrtuctions in:* `ParentElectronApp/electron_setup.md`.

#### Android (Child):

*You'll see a more detailed insrtuctions in:* `Child/child_setup.md`.

#### Android (Parent):

*You'll see a more detailed insrtuctions in:* `ParentAndroid/parent-android_setup.md`.

---