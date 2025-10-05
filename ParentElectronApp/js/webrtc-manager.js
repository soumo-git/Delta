// WebRTC Connection Manager
console.log("✅ webrtc-manager.js loaded");

let locationStartedNotified = false;

class WebRTCManager {
  constructor() {
    this.peerConnection = null;
    this.dataChannel = null;
    this.videoStreams = {};
    this.onConnectionEstablished = null;
    this.onStreamReceived = null;
    this.onDataChannelMessage = null;
    this.currentChildId = null;
    this.forceNextVideoAsScreen = false; // Debug flag
  }

  // Initialize WebRTC connection
  async initializeConnection(childId) {
    console.log("🔗 Initializing WebRTC connection for Child ID:", childId);
    this.currentChildId = childId;
    
    const db = window.firebaseDB;
    // Convert formatted ID to raw ID for Firebase path (remove dashes)
    const rawChildId = childId.replace(/-/g, "");
    console.log("🔗 Using raw Child ID for Firebase path:", rawChildId);
    const basePath = `calls/${rawChildId}`;
    const offerRef = db.ref(`${basePath}/offer`);
    const answerRef = db.ref(`${basePath}/answer`);

    // Create peer connection with Google's public STUN servers
    this.peerConnection = new RTCPeerConnection({
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
    });

    // Set up event handlers
    this.setupEventHandlers();

    // Set up Firebase listeners
    this.setupFirebaseListeners(offerRef, answerRef);

    return { peerConnection: this.peerConnection, dataChannel: this.dataChannel };
  }

