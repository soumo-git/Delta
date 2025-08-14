// Firebase Configuration and Initialization
console.log("✅ firebase-config.js loaded");

// Firebase Config (replace with your own Firebase project settings)
const firebaseConfig = {
  apiKey: "YOUR_API_KEY",
  authDomain: "YOUR_PROJECT_ID.firebaseapp.com",
  databaseURL: "YOUR_RTDATABASE_URL",
  projectId: "YOUR_PROJECT_ID",
  storageBucket: "YOUR_PROJECT_ID.appspot.com",
  messagingSenderId: "YOUR_SENDER_ID",
  appId: "YOUR_APP_ID",
  measurementId: "YOUR_MEASUREMENT_ID"
};

// Initialize Firebase
firebase.initializeApp(firebaseConfig);
const db = firebase.database();
const auth = firebase.auth();

// Test Firebase connection
db.ref("test_write").set({ fromElectron: Date.now() })
  .then(() => console.log("✅ Firebase test write succeeded"))
  .catch((err) => console.error("❌ Firebase test write failed:", err));

// Export for use in other modules
window.firebaseDB = db;
window.firebaseAuth = auth;
