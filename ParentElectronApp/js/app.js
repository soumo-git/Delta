// Main Application Controller
console.log("✅ app.js loaded");

class ParentApp {
  constructor() {
    this.ui = null;
    this.webrtcManager = null;
    this.connectScreen = null;
    this.dashboard = null;
    this.authManager = null;
    this.currentState = 'initializing';
    this.init();
  }

  async init() {
    console.log('🚀 Initializing Parent App...');
    
    // Wait for DOM to be ready
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', () => this.setup());
    } else {
      this.setup();
    }
  }

  setup() {
    // Initialize components
    this.ui = new window.UIComponents();
    this.webrtcManager = new window.WebRTCManager();
    this.authManager = window.authManager;
    
    // Initialize screens
    this.connectScreen = new window.ConnectScreen(this.ui, this.webrtcManager);
    this.dashboard = new window.Dashboard(this.ui, this.webrtcManager);
    
    // Set up connection callback
    this.connectScreen.onConnect((childId, peerConnection, dataChannel) => {
      this.handleConnectionSuccess(childId, peerConnection, dataChannel);
    });
    
    // Set up authentication event listeners
    this.setupAuthEventListeners();
    
    // Set up heading animation
    this.setupHeadingAnimation();
    
    // Check authentication status and show appropriate screen
    this.checkAuthAndShowScreen();
    
    // Make app globally available for UI callbacks
    window.parentApp = this;
    
    console.log('✅ Parent App initialized');
  }

  setupAuthEventListeners() {
    // Google sign-in button
    const googleSignInBtn = document.getElementById('google-signin-btn');
    if (googleSignInBtn) {
      googleSignInBtn.addEventListener('click', async () => {
        try {
          await this.authManager.signInWithGoogle();
        } catch (error) {
          console.error('❌ Sign-in failed:', error);
          // You could show an error message to the user here
        }
      });
    }

    // Continue to dashboard button
    const continueBtn = document.getElementById('continue-btn');
    if (continueBtn) {
      continueBtn.addEventListener('click', () => {
        this.showConnectScreen();
      });
    }

    // Sign out button
    const signOutBtn = document.getElementById('signout-btn');
    if (signOutBtn) {
      signOutBtn.addEventListener('click', async () => {
        try {
          await this.authManager.signOut();
        } catch (error) {
          console.error('❌ Sign-out failed:', error);
        }
      });
    }
  }

  setupHeadingAnimation() {
    const heading = document.getElementById('project-delta-heading');
    if (!heading) return;

    const originalText = heading.getAttribute('data-original-text');
    const hoverText = heading.getAttribute('data-hover-text');
    let isAnimating = false;

    // Create a span for the text content to isolate it from any potential icons
    const textSpan = document.createElement('span');
    textSpan.textContent = originalText;
    textSpan.classList.add('heading-text');
    
    // Clear heading and add the text span
    heading.innerHTML = '';
    heading.appendChild(textSpan);

    // Add typing animation class initially to the text span
    textSpan.classList.add('terminal-typing');

    // Remove typing animation after it completes
    setTimeout(() => {
      textSpan.classList.remove('terminal-typing');
      textSpan.style.borderRight = 'none';
    }, 2500);

    // Function to animate text change with letter-by-letter effect
    const animateTextChange = (newText, direction = 'up') => {
      if (isAnimating) return;
      isAnimating = true;

      const currentText = textSpan.textContent;
      const maxLength = Math.max(currentText.length, newText.length);
      
      // Create individual letter spans
      const letters = [];
      for (let i = 0; i < maxLength; i++) {
        const letterSpan = document.createElement('span');
        letterSpan.textContent = i < currentText.length ? currentText[i] : ' ';
        letterSpan.style.display = 'inline-block';
        letterSpan.style.transition = 'all 0.3s ease-in-out';
        // Preserve spaces by setting white-space for space characters
        if (currentText[i] === ' ') {
          letterSpan.style.whiteSpace = 'pre';
        }
        letters.push(letterSpan);
      }

      // Clear text span and add letter spans
      textSpan.innerHTML = '';
      letters.forEach(letter => textSpan.appendChild(letter));

      // Animate letters out
      letters.forEach((letter, index) => {
        setTimeout(() => {
          letter.style.transform = direction === 'up' ? 'translateY(-20px)' : 'translateY(20px)';
          letter.style.opacity = '0';
        }, index * 25);
      });

      // Change text and animate letters in
      setTimeout(() => {
        letters.forEach((letter, index) => {
          const newChar = index < newText.length ? newText[index] : ' ';
          letter.textContent = newChar;
          // Preserve spaces in new text
          if (newChar === ' ') {
            letter.style.whiteSpace = 'pre';
          }
          letter.style.transform = direction === 'up' ? 'translateY(20px)' : 'translateY(-20px)';
          letter.style.opacity = '0';
        });

        letters.forEach((letter, index) => {
          setTimeout(() => {
            letter.style.transform = 'translateY(0)';
            letter.style.opacity = '1';
          }, index * 25);
        });
      }, maxLength * 25 + 50);

      // Reset animation flag
      setTimeout(() => {
        isAnimating = false;
      }, maxLength * 50 + 100);
    };

    // Hover effect
    heading.addEventListener('mouseenter', () => {
      animateTextChange(hoverText, 'up');
    });

    heading.addEventListener('mouseleave', () => {
      animateTextChange(originalText, 'down');
    });
  }

  async checkAuthAndShowScreen() {
    console.log('🔍 Checking authentication status...');
    
    // Wait a bit for auth state to be determined
    await new Promise(resolve => setTimeout(resolve, 1000));
    
    if (this.authManager.isAuthenticated()) {
      if (this.authManager.canAccessApp()) {
        console.log('✅ User authenticated and has active subscription');
        this.showAuthScreen(); // Show auth screen (which will display user info)
      } else {
        console.log('⚠️ User authenticated but subscription expired');
        this.showAuthScreen();
      }
    } else {
      console.log('❌ User not authenticated');
      this.showAuthScreen();
    }
  }

  showAuthScreen() {
    this.currentState = 'auth';
    this.ui.showScreen('auth');
    console.log('🔐 Auth screen shown');
  }

  showConnectScreen() {
    this.currentState = 'connecting';
    this.connectScreen.show();
    console.log('📱 Connect screen shown');
  }

  handleConnectionSuccess(childId, peerConnection, dataChannel) {
    console.log('🎉 Connection established with child:', childId);
    
    this.currentState = 'connected';
    
    // Hide connect screen
    this.connectScreen.hide();
    
    // Show dashboard
    this.dashboard.show(childId, peerConnection, dataChannel);
    
    // Set up stream handling
    this.setupStreamHandling();
  }

  setupStreamHandling() {
    // The WebRTC manager will handle stream events and notify the dashboard
    // through the callback system we set up
    console.log('🎥 Stream handling setup complete');
  }

  showDashboard() {
    if (this.currentState === 'connected') {
      this.ui.showScreen('dashboard');
      console.log('🎛️ Dashboard shown');
    } else {
      console.warn('⚠️ Cannot show dashboard - not connected');
    }
  }

  // Methods called by UI components
  toggleStream(type) {
    if (this.currentState === 'connected') {
      this.dashboard.toggleStream(type);
    }
  }

  sendCommand(command) {
    if (this.currentState === 'connected') {
      return this.dashboard.sendCommand(command);
    }
    return false;
  }

  // Disconnect and return to connect screen
  disconnect() {
    if (this.currentState === 'connected') {
      this.dashboard.disconnect();
      this.showConnectScreen();
    }
  }

  // Get app status
  getStatus() {
    return {
      state: this.currentState,
      authenticated: this.authManager ? this.authManager.isAuthenticated() : false,
      subscriptionActive: this.authManager ? this.authManager.canAccessApp() : false,
      dashboard: this.dashboard ? this.dashboard.getConnectionStatus() : null
    };
  }

  // Get current user info
  getCurrentUser() {
    return this.authManager ? this.authManager.getCurrentUser() : null;
  }

  // Get subscription info
  getSubscriptionInfo() {
    return this.authManager ? this.authManager.getUserSubscription() : null;
  }

  // Debug audio functionality
  debugAudio() {
    const audioElement = document.getElementById('video-mic');
    if (audioElement) {
      console.log('🎧 Audio Element Debug:');
      console.log('- srcObject:', audioElement.srcObject);
      console.log('- muted:', audioElement.muted);
      console.log('- volume:', audioElement.volume);
      console.log('- paused:', audioElement.paused);
      console.log('- readyState:', audioElement.readyState);
      console.log('- currentTime:', audioElement.currentTime);
      console.log('- duration:', audioElement.duration);
      console.log('- ended:', audioElement.ended);
      console.log('- autoplay:', audioElement.autoplay);
      console.log('- playbackRate:', audioElement.playbackRate);
      
      if (audioElement.srcObject) {
        const audioTracks = audioElement.srcObject.getAudioTracks();
        console.log('- Audio tracks:', audioTracks.length);
        audioTracks.forEach((track, i) => {
          console.log(`  Track ${i}:`, {
            id: track.id,
            enabled: track.enabled,
            muted: track.muted,
            readyState: track.readyState,
            kind: track.kind,
            label: track.label
          });
        });
        
        // Check if stream is active
        console.log('- Stream active:', audioElement.srcObject.active);
        console.log('- Stream id:', audioElement.srcObject.id);
      }
      
      // Check audio context state
      if (typeof AudioContext !== 'undefined') {
        try {
          const audioContext = new (window.AudioContext || window.webkitAudioContext)();
          console.log('- Audio context state:', audioContext.state);
          console.log('- Audio context sample rate:', audioContext.sampleRate);
        } catch (e) {
          console.error('- Failed to create audio context:', e);
        }
      }
    } else {
      console.log('❌ Audio element not found');
    }
  }
  
  // Force audio playback
  forceAudioPlay() {
    const audioElement = document.getElementById('video-mic');
    if (audioElement && audioElement.srcObject) {
      console.log('🎵 Forcing audio playback...');
      audioElement.muted = false;
      audioElement.volume = 1.0;
      audioElement.play()
        .then(() => {
          console.log('✅ Audio forced to play successfully');
        })
        .catch(err => {
          console.error('❌ Failed to force audio play:', err);
        });
    } else {
      console.log('❌ No audio element or stream found');
    }
  }

  // Clean up when app is closing
  cleanup() {
    console.log('🧹 Cleaning up Parent App...');
    
    if (this.authManager) {
      this.authManager.cleanup();
    }
    
    if (this.connectScreen) {
      this.connectScreen.destroy();
    }
    
    if (this.dashboard) {
      this.dashboard.disconnect();
    }
    
    if (this.webrtcManager) {
      this.webrtcManager.cleanup();
    }
    
    console.log('✅ Parent App cleanup complete');
  }
}