  setupEventHandlers() {
    const pc = this.peerConnection;

    // Connection state monitoring
    pc.oniceconnectionstatechange = () => {
      console.log("🌐 ICE State:", pc.iceConnectionState);
    };

    pc.onconnectionstatechange = () => {
      console.log("🔌 Connection State:", pc.connectionState);
      if (pc.connectionState === 'connected') {
        console.log("✅ WebRTC connection established!");
        
        // Log all current senders (tracks being sent)
        const senders = pc.getSenders();
        console.log("📤 Current senders:", senders.length);
        senders.forEach((sender, index) => {
          if (sender.track) {
            console.log(`Sender ${index}:`, {
              trackId: sender.track.id,
              trackKind: sender.track.kind,
              trackEnabled: sender.track.enabled
            });
          }
        });
        
        // Log all current receivers (tracks being received)
        const receivers = pc.getReceivers();
        console.log("📥 Current receivers:", receivers.length);
        receivers.forEach((receiver, index) => {
          if (receiver.track) {
            console.log(`Receiver ${index}:`, {
              trackId: receiver.track.id,
              trackKind: receiver.track.kind,
              trackEnabled: receiver.track.enabled
            });
          }
        });
      }
    };

    pc.onicegatheringstatechange = () => {
      console.log("📶 ICE Gathering State:", pc.iceGatheringState);
    };

    // Handle received tracks - FIXED to work like the working version
    pc.ontrack = (event) => {
      const timestamp = new Date().toISOString();
      console.log(`🎥 [${timestamp}] ontrack event received!`);
      console.log("Event details:", event);
      const track = event.track;
      const remoteStream = event.streams?.[0] || new MediaStream([track]);
      
      console.log("=== TRACK DETAILS ===");
      console.log("Track kind:", track.kind);
      console.log("Track ID:", track.id);
      console.log("Track label:", track.label);
      console.log("Track enabled:", track.enabled);
      console.log("Track muted:", track.muted);
      console.log("Track readyState:", track.readyState);
      console.log("Streams count:", event.streams?.length);
      console.log("Remote stream ID:", remoteStream.id);
      console.log("Remote stream active:", remoteStream.active);
      console.log("====================");
      
      if (track.kind === 'video') {
        // Determine if this is camera or screen based on track label/id
        const isScreenTrack = track.id.includes('SCREEN') || track.label.includes('screen') || track.id.includes('SCREEN_TRACK');
        let streamType = isScreenTrack ? 'screen' : 'camera';
        
        console.log(`📹 Processing video track:`);
        console.log('- Track ID:', track.id);
        console.log('- Track label:', track.label);
        console.log('- Is screen track:', isScreenTrack);
        console.log('- Initial stream type:', streamType);
        
        // If we already have a camera stream, assume this is screen
        if (!isScreenTrack && this.videoStreams.camera) {
          streamType = 'screen';
          console.log('- Detected as screen track (camera already exists)');
        }
        
        // Debug: Force as screen if flag is set
        if (this.forceNextVideoAsScreen) {
          streamType = 'screen';
          this.forceNextVideoAsScreen = false;
          console.log('- Forced as screen track (debug flag)');
        }
        
        let videoElements;
        if (streamType === 'camera') {
          videoElements = document.querySelectorAll('.video-camera');
        } else if (streamType === 'screen') {
          videoElements = document.querySelectorAll('.video-screen');
        } else {
          videoElements = [document.getElementById(`video-${streamType}`)].filter(Boolean);
        }
        console.log('- Final stream type:', streamType);
        if (videoElements && videoElements.length > 0) {
          videoElements.forEach(videoElement => {
          videoElement.srcObject = remoteStream;
          videoElement.classList.remove('hidden');
          videoElement.onloadedmetadata = () => {
            videoElement.play()
              .then(() => console.log(`▶️ ${streamType} video playing`))
              .catch(err => console.error(`❌ ${streamType} video.play() error:`, err));
          };
          });
          const placeholder = document.getElementById(`placeholder-${streamType}`);
          if (placeholder) {
            placeholder.classList.add('hidden');
          }
        } else {
          console.error(`❌ Video element(s) for '${streamType}' not found!`);
        }
        
        // Store stream reference
        this.videoStreams[streamType] = remoteStream;
        console.log(`✅ Video stream set for ${streamType}`);
        
      } else if (track.kind === 'audio') {
        // Handle audio track (microphone)
        console.log("🎤 Processing audio track...");
        
        const audioElement = document.getElementById('video-mic');
        if (audioElement) {
          audioElement.srcObject = remoteStream;
          audioElement.muted = false;
          audioElement.autoplay = true;
          audioElement.controls = false;
          audioElement.style.display = 'block';
          // Try to play audio
          audioElement.play().catch(e => {
            console.warn('Audio play() failed:', e);
            if (window.parentApp && window.parentApp.dashboard && window.parentApp.dashboard.ui) {
              window.parentApp.dashboard.ui.showNotification('Click anywhere to enable microphone audio', 'info');
            }
            const unlockAudio = () => {
              audioElement.play().then(() => {
                console.log('🔊 Audio unlocked by user gesture');
                document.removeEventListener('click', unlockAudio);
              }).catch(err => {
                console.warn('Audio play() still failed after user gesture:', err);
              });
            };
            document.addEventListener('click', unlockAudio);
          });
          // --- Live Waveform Visualization ---
          const waveCanvas = document.getElementById('mic-wave');
          if (waveCanvas && window.AudioContext) {
            const ctx = waveCanvas.getContext('2d');
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const analyser = audioCtx.createAnalyser();
            analyser.fftSize = 256;
            const source = audioCtx.createMediaStreamSource(remoteStream);
            source.connect(analyser);
            const dataArray = new Uint8Array(analyser.frequencyBinCount);
            function drawWave() {
              analyser.getByteTimeDomainData(dataArray);
              ctx.clearRect(0, 0, waveCanvas.width, waveCanvas.height);
              ctx.save();
              ctx.lineWidth = 3;
              ctx.strokeStyle = 'rgba(99, 255, 188, 0.95)';
              ctx.shadowColor = '#22d3ee';
              ctx.shadowBlur = 8;
              ctx.beginPath();
              const sliceWidth = waveCanvas.width / dataArray.length;
              let x = 0;
              for (let i = 0; i < dataArray.length; i++) {
                const v = dataArray[i] / 128.0;
                const y = (v * waveCanvas.height) / 2;
                if (i === 0) {
                  ctx.moveTo(x, y);
                } else {
                  ctx.lineTo(x, y);
                }
                x += sliceWidth;
              }
              ctx.stroke();
              ctx.restore();
              requestAnimationFrame(drawWave);
            }
            drawWave();
          }
          // --- VU Meter ---
          const vuCanvas = document.getElementById('mic-vu-meter');
          if (vuCanvas && window.AudioContext) {
            const ctx = vuCanvas.getContext('2d');
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const analyser = audioCtx.createAnalyser();
            analyser.fftSize = 256;
            const source = audioCtx.createMediaStreamSource(remoteStream);
            source.connect(analyser);
            const dataArray = new Uint8Array(analyser.frequencyBinCount);
            function drawVU() {
              analyser.getByteTimeDomainData(dataArray);
              ctx.clearRect(0, 0, vuCanvas.width, vuCanvas.height);
              // Calculate RMS
              let sum = 0;
              for (let i = 0; i < dataArray.length; i++) {
                const v = (dataArray[i] - 128) / 128;
                sum += v * v;
              }
              const rms = Math.sqrt(sum / dataArray.length);
              const barWidth = Math.max(4, rms * vuCanvas.width * 1.5);
              ctx.fillStyle = rms > 0.2 ? '#22d3ee' : '#4ade80';
              ctx.fillRect(0, 0, barWidth, vuCanvas.height);
              requestAnimationFrame(drawVU);
            }
            drawVU();
          }
          // --- Audio Metrics ---
          this.startMicMetricsMonitor(remoteStream);
        } else {
          console.error("❌ Audio element 'video-mic' not found!");
        }
        
        // Place the VU meter overlay inside the camera video window
        const cameraStreamWindow = document.getElementById('stream-camera');
        if (cameraStreamWindow) {
          let vuContainer = cameraStreamWindow.querySelector('#camera-vu-container');
          if (!vuContainer) {
            vuContainer = document.createElement('div');
            vuContainer.id = 'camera-vu-container';
            vuContainer.style.position = 'absolute';
            vuContainer.style.bottom = '48px'; // moved higher
            vuContainer.style.left = '16px';
            vuContainer.style.zIndex = '30';
            const vuCanvas = document.createElement('canvas');
            vuCanvas.id = 'camera-vu-meter';
            vuCanvas.width = 7;
            vuCanvas.height = 80;
            vuCanvas.style.background = 'rgba(0,0,0,0.3)';
            vuCanvas.style.borderRadius = '6px';
            vuContainer.appendChild(vuCanvas);
            cameraStreamWindow.appendChild(vuContainer);
          }
          const cameraVu = cameraStreamWindow.querySelector('#camera-vu-meter');
          if (cameraVu && window.AudioContext) {
            const ctx = cameraVu.getContext('2d');
            const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
            const analyser = audioCtx.createAnalyser();
            analyser.fftSize = 256;
            const source = audioCtx.createMediaStreamSource(remoteStream);
            source.connect(analyser);
            const dataArray = new Uint8Array(analyser.frequencyBinCount);
            function drawVerticalVU() {
              analyser.getByteTimeDomainData(dataArray);
              ctx.clearRect(0, 0, cameraVu.width, cameraVu.height);
              // Calculate RMS
              let sum = 0;
              for (let i = 0; i < dataArray.length; i++) {
                const v = (dataArray[i] - 128) / 128;
                sum += v * v;
              }
              const rms = Math.sqrt(sum / dataArray.length);
              const barHeight = Math.max(4, rms * cameraVu.height * 4.5); // increased sensitivity
              // Gradient: green (low) to yellow to red (high)
              const grad = ctx.createLinearGradient(0, cameraVu.height, 0, 0);
              grad.addColorStop(0, '#22d3ee');
              grad.addColorStop(0.5, '#4ade80');
              grad.addColorStop(0.8, '#facc15');
              grad.addColorStop(1, '#ef4444');
              ctx.fillStyle = grad;
              ctx.fillRect(0, cameraVu.height - barHeight, cameraVu.width, barHeight);
              requestAnimationFrame(drawVerticalVU);
            }
            drawVerticalVU();
          }
        }
        
        // Store stream reference
        this.videoStreams.mic = remoteStream;
        console.log("✅ Audio stream set for microphone");
        
        // Additional debugging
        setTimeout(() => {
          const audioTracks = remoteStream.getAudioTracks();
          console.log("🎤 Audio tracks in stream:", audioTracks);
          audioTracks.forEach((track, index) => {
            console.log(`Track ${index}:`, {
              id: track.id,
              kind: track.kind,
              enabled: track.enabled,
              muted: track.muted,
              readyState: track.readyState
            });
          });
        }, 1000);
      }
    };

    // Handle data channel
    pc.ondatachannel = (event) => {
      this.dataChannel = event.channel;
      console.log("✅ DataChannel received:", this.dataChannel.label);

      this.dataChannel.onopen = () => {
        console.log("🚀 DataChannel is open");
        
        // Initialize microphone placeholder
        this.showMicrophonePlaceholder();
        
        if (this.onConnectionEstablished) {
          this.onConnectionEstablished(this.dataChannel);
        }
      };

      this.dataChannel.onclose = () => {
        console.warn("🛑 DataChannel closed");
      };

      this.dataChannel.onerror = (e) => {
        console.error("❌ DataChannel error:", e);
      };

      this.dataChannel.onmessage = (e) => {
        console.log("📩 From Child:", e.data);
        
        // Handle command acknowledgments and status updates
        this.handleChildMessage(e.data);
        
        if (this.onDataChannelMessage) {
          this.onDataChannelMessage(e.data);
        }
      };
    };

    // Remove onicecandidate handler for Non-Trickle ICE
    // pc.onicecandidate = (event) => { /* No-op for Non-Trickle ICE */ };
  }

