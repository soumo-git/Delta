# 🚀 Project Delta Setup Guide

A comprehensive guide to setting up Project Delta - a parental control system with stealth monitoring capabilities.

## 📋 Overview

Project Delta consists of multiple components working together to provide comprehensive parental monitoring:

- **Child Android App**: Stealth monitoring app that runs on children's devices
- **Parent Android App**: Mobile Android application for parents (alternative to desktop app)
- **Parent Electron App**: Desktop application for parents to monitor and control
- **Firebase Backend**: Real-time database and authentication
- **Email Service**: Standalone service for OTP and notification emails

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed:

### Required Software
- **Node.js** (v18.0.0 or higher) - [Download](https://nodejs.org/)
- **Java JDK** (v11 or higher) - [Download](https://adoptium.net/)
- **Android Studio** (Arctic Fox or higher) - [Download](https://developer.android.com/studio)
- **Git** - [Download](https://git-scm.com/)
- **Python** (for some build tools) - [Download](https://python.org/)

### Required Accounts & Services
- **Google Account** (for Gmail App Passwords and Firebase)
- **Firebase Account** - [Sign up](https://firebase.google.com/)

---

## 🔥 Step 1: Firebase Setup

Firebase serves as the backend for real-time communication between parent and child apps.

### 1.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Add project"** or use existing project
3. Enter project name: `project-delta-[your-name]`
4. Follow the setup wizard (enable Google Analytics if desired)

### 1.2 Enable Required Services

In your Firebase project dashboard:

1. **Authentication**:
   - Go to **Authentication** → **Get started**
   - Enable **Email/Password** and **Anonymous** sign-in methods

2. **Realtime Database**:
   - Go to **Realtime Database** → **Create database**
   - Choose **"Start in test mode"** (change to production rules later)
   - Set up this database rules:

```javascript
{
  "rules": {
    ".read": true,   // no global read
    ".write": true,  // no global write

    "children": {
      "$childId": {
        // Anyone can read children to know if an ID exists
        ".read": true,

        // Only allow write if the node doesn't exist yet (first registration)
        ".write": "!data.exists()"
      }
    },

    "signals": {
      "$childId": {
        // Parent needs to read to get offers/answers/ICE
        ".read": true,

        // Allow write so Child + Parent can push offers/answers/ICE
        ".write": true
      }
    }
  }
}

```

### 1.3 Get Firebase Configuration

1. Go to **Project Settings** (gear icon) → **General**
2. Scroll to **"Your apps"** section
3. Click **"Add app"** → **Web app** (</>)
4. Register your app with any name
5. Copy the configuration object

### 1.4 Update Firebase Config in Parent App

Update `ParentElectronApp/js/firebase-config.js`:

```javascript
const firebaseConfig = {
  apiKey: "your-api-key",
  authDomain: "your-project.firebaseapp.com",
  databaseURL: "https://your-project-default-rtdb.firebaseio.com",
  projectId: "your-project-id",
  storageBucket: "your-project.appspot.com",
  messagingSenderId: "your-messaging-sender-id",
  appId: "your-app-id",
  measurementId: "your-measurement-id"
};
```

---

## 📱 Step 2: Child Android App Setup

### 2.1 Android Studio Configuration

1. Open Android Studio
2. Select **"Open"** → Navigate to `Child/` directory
3. Wait for Gradle sync to complete

### 2.2 Configure App Settings

1. Open `Child/app/src/main/java/com/soumo/child/configuration/AppConfig.kt`
2. Update Firebase configuration.

### 2.3 Build and Install

1. Connect Android device.
2. Click **"Run"** in Android Studio (Shift+F10)
3. Select your device.
4. App will install as "Child".

**Note**: The child app runs in stealth mode and will hide its icon when you command to hide it.

---

## 📱 Step 3: Parent Android App Setup

If you prefer a mobile parent app instead of the desktop app, you can use the Android version.

### 3.1 Android Studio Configuration

1. Open Android Studio
2. Select **"Open"** → Navigate to `ParentAndroid/` directory
3. Wait for Gradle sync to complete

### 3.2 Configure App Settings

1. Open `ParentAndroid/app/src/main/java/com/soumo/parentandroid/AppConf.kt`
2. Update Firebase configuration.
3. Update RENDER_API_URL with your Render API URL.
4. Update FIREBASE_DB_URL with your Firebase Database URL.

### 3.3 Build and Install Android App

1. Connect Android device.
2. Click **"Run"** in Android Studio.
3. Select your device.
4. App will install as "Parent Android".

**Note**: This Android parent app provides the same functionality as the Electron app but in a mobile form factor.

---

## 🖥️ Step 4: Parent Electron App Setup

### 4.1 Install Dependencies

```bash
cd ParentElectronApp
npm install . 
```

### 4.2 Configure Firebase

Update `js/firebase-config.js` with your Firebase project credentials (already covered in Step 1.4)

### 4.3 Run the Application

**Development Mode:**
```bash
npm start
```

**Build for Production:**
```bash
npm run build
npm run dist
```

The app will be available in the `dist/` folder as an installer.

---

## 📧 Step 5: Email Service Setup

For reliable OTP email delivery, set up the standalone email service deployed on Render.

### 5.1 Deploy to Render

1. Create a GitHub repository for the `email-service` folder
2. Go to [Render.com](https://render.com/) and sign up
3. Click **"New +"** → **"Web Service"**
4. Connect your GitHub repository
5. Configure:
   - Name: `project-delta-email-service`
   - Environment: `Node`
   - Build Command: `npm install`
   - Start Command: `npm start`

### 4.2 Environment Variables

In Render dashboard, add:

```env
GMAIL_USER=your-email@gmail.com
GMAIL_APP_PASSWORD=your-16-char-app-password
```

### 4.3 Update Parent App

Update `ParentElectronApp/js/auth-manager.js` to use the Render email service:

```javascript
// Use Render email service instead of Firebase Functions:
const response = await fetch('https://your-service.onrender.com/send-otp', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, otp })
});
```

---

## 🔧 Configuration Files

### Child App Configuration

Key files to configure in the Android app:

- `app/src/main/java/com/soumo/child/configuration/AppConfig.kt` - Main app settings
- `app/src/main/java/com/soumo/child/webrtc/WebRTCConfig.kt` - WebRTC settings
- `app/src/main/java/com/soumo/child/service/BackgroundService.kt` - Background service settings

### Parent App Configuration

Key files to configure in the Electron app:

- `js/firebase-config.js` - Firebase connection settings
- `js/auth-manager.js` - Authentication settings
- `js/webrtc-manager.js` - WebRTC connection settings

---

## 🚀 Running the System

### Start Order

1. **Firebase Services**: Already running (Realtime Database, Authentication)
2. **Email Service**: Deployed and running on Render
3. **Child App**: Install and run on target Android device
4. **Parent App**: Choose one:
   - **Parent Android App**: Install and run on parent's Android device
   - **Parent Electron App**: Run the desktop application on computer

### Testing the Connection

1. Open your chosen Parent App (Android or Electron)
2. Sign up/Login with email and password
3. The app will attempt to connect to Firebase
4. Install Child app on Android device
5. Both apps should now communicate through Firebase

---

## 🔒 Security Considerations

### Child App Security

- App runs in stealth mode and hides its icon if you want.
- Requires special permissions for background operation. Don't worry, it's safe.
- Uses encrypted communication via Firebase.

### Parent App Security

- Uses Firebase Authentication for secure login
- All communication encrypted via HTTPS/WebSockets
- Local storage encrypted where possible

### Firebase Security

- Enable production database rules before deployment (You need to impliment App Check to do this):

```javascript
{
  "rules": {
    "child_devices": {
      ".read": "auth != null && auth.token.email_verified == true",
      ".write": "auth != null && auth.token.email_verified == true"
    },
    "parent_users": {
      ".read": "auth != null && auth.token.email_verified == true",
      ".write": "auth != null && auth.token.email_verified == true"
    }
  }
}
```

---

## 🧪 Testing

### Unit Testing

```bash
# Child App
cd Child
./gradlew test

# Parent App (if tests exist)
cd ParentElectronApp
npm test
```

### Integration Testing

1. Deploy all components
2. Install Child app on Android device
3. Install/Run Parent app (Android or Electron) on parent device
4. Test video streaming, location tracking, and other features

### Common Test Scenarios

- Email OTP verification
- Real-time video streaming
- Location tracking
- SMS monitoring
- App persistence after reboot

---

## 🛠️ Development

### Project Structure

```
Project Delta/
├── Child/                          # Android app for children
│   ├── app/src/main/java/com/soumo/child/
│   │   ├── BackgroundService.kt    # Main stealth service
│   │   ├── webrtc/                 # WebRTC components
│   │   ├── signaling/              # Firebase signaling
│   │   └── ui/                     # User interface
├── ParentAndroid/                  # Android app for parents (mobile)
│   ├── app/src/main/java/com/soumo/parentandroid/
│   │   ├── MainActivity.kt         # Main parent interface
│   │   ├── webrtc/                 # WebRTC components
│   │   └── ui/                     # User interface
├── ParentElectronApp/              # Electron app for parents (desktop)
│   ├── js/
│   │   ├── auth-manager.js         # Authentication
│   │   ├── webrtc-manager.js       # Video streaming
│   │   ├── dashboard.js            # Main dashboard
│   │   └── firebase-config.js      # Firebase setup
└── email-service/                  # Standalone email service
    ├── server.js                   # Express server
    └── README.md                   # Deployment guide
```

### Adding Features

- **Child App**: Add new monitoring features in `BackgroundService.kt`
- **Parent Android App**: Add new mobile features in `MainActivity.kt`
- **Parent Electron App**: Add new UI features in `js/dashboard.js`
- **Email Service**: Add new endpoints in `email-service/server.js`

---

## 🚨 Troubleshooting

### Common Issues

#### Firebase Connection Issues

**Problem**: Parent app can't connect to Firebase
**Solution**:
1. Check Firebase configuration in `firebase-config.js`
2. Ensure Firebase project is active and services are enabled
3. Check browser console for specific error messages

#### Email Not Sending

**Problem**: OTP emails not being delivered
**Solution**:
1. Verify Gmail App Password is correct
2. Check Render service logs in your Render dashboard
3. Ensure 2-factor authentication is enabled on Gmail
4. Test with the health check endpoint

#### Child App Not Starting

**Problem**: Child app doesn't start or crashes
**Solution**:
1. Check Android device permissions (overlay, notifications, etc.)
2. Ensure all required permissions are granted
3. Check device logs: `adb logcat`
4. Verify Firebase configuration

#### Video Streaming Issues

**Problem**: Video not streaming between apps
**Solution**:
1. Check WebRTC permissions on both devices
2. Ensure stable internet connection
3. Check Render service for any API errors
4. Verify STUN/TURN server configuration

#### App Not Stealthy

**Problem**: Child app icon visible or not hiding
**Solution**:
1. Check stealth mode implementation in `BackgroundService.kt`
2. Ensure proper permissions for hiding app icon
3. Test on different Android versions

### Getting Help

1. Check Firebase Console for error logs
2. Review browser/device console logs
3. Verify all configuration files are correctly updated
4. Test each component individually before full integration

### Debug Mode

For debugging, you can:

1. **Child App**: Enable debug logging in `AppConfig.kt`
2. **Parent App**: Open browser dev tools (F12) for console logs
3. **Email Service**: Check Render dashboard logs for errors

---

## 📋 Deployment Checklist

- [ ] Firebase project created and configured
- [ ] Gmail App Password generated and configured
- [ ] Child Android app built and tested
- [ ] Parent Electron app configured and running
- [ ] Email service deployed on Render
- [ ] All components tested together
- [ ] Production Firebase rules enabled
- [ ] All placeholder configurations replaced

---

## 🔄 Updates and Maintenance

### Regular Updates

1. **Dependencies**: Keep all packages updated
2. **Firebase**: Monitor service usage and costs
3. **Security**: Regularly update Firebase security rules
4. **Features**: Add new monitoring features as needed

### Backup

1. Export Firebase project configuration regularly
2. Backup any local configuration files
3. Keep service account keys secure

---

## 💰 Cost Estimation

### Free Tier Limits

- **Firebase**:
  - Realtime Database: 1GB storage, 100 concurrent connections
  - Authentication: 50,000 monthly active users

- **Email Service (Render)**: 750 hours/month free

### Scaling Considerations

- Monitor Firebase usage in console
- Consider upgrading plans for high usage
- Implement usage limits if needed

---

## 📞 Support

For issues not covered in this guide:

1. Check the existing documentation in each component folder
2. Review the source code for implementation details
3. Test individual components before integration
4. Check Firebase and Render dashboards for service status

**Note**: This is a complex system with multiple components. Take time to understand each part before deployment.

---

*Built with ❤️ by Project Delta Team*