// Initialize app when script loads
document.addEventListener('DOMContentLoaded', () => {
  window.parentApp = new ParentApp();
});

// Handle app cleanup on window close
window.addEventListener('beforeunload', () => {
  if (window.parentApp) {
    window.parentApp.cleanup();
  }
});

// Export for use in other modules
window.ParentApp = ParentApp;

// Global debug functions
window.debugAudio = () => {
  if (window.parentApp) {
    window.parentApp.debugAudio();
  } else {
    console.log('❌ Parent app not initialized');
  }
};

window.testAudioElement = () => {
  const audioElement = document.getElementById('video-mic');
  if (audioElement) {
    console.log('🎧 Testing audio element manually...');
    audioElement.play()
      .then(() => console.log('✅ Manual audio play successful'))
      .catch(err => console.error('❌ Manual audio play failed:', err));
  } else {
    console.log('❌ Audio element not found');
  }
};

window.forceAudioPlay = () => {
  if (window.parentApp) {
    window.parentApp.forceAudioPlay();
  } else {
    console.log('❌ Parent app not initialized');
  }
};

window.checkAudioPermissions = () => {
  navigator.mediaDevices.getUserMedia({ audio: true })
    .then(stream => {
      console.log('✅ Audio permissions granted');
      stream.getTracks().forEach(track => track.stop());
    })
    .catch(err => {
      console.error('❌ Audio permissions denied:', err);
    });
};