  setupFirebaseListeners(offerRef, answerRef) {
    // Listen for offers from child (including renegotiation offers)
    offerRef.on("value", async (snap) => {
      console.log("🔍 Firebase offer listener triggered");
      console.log("🔍 Firebase path:", offerRef.toString());
      console.log("🔍 Firebase data:", snap.val());
      const offer = snap.val();
      if (!offer) {
        console.warn("⛔ No offer data");
        return;
      }

      try {
        console.log("📥 Received offer (possibly renegotiation):", offer);
        console.log("Current signaling state:", this.peerConnection.signalingState);
        console.log("Current remote description exists:", !!this.peerConnection.currentRemoteDescription);

        // Handle both initial offers and renegotiation offers
        if (this.peerConnection.signalingState === 'stable' || 
            this.peerConnection.signalingState === 'have-local-offer') {
          
          await this.peerConnection.setRemoteDescription(new RTCSessionDescription(offer));
          console.log("✅ Remote description set (renegotiation)");

          const answer = await this.peerConnection.createAnswer();
          await this.peerConnection.setLocalDescription(answer);
          // Wait for ICE gathering to complete for Non-Trickle ICE
          await new Promise((resolve) => {
            if (this.peerConnection.iceGatheringState === 'complete') return resolve();
            const check = () => {
              if (this.peerConnection.iceGatheringState === 'complete') {
                this.peerConnection.removeEventListener('icegatheringstatechange', check);
                resolve();
              }
            };
            this.peerConnection.addEventListener('icegatheringstatechange', check);
          });
          await answerRef.set({
            type: this.peerConnection.localDescription.type,
            sdp: this.peerConnection.localDescription.sdp
          });

          console.log("📤 Answer sent to Firebase (Non-Trickle ICE)");
        } else {
          console.warn("⚠️ Ignoring offer in signaling state:", this.peerConnection.signalingState);
        }
      } catch (err) {
        console.error("❌ Error handling offer:", err);
      }
    });
  }

