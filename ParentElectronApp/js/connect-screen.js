// Connect Screen Component
console.log("✅ connect-screen.js loaded");

class ConnectScreen {
  constructor(ui, webrtcManager) {
    this.ui = ui;
    this.webrtcManager = webrtcManager;
    this.particlesBackground = null;
    this.onConnectionSuccess = null;
    this.init();
  }

  init() {
    this.setupParticlesBackground();
    this.setupEventListeners();
  }

  setupParticlesBackground() {
    const canvas = document.getElementById('particles-canvas');
    if (canvas) {
      this.particlesBackground = new window.ParticlesBackground(canvas);
    }
  }

  setupEventListeners() {
    const connectBtn = document.getElementById('connect-btn');
    const childIdInput = document.getElementById('child-id-input');

    if (connectBtn) {
      connectBtn.addEventListener('click', () => this.handleConnect());
    }

    if (childIdInput) {
      childIdInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
          this.handleConnect();
        }
      });

      // Auto-format input to show only 12 digits
      childIdInput.addEventListener('input', (e) => {
        let value = e.target.value.replace(/\D/g, ''); // Remove non-digits
        if (value.length > 12) {
          value = value.substring(0, 12);
        }
        e.target.value = value;
      });
    }
  }

  async handleConnect() {
    const childIdInput = document.getElementById('child-id-input');
    const connectBtn = document.getElementById('connect-btn');
    
    if (!childIdInput || !connectBtn) return;

    const childId = childIdInput.value.trim();
    
    // Validate 12-digit ID
    if (!/^\d{12}$/.test(childId)) {
      this.ui.showNotification('❌ Please enter a valid 12-digit Child ID', 'error');
      return;
    }

    console.log('🔗 Connecting to Child ID:', childId);
    
    // Show loading state
    this.ui.showLoading(connectBtn, true);

    try {
      // Set up WebRTC callbacks
      this.webrtcManager.setCallbacks({
        onConnectionEstablished: (dataChannel) => {
          console.log('✅ Connection established');
          this.ui.showNotification('✅ Connected successfully!', 'success');
          this.ui.showLoading(connectBtn, false);
          
          // Switch to dashboard
          if (this.onConnectionSuccess) {
            this.onConnectionSuccess(childId, this.webrtcManager.peerConnection, dataChannel);
          }
        },
        onStreamReceived: (trackKind, stream) => {
          console.log(`🎥 Stream received: ${trackKind}`);
          // This will be handled by the dashboard
        },
        onDataChannelMessage: (message) => {
          console.log('📩 Message from child:', message);
        }
      });

      // Store child ID for ICE candidate handling
      this.webrtcManager.currentChildId = childId;
      
      // Initialize WebRTC connection
      await this.webrtcManager.initializeConnection(childId);
      
    } catch (error) {
      console.error('❌ Connection failed:', error);
      this.ui.showNotification('❌ Connection failed. Please try again.', 'error');
      this.ui.showLoading(connectBtn, false);
    }
  }

  // Set callback for successful connection
  onConnect(callback) {
    this.onConnectionSuccess = callback;
  }

  // Show the connect screen
  show() {
    this.ui.showScreen('connect');
    
    // Reset form
    const childIdInput = document.getElementById('child-id-input');
    const connectBtn = document.getElementById('connect-btn');
    
    if (childIdInput) {
      childIdInput.value = '';
      childIdInput.focus();
    }
    
    if (connectBtn) {
      this.ui.showLoading(connectBtn, false);
    }
  }

  // Hide the connect screen
  hide() {
    // Clean up particles background if needed
    if (this.particlesBackground) {
      this.particlesBackground.destroy();
      this.particlesBackground = null;
    }
  }

  // Destroy the connect screen
  destroy() {
    this.hide();
    this.onConnectionSuccess = null;
  }
}

// Export for use in other modules
window.ConnectScreen = ConnectScreen;
