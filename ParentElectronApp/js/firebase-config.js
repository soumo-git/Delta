// Firebase Configuration and Initialization
console.log("✅ firebase-config.js loaded");

// Firebase Config
const firebaseConfig = {
  apiKey: "USE YOUR OWN FIREBASE API KEY",
  authDomain: "USE YOUR OWN FIREBASE AUTH DOMAIN",
  databaseURL: "USE YOUR OWN FIREBASE REALTIME DATABASE URL",
  projectId: "USE YOUR OWN FIREBASE PROJECT ID",
  storageBucket: "USE YOUR OWN FIREBASE STORAGE BUCKET",
  messagingSenderId: "USE YOUR OWN FIREBASE MESSAGING SENDER ID",
  appId: "USE YOUR OWN FIREBASE APP ID",
  measurementId: "USE YOUR OWN FIREBASE MEASUREMENT ID"
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