  // Send command to child device
  sendCommand(command) {
    if (this.dataChannel?.readyState === "open") {
      try {
        this.dataChannel.send(command);
        console.log("📤 Sent:", command);
        this.showCommandFeedback(command, 'sent');
        this.setupCommandTimeout(command);
        return true;
      } catch (e) {
        console.error("❌ Error sending command:", e);
        this.showCommandFeedback(command, 'error', e.message);
        return false;
      }
    } else {
      console.warn("⚠️ DataChannel not ready");
      this.showCommandFeedback(command, 'error', 'Connection not ready');
      return false;
    }
  }
  
  setupCommandTimeout(command) {
    const timeoutId = setTimeout(() => {
      console.warn(`⚠️ Command ${command} timeout - no acknowledgment received`);
      this.showCommandFeedback(command, 'timeout');
    }, 10000); // 10 second timeout
    
    // Store timeout for potential cancellation
    this.commandTimeouts = this.commandTimeouts || {};
    this.commandTimeouts[command] = timeoutId;
  }
  
  showCommandFeedback(command, status, errorMessage = null) {
    // Create or update status indicator
    const statusEl = document.getElementById('command-status') || this.createStatusElement();
    
    const statusText = {
      'sent': `📤 ${command} sent...`,
      'success': `✅ ${command} successful`,
      'error': `❌ ${command} failed: ${errorMessage}`,
      'timeout': `⏰ ${command} timeout`,
      'permission': `🔐 ${command} - permission requested`
    };
    
    statusEl.textContent = statusText[status] || statusText['sent'];
    statusEl.className = `command-status ${status}`;
    
    // Auto-hide success messages after 3 seconds
    if (status === 'success') {
      setTimeout(() => {
        statusEl.textContent = '';
        statusEl.className = 'command-status';
      }, 3000);
    }
  }
  
