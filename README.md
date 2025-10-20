# Project Delta 🚀

Welcome to **Project Delta**—the ultimate parental control suite disguised as a stealthy Android app and a sleek desktop dashboard.

Delta is a **two-headed beast** 🐉:

- **Child App**: A stealthy Android app that vanishes from sight, streams live video and audio, tracks location, and monitors SMS and calls—all while the kid thinks they’re just playing games. It’s like having a spy cam in their pocket, minus the creepy factor (or maybe not).

- **Parent App**: A sleek interface that gives parents the power to monitor, control, and understand their child's digital world. It’s like having a security camera, a therapist, and a tech support agent all rolled into one.

## Disclaimer – Please Read Before Using

These applications are intended solely for lawful and authorized monitoring. You may only install, use, or operate them: On devices you own; or on devices where you have explicit, informed consent from the user being monitored. 

Unauthorized use may be illegal in your country, state, or region. 
Installing or using these apps without proper consent may violate privacy, data protection, and wiretapping laws.

Penalties can include criminal charges, fines, and imprisonment. 
By installing or using these apps, you agree that: 

- You are solely responsible for ensuring your use complies with all applicable laws. 
- The developer(s) of these applications assume no liability for misuse, damage, or legal consequences resulting from 
improper use. 
- If you are unsure about the legality of using these apps in your situation, consult a qualified legal professional before 
proceeding.

## Features 

- ***Stealth Mode***: The Child App literally deletes its own icon and laughs at the idea of “visibility.” Once gone, it stays gone until parent wants to show up.
- ***Video Streaming***: Parents get live video feeds of what the kid’s up to. (Spoiler: it’s usually TikTok, Instagram, or staring blankly at a wall.)
- ***Mic Streaming***: Live mic feed to parent for live audio monitoring. Just listen and chill, baby.
- ***Location Monitoring***: Just hit on the start button and you'll get live location.
- ***SMS Monitoring***: You can see all previous sms and all upcoming sms live.
- ***Call Monitoring***: All call logs in your parent's dashboard. (Call recording coming soon ☠️)
- ***Chat Monitoring***: All social media chats in parent's hand - Privacy left the chat. (Currently working on it, but you can still see the chat's - Just open the devoloper tools in ParentElectron app. - Press Ctrl+Shift+I)
- ***Notification Monitoring***: All notifications from child's device to parent's dashboard. (Currently working on it, but you can still see the notifications - Just open the devoloper tools in ParentElectron app. - Press Ctrl+Shift+I)
- ***Persistence***: Reboots? App restarts? The Child App just shrugs. Try harder, kid.
- ***Dark Dashboard Vibes***: The Parent App is wrapped in a clean, scalable Electron UI—complete with cosmic particles swirling in the background. Because if you’re spying, might as well look cool.
- ***Cross-Platform Parent App***: Whether you’re on Windows, Mac, or Linux, the Parent App has got you covered. Spy from anywhere, anytime.
- ***Email Microservice***: OTPs, password resets, and test emails sent via Gmail SMTP. Because even spies need secure communication.

---
---
# Setup Guide

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

- Update these rules with authenticated checks before going live.

### 1.4 Register Apps and Follow Next Steps

#### Web (Electron):

> *You'll see a more detailed insrtuctions in:* `ParentElectronApp/README.md`.

#### Android (Child):

> *You'll see a more detailed insrtuctions in:* `Child/README.md`.

#### Android (Parent):

> *You'll see a more detailed insrtuctions in:* `ParentAndroid/README.md`.

---

#### Why Project Delta?

Let’s just say we’ve turned “what‑if” into “why‑not” and built a tool that can morph any device into a surveillance‑friendly playground. 

#### Who Is This For?

- Tech-savvy parents who want to keep an eye on their kids’ digital lives.
- Kids who think they can outsmart their parents (good luck with that).
- Developers looking for a fun project that pushes the boundaries of mobile and desktop tech.
- And anyone else who’s ever wondered, “What if I could…?”

#### Final Words
- **Children**: Beware.
- **Parents**: Enjoy.
- **Developers**: Maybe start updating your résumés.
- **Creeps**: You already know what to do.

> ###### Built by **Soumo** — the non-coder who pulled it off anyway.

---