window.debugScreenStream = () => {
  console.log('🖥️ Screen Stream Debug:');
  
  const screenElement = document.getElementById('video-screen');
  console.log('- Screen video element:', screenElement);
  
  if (screenElement) {
    console.log('- srcObject:', screenElement.srcObject);
    console.log('- paused:', screenElement.paused);
    console.log('- readyState:', screenElement.readyState);
    console.log('- videoWidth:', screenElement.videoWidth);
    console.log('- videoHeight:', screenElement.videoHeight);
    console.log('- Quality:', `${screenElement.videoWidth}x${screenElement.videoHeight}`);
    console.log('- Aspect ratio:', (screenElement.videoWidth / screenElement.videoHeight).toFixed(2));
    
    if (screenElement.srcObject) {
      const videoTracks = screenElement.srcObject.getVideoTracks();
      console.log('- Video tracks:', videoTracks.length);
      videoTracks.forEach((track, i) => {
        console.log(`  Track ${i}:`, {
          id: track.id,
          label: track.label,
          enabled: track.enabled,
          muted: track.muted,
          readyState: track.readyState
        });
      });
    }
  }
  
  const placeholder = document.getElementById('placeholder-screen');
  console.log('- Screen placeholder:', placeholder);
  if (placeholder) {
    console.log('- Placeholder hidden:', placeholder.classList.contains('hidden'));
  }
};

window.debugWaveAnimation = () => {
  console.log('🌊 Wave Animation Debug:');
  
  const micWindow = document.getElementById('stream-mic');
  console.log('- Mic window:', micWindow);
  
  const waveContainer = document.getElementById('wave-container');
  console.log('- Wave container:', waveContainer);
  
  const waveBars = document.querySelectorAll('.wave-bar');
  console.log('- Wave bars count:', waveBars.length);
  
  waveBars.forEach((bar, index) => {
    console.log(`  Bar ${index}:`, {
      height: bar.style.height,
      background: bar.style.background,
      animation: bar.style.animation
    });
  });
  
  const micIcon = document.querySelector('.mic-icon');
  console.log('- Mic icon:', micIcon);
  
  // Check if WebRTC manager has audio context
  if (window.parentApp && window.parentApp.webrtcManager) {
    const manager = window.parentApp.webrtcManager;
    console.log('- Audio context:', manager.audioContext);
    console.log('- Analyser:', manager.analyser);
  }
};

window.forceWaveAnimation = () => {
  console.log('🌊 Forcing wave animation...');
  
  const waveBars = document.querySelectorAll('.wave-bar');
  if (waveBars.length > 0) {
    waveBars.forEach((bar, index) => {
      bar.style.animation = `wave ${0.6 + index * 0.1}s ease-in-out infinite alternate`;
    });
    console.log('✅ Forced CSS animation on', waveBars.length, 'bars');
  } else {
    console.log('❌ No wave bars found');
  }
};