  createStatusElement() {
    const statusEl = document.createElement('div');
    statusEl.id = 'command-status';
    statusEl.className = 'command-status';
    statusEl.style.cssText = `
      position: fixed;
      top: 20px;
      right: 20px;
      background: rgba(0, 0, 0, 0.8);
      color: white;
      padding: 10px 15px;
      border-radius: 5px;
      font-family: monospace;
      font-size: 12px;
      z-index: 1000;
      transition: opacity 0.3s;
    `;
    document.body.appendChild(statusEl);
    return statusEl;
  }
  
  handleChildMessage(message) {
    try {
      // Handle JSON messages (like location updates and SMS)
      if (message.startsWith('{')) {
        const data = JSON.parse(message);
        if (data.type === 'LOCATION_UPDATE') {
          this.handleLocationUpdate(data);
          return;
        }
        if (data.type === 'sms' && window.parentApp && window.parentApp.dashboard) {
          window.parentApp.dashboard.handleChildMessage(message);
          return;
        }
      }
      
      // Handle ping from child - respond with pong
      if (message === 'PING_CHILD') {
        console.log('📡 Received ping from child, sending pong');
        if (this.dataChannel && this.dataChannel.readyState === 'open') {
          this.dataChannel.send('PONG_PARENT');
        }
        return;
      }
      
      // Forward stealth ACKs to dashboard if present
      if ((message === 'STEALTH_ON_ACK' || message === 'STEALTH_OFF_ACK') && window.parentApp && window.parentApp.dashboard) {
        window.parentApp.dashboard.handleChildMessage(message);
        return;
      }
      
      // Handle simple status messages
      const statusMap = {
        'CAMERA_STARTED': { status: 'success', command: 'CAMERA_ON' },
        'CAMERA_STOPPED': { status: 'success', command: 'CAMERA_OFF' },
        'MIC_STARTED': { status: 'success', command: 'MIC_ON' },
        'MIC_STOPPED': { status: 'success', command: 'MIC_OFF' },
        'SCREEN_STOPPED': { status: 'success', command: 'SCREEN_OFF' },
        'LOCATION_STARTED': { status: 'success', command: 'LOCATE_CHILD' },
        'LOCATION_STOPPED': { status: 'success', command: 'LOCATE_CHILD_STOP' },
        'PONG_CHILD': { status: 'success', command: 'PING_CHILD' },
        'PING_CHILD': { status: 'info', command: 'PING_CHILD' },
        
        // Permission requests
        'CAMERA_PERMISSION_REQUESTED': { status: 'permission', command: 'CAMERA_ON' },
        'MIC_PERMISSION_REQUESTED': { status: 'permission', command: 'MIC_ON' },
        'SCREEN_PERMISSION_REQUESTED': { status: 'permission', command: 'SCREEN_ON' },
        'LOCATION_PERMISSION_REQUESTED': { status: 'permission', command: 'LOCATE_CHILD' },
        
        // Errors
        'CAMERA_ERROR': { status: 'error', command: 'CAMERA_ON' },
        'MIC_ERROR': { status: 'error', command: 'MIC_ON' },
        'SCREEN_CAPTURE_ERROR': { status: 'error', command: 'SCREEN_ON' },
        'SCREEN_ERROR': { status: 'error', command: 'SCREEN_ON' },
        'LOCATION_ERROR': { status: 'error', command: 'LOCATE_CHILD' },
        'LOCATION_PERMISSION_DENIED': { status: 'error', command: 'LOCATE_CHILD' },
        'SCREEN_PERMISSION_ERROR': { status: 'error', command: 'SCREEN_ON' },
        'UNKNOWN_COMMAND': { status: 'error', command: 'UNKNOWN' }
      };
      
      // Check for exact matches first
      if (statusMap[message]) {
        const { status, command } = statusMap[message];
        this.showCommandFeedback(command, status);
        this.clearCommandTimeout(command);
        return;
      }
      
      // Check for error messages with details
      for (const [key, value] of Object.entries(statusMap)) {
        if (message.startsWith(key + ':')) {
          const errorMessage = message.substring(key.length + 1);
          this.showCommandFeedback(value.command, 'error', errorMessage);
          this.clearCommandTimeout(value.command);
          return;
        }
      }
      
      // Handle unknown messages
      console.log("📩 Unhandled message from child:", message);
      
      if (message === 'LOCATION_STARTED') {
        if (!locationStartedNotified) {
          this.showCommandFeedback('LOCATE_CHILD', 'success');
          locationStartedNotified = true;
        }
        this.clearCommandTimeout('LOCATE_CHILD');
        return;
      }
      if (message === 'LOCATION_STOPPED') {
        locationStartedNotified = false;
      }
      
    } catch (e) {
      console.error("❌ Error handling child message:", e);
    }
  }
  
