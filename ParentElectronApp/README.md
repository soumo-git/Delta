## Parent Electron App

Go to Firebase Console and navigate to your project. Add a new app and select Web. Name it `parent-electron`.

### Configure Firebase & Email

1. Edit `ParentElectronApp/js/firebase-config.js` and `ParentElectronApp/renderer.js` with your Firebase config. 

```javascript
apiKey: "USE YOUR OWN FIREBASE API KEY",
authDomain: "USE YOUR OWN FIREBASE AUTH DOMAIN",
databaseURL: "USE YOUR OWN FIREBASE REALTIME DATABASE URL",
projectId: "USE YOUR OWN FIREBASE PROJECT ID",
storageBucket: "USE YOUR OWN FIREBASE STORAGE BUCKET",
messagingSenderId: "USE YOUR OWN FIREBASE MESSAGING SENDER ID",
appId: "USE YOUR OWN FIREBASE APP ID",
measurementId: "USE YOUR OWN FIREBASE MEASUREMENT ID"
```

Already disscussed in email service setup guide.

2. Update `ParentElectronApp/js/auth-manager.js` **(Line 46)** with the email service URL.

```javascript
this.emailServiceUrl = isDevelopment 
        ? 'http://localhost:3000' 
        : 'USE YOUR OWN DEPLOYED EMAIL SERVICE URL';
```

### Install Dependencies & Run

```bash
cd ParentElectronApp
npm install
npm start          # launches Electron in development
npm run build      # prepares assets
npm run dist       # creates installer under ParentElectronApp/dist/
```

The build uses `electron-builder` with an NSIS installer profile defined in `package.json`.

### Common Customizations

- Replace `assets/Icon.ico` for custom branding. Don't change the name.
- Review `js/webrtc-manager.js` if you need to adjust STUN/TURN servers.
- Use `F12` inside the Electron window to open DevTools (enabled in `main.js`).

## First-Run Walkthrough

1. Ensure Firebase Authentication and Realtime Database are ready. Already disscussed in firebase setup guide.

2. Start the email service locally or verify the hosted Render instance. Already disscussed in email service setup guide.

3. Launch the parent Electron app (Or Android parent app. Already disscussed in parent android app setup guide.)

---

That's it! You have successfully set up the Parent Electron app. 🎉