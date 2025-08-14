# Project Delta - Setup Instructions

This document contains instructions for setting up the Project Delta application with your own credentials and configuration.

## 🔐 Required Credentials & Configuration

Before running the application, you need to replace placeholder values with your actual credentials in the following files:

### 1. Firebase Configuration

#### Firebase Project Setup
1. Create a new Firebase project at [Firebase Console](https://console.firebase.google.com/)
2. Enable the following services:
   - Realtime Database
   - Authentication
   - Cloud Functions
   - Firebase Storage

#### Files to Update:

**`.firebaserc`**
```json
{
  "projects": {
    "default": "YOUR-FIREBASE-PROJECT-ID"
  }
}
```

**`email-service/.env`**
Replace the following placeholders:
- `YOUR_FIREBASE_PROJECT_ID` - Your Firebase project ID
- `your-private-key-id` - Private key ID from service account
- `Your private key here` - Full private key content
- `your-client-email@YOUR_PROJECT_ID.iam.gserviceaccount.com` - Service account email
- `your-client-id` - Client ID from service account
- `YOUR_PROJECT_ID-default-rtdb.REGION.firebasedatabase.app` - Your Realtime Database URL
- `your-email@gmail.com` - Your Gmail address
- `your-gmail-app-password` - Your Gmail app password

**`email-service/firebase-service-account.json.json`**
Download your Firebase service account JSON file and replace all placeholder values.

**`Child/app/google-services.json`**
Download the `google-services.json` file from your Firebase project for Android apps.

### 2. Gmail Configuration

#### Getting Gmail App Password
1. Go to your [Google Account settings](https://myaccount.google.com/)
2. Select "Security" → "2-Step Verification" → "App passwords"
3. Generate an app password for "Mail"
4. Use this password in the `GMAIL_APP_PASSWORD` field

### 3. Android Configuration

#### Local Properties
Update `local.properties` files in both Android projects:
- `Child/local.properties`
- `ParentAndroid/local.properties`

Replace `YOUR_USERNAME` with your actual Windows username.

#### Google Services
1. In Firebase Console, go to Project Settings
2. Add Android apps with package names:
   - `com.soumo.child` (Child app)
   - `com.soumo.parent` (Parent app)
3. Download `google-services.json` for each app
4. Replace the placeholder `google-services.json` file in `Child/app/`

### 4. Package Names (Optional)
If you want to change the package names from `com.soumo.*`:
1. Update package names in Android manifests
2. Update package names in `google-services.json`
3. Update package names in Firebase project settings

## 📁 File Structure

```
Project Delta/
├── .firebaserc                     # Firebase project configuration
├── firebase.json                   # Firebase hosting/functions config
├── email-service/
│   ├── .env                       # Environment variables (SENSITIVE)
│   ├── firebase-service-account.json.json  # Service account (SENSITIVE)
│   └── server.js                  # Email service code
├── Child/
│   ├── app/google-services.json   # Firebase config for Child app (SENSITIVE)
│   └── local.properties           # Android SDK path (LOCAL)
├── ParentAndroid/
│   └── local.properties           # Android SDK path (LOCAL)
├── ParentElectronApp/
│   ├── main.js                    # Electron main process
│   └── package.json               # Electron dependencies
└── functions/
    ├── index.js                   # Firebase Cloud Functions
    └── package.json               # Functions dependencies
```

## 🚫 Files to Exclude from Version Control

Make sure these files are in your `.gitignore`:

```gitignore
# Environment and credentials
**/.env
**/local.properties
**/google-services.json
**/firebase-service-account*.json
**/*-key.json
**/*.keystore

# Build outputs
**/build/
**/dist/
**/node_modules/
**/.gradle/

# IDE files
**/.idea/
**/.vscode/
```

## 🚀 Getting Started

1. **Clone the repository**
2. **Set up Firebase project** (see Firebase Configuration above)
3. **Update all configuration files** with your credentials
4. **Install dependencies**:
   ```bash
   # Email service
   cd email-service
   npm install
   
   # Firebase functions
   cd ../functions
   npm install
   
   # Electron app
   cd ../ParentElectronApp
   npm install
   ```
5. **Build Android apps** using Android Studio
6. **Deploy Firebase functions** (if needed):
   ```bash
   firebase deploy --only functions
   ```

## ⚠️ Security Notes

- **Never commit sensitive files** to version control
- **Use environment variables** for sensitive data in production
- **Rotate credentials regularly**
- **Use Firebase Security Rules** to protect your database
- **Enable Firebase App Check** for additional security

## 📞 Support

If you need help setting up the project, please check the individual README files in each component directory for detailed instructions.

## 🔍 Verification

To verify your setup:
1. Run the email service: `cd email-service && npm start`
2. Test the email endpoint: `POST http://localhost:3000/test-email`
3. Build the Android apps in Android Studio
4. Run the Electron app: `cd ParentElectronApp && npm start`

All components should work without errors if properly configured.