  handleLocationUpdate(data) {
    try {
      const { coords, accuracy, timestamp } = data;
      const [lat, lng] = coords;
      
      console.log(`📍 Location update: ${lat}, ${lng} (accuracy: ${accuracy}m)`);
      
      // Update location on map if location manager exists
      if (window.locationManager) {
        window.locationManager.updateLocation(lat, lng);
      }
      
      // Show success feedback for location tracking
      this.showCommandFeedback('LOCATE_CHILD', 'success');
      this.clearCommandTimeout('LOCATE_CHILD');
      
    } catch (e) {
      console.error("❌ Error handling location update:", e);
    }
  }
  
  clearCommandTimeout(command) {
    if (this.commandTimeouts && this.commandTimeouts[command]) {
      clearTimeout(this.commandTimeouts[command]);
      delete this.commandTimeouts[command];
    }
  }

  // Get stream by type
  getStream(type) {
    return this.videoStreams[type] || null;
  }

  // Set callbacks
  setCallbacks(callbacks) {
    this.onConnectionEstablished = callbacks.onConnectionEstablished;
    this.onStreamReceived = callbacks.onStreamReceived;
    this.onDataChannelMessage = callbacks.onDataChannelMessage;
  }

  // Show microphone placeholder (inactive state)
  showMicrophonePlaceholder() {
    // Hide the audio wave indicator
    const audioWaveIndicator = document.getElementById('audio-wave-indicator');
    if (audioWaveIndicator) {
      audioWaveIndicator.classList.add('hidden');
    }
    
    // Reset mic button to initial state
    const micButton = document.getElementById('btn-mic');
    if (micButton) {
      micButton.textContent = '🎤 Mic';
      micButton.className = 'audio-control-btn text-xs font-semibold px-3 py-1 rounded transition border disabled:opacity-40 disabled:cursor-not-allowed bg-blue-600 text-white border-blue-700 hover:bg-blue-700';
    }
  }
  
