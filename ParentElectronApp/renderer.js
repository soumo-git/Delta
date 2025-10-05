console.log("✅ renderer.js loaded");

// --- Firebase Config ---
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

firebase.initializeApp(firebaseConfig);
const db = firebase.database();

db.ref("test_write").set({ fromElectron: Date.now() })
  .then(() => console.log("✅ Firebase test write succeeded"))
  .catch((err) => console.error("❌ Firebase test write failed:", err));

// --- UI Elements ---
const connectBtn = document.getElementById("connectBtn");
const startBtn = document.getElementById("startCamera");
const stopBtn = document.getElementById("stopCamera");
const pingBtn = document.getElementById("pingChild");
const childInput = document.getElementById("childId");
const video = document.getElementById("remoteVideo");
const panel = document.getElementById("controlPanel");

let pc = null;
let dc = null;

// Disable buttons initially
[startBtn, stopBtn, pingBtn].forEach(btn => btn.disabled = true);

// --- Connect Handler ---
connectBtn.onclick = async () => {
  const childId = childInput.value.trim();
  if (!/^\d{12}$/.test(childId)) {
    alert("❌ Please enter a valid 12-digit Child ID");
    return;
  }

  console.log("🔗 Connecting to Child ID:", childId);
  panel.style.display = "block";

  const basePath = `calls/${childId}`;
  const offerRef = db.ref(`${basePath}/offer`);
  const answerRef = db.ref(`${basePath}/answer`);
  // const iceRef = db.ref(`${basePath}/candidates`); // Removed for Non-Trickle ICE

  // --- Updated: Google's Public STUN Server ---
  pc = new RTCPeerConnection({
    iceServers: [
      { urls: [
        "stun:stun.l.google.com:19302",
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302",
        "stun:stun3.l.google.com:19302",
        "stun:stun4.l.google.com:19302",
        "stun:stun.ekiga.net",
        "stun:stun.ideasip.com",
        "stun:stun.schlund.de",
        "stun:stun.stunprotocol.org",
        "stun:stun.voiparound.com",
        "stun:stun.voipbuster.com",
        "stun:stun.voipstunt.com",
        "stun:stun.voxgratia.org",
        "stun:stun.xten.com"
      ]}
    ]
    //iceTransportPolicy: "relay" // 👈 REQUIRED for TURN-only (forces DataChannel over TURN) - Currently not working, We'll fix later.
  });

  pc.oniceconnectionstatechange = () => console.log("🌐 ICE State:", pc.iceConnectionState);
  pc.onconnectionstatechange = () => console.log("🔌 Connection State:", pc.connectionState);
  pc.onicegatheringstatechange = () => console.log("📶 ICE Gathering State:", pc.iceGatheringState);

  pc.ontrack = (event) => {
    console.log("🎥 ontrack event:", event);
    const remoteStream = event.streams?.[0] || new MediaStream([event.track]);
    video.srcObject = remoteStream;

    video.onloadedmetadata = () => {
      video.play()
        .then(() => console.log("▶️ Video playing"))
        .catch(err => console.error("❌ video.play() error:", err));
    };

    setTimeout(() => {
      const tracks = video.srcObject?.getVideoTracks();
      console.log("📊 Remote video tracks:", tracks);
    }, 1000);
  };

  pc.ondatachannel = (event) => {
    dc = event.channel;
    console.log("✅ DataChannel received:", dc.label);

    dc.onopen = () => {
      console.log("🚀 DataChannel is open");
      [startBtn, stopBtn, pingBtn].forEach(btn => btn.disabled = false);
    };

    dc.onclose = () => console.warn("🛑 DataChannel closed");
    dc.onerror = (e) => console.error("❌ DataChannel error:", e);
    dc.onmessage = (e) => console.log("📩 From Child:", e.data);
  };

  offerRef.on("value", async (snap) => {
    const offer = snap.val();
    if (!offer || pc.currentRemoteDescription) {
      console.warn("⛔ Offer already handled or invalid");
      return;
    }

    try {
      console.log("📥 Received offer:", offer);

      await pc.setRemoteDescription(new RTCSessionDescription(offer));
      console.log("✅ Remote description set");

      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      
      // Wait for ICE gathering to complete for Non-Trickle ICE
      await new Promise((resolve) => {
        if (pc.iceGatheringState === 'complete') return resolve();
        const check = () => {
          if (pc.iceGatheringState === 'complete') {
            pc.removeEventListener('icegatheringstatechange', check);
            resolve();
          }
        };
        pc.addEventListener('icegatheringstatechange', check);
      });
      
      await answerRef.set({
        type: pc.localDescription.type,
        sdp: pc.localDescription.sdp
      });

      console.log("📤 Answer sent to Firebase (Non-Trickle ICE)");
    } catch (err) {
      console.error("❌ Error handling offer:", err);
    }
  });
};

// 🎬 Commands
startBtn.onclick = () => {
  if (dc?.readyState === "open") {
    dc.send("CAMERA_ON");
    console.log("📤 Sent: CAMERA_ON");
  } else {
    alert("⚠️ DataChannel not ready");
  }
};

stopBtn.onclick = () => {
  if (dc?.readyState === "open") {
    dc.send("CAMERA_OFF");
    console.log("📤 Sent: CAMERA_OFF");
  } else {
    alert("⚠️ DataChannel not ready");
  }
};

pingBtn.onclick = () => {
  if (dc?.readyState === "open") {
    dc.send("PING_CHILD");
    console.log("📤 Sent: PING_CHILD");
  } else {
    alert("⚠️ DataChannel not ready");
  }
};