  // Create audio visualization container (integrated with camera)
  createAudioVisualization() {
    // Show the audio wave indicator
    const audioWaveIndicator = document.getElementById('audio-wave-indicator');
    if (audioWaveIndicator) {
      audioWaveIndicator.classList.remove('hidden');
    }
    
    // Update mic button to show active state
    const micButton = document.getElementById('btn-mic');
    if (micButton) {
      micButton.textContent = '🔴 Live';
      micButton.className = 'audio-control-btn text-xs font-semibold px-3 py-1 rounded transition border disabled:opacity-40 disabled:cursor-not-allowed bg-red-600 text-white border-red-700 hover:bg-red-700';
    }
    
    console.log('✅ Audio visualization created (integrated)');
  }
  
  // Start wave animation based on audio levels
  startWaveAnimation(audioElement, stream) {
    try {
      console.log("🌊 Starting wave animation...");
      
      // First check if audio tracks are enabled and active
      const audioTracks = stream.getAudioTracks();
      if (audioTracks.length === 0) {
        console.log("⚠️ No audio tracks found, skipping wave animation");
        return;
      }
      
      const audioTrack = audioTracks[0];
      if (!audioTrack.enabled) {
        console.log("⚠️ Audio track disabled, skipping wave animation");
        return;
      }
      
      // Create audio context for analyzing audio
      const audioContext = new (window.AudioContext || window.webkitAudioContext)();
      const analyser = audioContext.createAnalyser();
      const source = audioContext.createMediaStreamSource(stream);
      
      // Configure analyser for better frequency analysis
      analyser.fftSize = 512;
      analyser.smoothingTimeConstant = 0.8;
      analyser.minDecibels = -90;
      analyser.maxDecibels = -10;
      
      const bufferLength = analyser.frequencyBinCount;
      const dataArray = new Uint8Array(bufferLength);
      
      source.connect(analyser);
      
      // Store for cleanup
      this.audioContext = audioContext;
      this.analyser = analyser;
      
      // Create visualization only when animation starts
      this.createAudioVisualization();
      
      // Start animation immediately but check for audio data
      console.log("📊 Starting animation with audio data monitoring...");
      this.startActualAnimation(analyser, dataArray, bufferLength);
      
      // Also monitor audio data levels for debugging
      this.monitorAudioLevels(analyser, dataArray, bufferLength);
      
      console.log("✅ Wave animation setup complete");
      
    } catch (error) {
      console.error("❌ Failed to start wave animation:", error);
      console.log("🔄 Falling back to CSS animation...");
      
      // Create visualization for fallback
      this.createAudioVisualization();
      
      // Fallback to CSS animation
      const waveBars = document.querySelectorAll('.wave-bar');
      if (waveBars.length > 0) {
        waveBars.forEach((bar, index) => {
          bar.style.animation = `wave ${0.6 + index * 0.1}s ease-in-out infinite alternate`;
        });
        console.log("✅ CSS fallback animation started");
      } else {
        console.error("❌ No wave bars found for fallback animation");
      }
    }
  }
  
  // Monitor audio levels for debugging
  monitorAudioLevels(analyser, dataArray, bufferLength) {
    setInterval(() => {
      if (!this.analyser) return;
      
      analyser.getByteFrequencyData(dataArray);
      let sum = 0;
      for (let i = 0; i < bufferLength; i++) {
        sum += dataArray[i];
      }
      const average = sum / bufferLength;
      
      if (average > 0) {
        console.log("📊 Audio level:", average.toFixed(2));
      }
    }, 2000); // Log every 2 seconds
  }
  
  // Start the actual wave animation
  startActualAnimation(analyser, dataArray, bufferLength) {
    // Animation function with improved frequency analysis
    const animate = () => {
      if (!this.analyser) return;
      
      analyser.getByteFrequencyData(dataArray);
      
      // Calculate average amplitude for overall volume
      let sum = 0;
      for (let i = 0; i < bufferLength; i++) {
        sum += dataArray[i];
      }
      const average = sum / bufferLength;
      
      // Use the small audio wave bars in the camera overlay
      const audioWaveBars = document.querySelectorAll('.audio-wave-bar');
      
      // Always animate, but scale based on audio data
      audioWaveBars.forEach((bar, index) => {
        // Use different frequency ranges for each bar
        const freqIndex = Math.floor((index + 1) * (bufferLength / audioWaveBars.length));
        const value = dataArray[freqIndex] || 0;
        
        // Scale height based on frequency data and average (smaller scale for overlay)
        const baseHeight = 12;
        const maxHeight = 20;
        
        // If no audio data, use a small random variation for visual appeal
        let height;
        if (average > 0.5) {
          height = baseHeight + (value / 255) * (maxHeight - baseHeight);
        } else {
          // Gentle idle animation when no audio
          height = baseHeight + Math.sin(Date.now() * 0.001 + index) * 2;
        }
        
        // Smooth the animation
        bar.style.height = `${Math.max(baseHeight, height)}px`;
        
        // Add dynamic color based on intensity
        const intensity = value / 255;
        if (intensity > 0.7) {
          bar.style.background = 'linear-gradient(45deg, #ef4444, #f87171)';
        } else if (intensity > 0.4) {
          bar.style.background = 'linear-gradient(45deg, #f59e0b, #fbbf24)';
        } else {
          bar.style.background = 'linear-gradient(45deg, #10b981, #34d399)';
        }
      });
      
      requestAnimationFrame(animate);
    };
    
    animate();
  }
  
  // Stop wave animation
  stopWaveAnimation() {
    if (this.audioContext) {
      this.audioContext.close();
      this.audioContext = null;
    }
    this.analyser = null;
    
    // Reset wave bars
    const waveBars = document.querySelectorAll('.wave-bar');
    waveBars.forEach(bar => {
      bar.style.height = '20px';
      bar.style.animation = 'none';
    });
    
    // Return to placeholder state
    this.showMicrophonePlaceholder();
  }

  // Clean up connection
  cleanup() {
    // Stop wave animation
    this.stopWaveAnimation();
    
    if (this.peerConnection) {
      this.peerConnection.close();
      this.peerConnection = null;
    }
    this.dataChannel = null;
    this.videoStreams = {};
    this.currentChildId = null;
  }

  startMicMetricsMonitor(stream) {
    // Use WebRTC stats API to get bitrate, packet loss, jitter
    const metricsEl = document.getElementById('mic-metrics');
    if (!metricsEl || !this.peerConnection) return;
    let lastBytes = 0;
    let lastTimestamp = 0;
    const update = () => {
      this.peerConnection.getStats(null).then(stats => {
        let bitrate = '--', packetLoss = '--', jitter = '--';
        stats.forEach(report => {
          if (report.type === 'inbound-rtp' && report.kind === 'audio') {
            if (lastBytes && lastTimestamp) {
              const deltaBytes = report.bytesReceived - lastBytes;
              const deltaTime = (report.timestamp - lastTimestamp) / 1000;
              bitrate = deltaTime > 0 ? ((deltaBytes * 8) / deltaTime / 1000).toFixed(1) + ' kbps' : '--';
            }
            lastBytes = report.bytesReceived;
            lastTimestamp = report.timestamp;
            if (report.packetsLost !== undefined && report.packetsReceived !== undefined) {
              const total = report.packetsLost + report.packetsReceived;
              packetLoss = total > 0 ? ((report.packetsLost / total) * 100).toFixed(2) + '%' : '0%';
            }
            if (report.jitter !== undefined) {
              jitter = (report.jitter * 1000).toFixed(1) + ' ms';
            }
          }
        });
        metricsEl.textContent = `Bitrate: ${bitrate} | Packet Loss: ${packetLoss} | Jitter: ${jitter}`;
      });
      setTimeout(update, 2000);
    };
    update();
  }
}

// Export for use in other modules
window.WebRTCManager = WebRTCManager;
