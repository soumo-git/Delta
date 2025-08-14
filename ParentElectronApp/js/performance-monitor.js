// Performance Monitoring Module
console.log("✅ performance-monitor.js loaded");

class PerformanceMonitor {
  constructor() {
    this.metrics = {
      connection: {
        latency: [],
        packetLoss: 0,
        bandwidth: null,
        quality: 'unknown'
      },
      streams: {
        camera: { fps: [], bitrate: [], resolution: null },
        screen: { fps: [], bitrate: [], resolution: null },
        mic: { volume: [], bitrate: [] }
      },
      resources: {
        memory: [],
        cpu: [],
        network: []
      }
    };
    
    this.monitoringInterval = null;
    this.statsInterval = null;
    this.peerConnection = null;
    this.dataChannel = null;
    
    this.init();
  }
  
  init() {
    // Start resource monitoring
    this.startResourceMonitoring();
    
    // Set up periodic stats collection
    this.statsInterval = setInterval(() => {
      this.collectStats();
    }, 5000); // Collect stats every 5 seconds
  }
  
  setPeerConnection(pc) {
    this.peerConnection = pc;
    this.startConnectionMonitoring();
  }
  
  setDataChannel(dc) {
    this.dataChannel = dc;
  }
  
  startConnectionMonitoring() {
    if (!this.peerConnection) return;
    
    // Monitor connection state changes
    this.peerConnection.onconnectionstatechange = () => {
      this.updateConnectionQuality();
    };
    
    // Monitor ICE connection state
    this.peerConnection.oniceconnectionstatechange = () => {
      this.updateConnectionQuality();
    };
  }
  
  startResourceMonitoring() {
    this.monitoringInterval = setInterval(() => {
      this.collectResourceMetrics();
    }, 10000); // Monitor resources every 10 seconds
  }
  
  collectStats() {
    if (!this.peerConnection) return;
    
    try {
      // Get connection stats
      this.peerConnection.getStats().then(stats => {
        stats.forEach(report => {
          this.processStatsReport(report);
        });
      }).catch(err => {
        console.warn("⚠️ Failed to collect WebRTC stats:", err);
      });
    } catch (err) {
      console.warn("⚠️ Error collecting stats:", err);
    }
  }
  
  processStatsReport(report) {
    const { type, id } = report;
    
    switch (type) {
      case 'candidate-pair':
        this.processCandidatePairStats(report);
        break;
      case 'inbound-rtp':
        this.processInboundRtpStats(report);
        break;
      case 'outbound-rtp':
        this.processOutboundRtpStats(report);
        break;
      case 'media-source':
        this.processMediaSourceStats(report);
        break;
      case 'track':
        this.processTrackStats(report);
        break;
    }
  }
  
  processCandidatePairStats(stats) {
    if (stats.state === 'succeeded') {
      // Calculate latency
      if (stats.currentRoundTripTime) {
        const latency = stats.currentRoundTripTime * 1000; // Convert to ms
        this.metrics.connection.latency.push(latency);
        
        // Keep only last 10 measurements
        if (this.metrics.connection.latency.length > 10) {
          this.metrics.connection.latency.shift();
        }
        
        // Update average latency
        const avgLatency = this.metrics.connection.latency.reduce((a, b) => a + b, 0) / this.metrics.connection.latency.length;
        this.metrics.connection.latency = avgLatency;
      }
      
      // Calculate packet loss
      if (stats.packetsLost !== undefined && stats.packetsReceived !== undefined) {
        const totalPackets = stats.packetsLost + stats.packetsReceived;
        if (totalPackets > 0) {
          this.metrics.connection.packetLoss = (stats.packetsLost / totalPackets) * 100;
        }
      }
    }
  }
  
  processInboundRtpStats(stats) {
    // Process incoming video/audio stats
    if (stats.mediaType === 'video') {
      this.updateVideoStats(stats, 'inbound');
    } else if (stats.mediaType === 'audio') {
      this.updateAudioStats(stats, 'inbound');
    }
  }
  
  processOutboundRtpStats(stats) {
    // Process outgoing video/audio stats
    if (stats.mediaType === 'video') {
      this.updateVideoStats(stats, 'outbound');
    } else if (stats.mediaType === 'audio') {
      this.updateAudioStats(stats, 'outbound');
    }
  }
  
  processMediaSourceStats(stats) {
    // Process media source stats (camera, screen, mic)
    if (stats.kind === 'video') {
      this.updateVideoSourceStats(stats);
    } else if (stats.kind === 'audio') {
      this.updateAudioSourceStats(stats);
    }
  }
  
  processTrackStats(stats) {
    // Process track-specific stats
    if (stats.kind === 'video') {
      this.updateVideoTrackStats(stats);
    } else if (stats.kind === 'audio') {
      this.updateAudioTrackStats(stats);
    }
  }
  
  updateVideoStats(stats, direction) {
    const streamType = this.determineStreamType(stats);
    if (!streamType) return;
    
    // Update FPS
    if (stats.framesPerSecond) {
      this.metrics.streams[streamType].fps.push(stats.framesPerSecond);
      if (this.metrics.streams[streamType].fps.length > 10) {
        this.metrics.streams[streamType].fps.shift();
      }
    }
    
    // Update bitrate
    if (stats.bytesReceived || stats.bytesSent) {
      const bytes = direction === 'inbound' ? stats.bytesReceived : stats.bytesSent;
      if (bytes) {
        const bitrate = (bytes * 8) / 1000; // Convert to kbps
        this.metrics.streams[streamType].bitrate.push(bitrate);
        if (this.metrics.streams[streamType].bitrate.length > 10) {
          this.metrics.streams[streamType].bitrate.shift();
        }
      }
    }
  }
  
  updateAudioStats(stats, direction) {
    // Update audio metrics
    if (stats.bytesReceived || stats.bytesSent) {
      const bytes = direction === 'inbound' ? stats.bytesReceived : stats.bytesSent;
      if (bytes) {
        const bitrate = (bytes * 8) / 1000; // Convert to kbps
        this.metrics.streams.mic.bitrate.push(bitrate);
        if (this.metrics.streams.mic.bitrate.length > 10) {
          this.metrics.streams.mic.bitrate.shift();
        }
      }
    }
  }
  
  updateVideoSourceStats(stats) {
    const streamType = this.determineStreamType(stats);
    if (!streamType) return;
    
    // Update resolution
    if (stats.width && stats.height) {
      this.metrics.streams[streamType].resolution = `${stats.width}x${stats.height}`;
    }
  }
  
  updateAudioSourceStats(stats) {
    // Update audio source metrics
    if (stats.audioLevel !== undefined) {
      this.metrics.streams.mic.volume.push(stats.audioLevel);
      if (this.metrics.streams.mic.volume.length > 10) {
        this.metrics.streams.mic.volume.shift();
      }
    }
  }
  
  updateVideoTrackStats(stats) {
    const streamType = this.determineStreamType(stats);
    if (!streamType) return;
    
    // Update track-specific metrics
    if (stats.frameWidth && stats.frameHeight) {
      this.metrics.streams[streamType].resolution = `${stats.frameWidth}x${stats.frameHeight}`;
    }
  }
  
  updateAudioTrackStats(stats) {
    // Update audio track metrics
    if (stats.audioLevel !== undefined) {
      this.metrics.streams.mic.volume.push(stats.audioLevel);
      if (this.metrics.streams.mic.volume.length > 10) {
        this.metrics.streams.mic.volume.shift();
      }
    }
  }
  
  determineStreamType(stats) {
    // Determine if this is camera or screen based on track ID or other indicators
    if (stats.trackId && stats.trackId.includes('screen')) {
      return 'screen';
    } else if (stats.trackId && stats.trackId.includes('camera')) {
      return 'camera';
    }
    
    // Fallback: assume camera if we don't have screen stream
    return this.metrics.streams.screen.bitrate.length > 0 ? 'camera' : 'camera';
  }
  
  collectResourceMetrics() {
    // Collect system resource usage
    if (navigator.memory) {
      this.metrics.resources.memory.push({
        used: navigator.memory.usedJSHeapSize,
        total: navigator.memory.totalJSHeapSize,
        limit: navigator.memory.jsHeapSizeLimit,
        timestamp: Date.now()
      });
      
      // Keep only last 20 measurements
      if (this.metrics.resources.memory.length > 20) {
        this.metrics.resources.memory.shift();
      }
    }
    
    // Collect network information if available
    if (navigator.connection) {
      this.metrics.resources.network.push({
        effectiveType: navigator.connection.effectiveType,
        downlink: navigator.connection.downlink,
        rtt: navigator.connection.rtt,
        timestamp: Date.now()
      });
      
      if (this.metrics.resources.network.length > 20) {
        this.metrics.resources.network.shift();
      }
    }
  }
  
  updateConnectionQuality() {
    const latency = this.metrics.connection.latency;
    const packetLoss = this.metrics.connection.packetLoss;
    
    let quality = 'unknown';
    
    if (latency && packetLoss !== undefined) {
      if (latency < 100 && packetLoss < 1) {
        quality = 'excellent';
      } else if (latency < 300 && packetLoss < 5) {
        quality = 'good';
      } else if (latency < 1000 && packetLoss < 10) {
        quality = 'poor';
      } else {
        quality = 'very_poor';
      }
    }
    
    this.metrics.connection.quality = quality;
    this.updatePerformanceDisplay();
  }
  
  updatePerformanceDisplay() {
    // Update UI with performance metrics
    const performanceEl = document.getElementById('performance-display');
    if (!performanceEl) return;
    
    const metrics = this.metrics;
    const html = `
      <div class="performance-metrics">
        <div class="metric-group">
          <h4>Connection Quality</h4>
          <div class="metric">
            <span class="label">Quality:</span>
            <span class="value quality-${metrics.connection.quality}">${metrics.connection.quality}</span>
          </div>
          <div class="metric">
            <span class="label">Latency:</span>
            <span class="value">${metrics.connection.latency ? Math.round(metrics.connection.latency) + 'ms' : 'N/A'}</span>
          </div>
          <div class="metric">
            <span class="label">Packet Loss:</span>
            <span class="value">${metrics.connection.packetLoss ? metrics.connection.packetLoss.toFixed(2) + '%' : 'N/A'}</span>
          </div>
        </div>
        
        <div class="metric-group">
          <h4>Stream Performance</h4>
          ${this.generateStreamMetrics()}
        </div>
        
        <div class="metric-group">
          <h4>System Resources</h4>
          ${this.generateResourceMetrics()}
        </div>
      </div>
    `;
    
    performanceEl.innerHTML = html;
  }
  
  generateStreamMetrics() {
    const streams = this.metrics.streams;
    let html = '';
    
    Object.keys(streams).forEach(type => {
      const stream = streams[type];
      if (stream.fps && stream.fps.length > 0) {
        const avgFps = stream.fps.reduce((a, b) => a + b, 0) / stream.fps.length;
        html += `
          <div class="metric">
            <span class="label">${type} FPS:</span>
            <span class="value">${avgFps.toFixed(1)}</span>
          </div>
        `;
      }
      
      if (stream.bitrate && stream.bitrate.length > 0) {
        const avgBitrate = stream.bitrate.reduce((a, b) => a + b, 0) / stream.bitrate.length;
        html += `
          <div class="metric">
            <span class="label">${type} Bitrate:</span>
            <span class="value">${avgBitrate.toFixed(1)} kbps</span>
          </div>
        `;
      }
      
      if (stream.resolution) {
        html += `
          <div class="metric">
            <span class="label">${type} Resolution:</span>
            <span class="value">${stream.resolution}</span>
          </div>
        `;
      }
    });
    
    return html || '<div class="metric">No active streams</div>';
  }
  
  generateResourceMetrics() {
    const resources = this.metrics.resources;
    let html = '';
    
    if (resources.memory && resources.memory.length > 0) {
      const latest = resources.memory[resources.memory.length - 1];
      const memoryUsage = ((latest.used / latest.total) * 100).toFixed(1);
      html += `
        <div class="metric">
          <span class="label">Memory Usage:</span>
          <span class="value">${memoryUsage}%</span>
        </div>
      `;
    }
    
    if (resources.network && resources.network.length > 0) {
      const latest = resources.network[resources.network.length - 1];
      html += `
        <div class="metric">
          <span class="label">Network:</span>
          <span class="value">${latest.effectiveType} (${latest.downlink}Mbps)</span>
        </div>
      `;
    }
    
    return html || '<div class="metric">No resource data</div>';
  }
  
  getMetrics() {
    return this.metrics;
  }
  
  getConnectionQuality() {
    return this.metrics.connection.quality;
  }
  
  getAverageLatency() {
    return this.metrics.connection.latency;
  }
  
  getPacketLoss() {
    return this.metrics.connection.packetLoss;
  }
  
  cleanup() {
    if (this.monitoringInterval) {
      clearInterval(this.monitoringInterval);
      this.monitoringInterval = null;
    }
    
    if (this.statsInterval) {
      clearInterval(this.statsInterval);
      this.statsInterval = null;
    }
    
    this.peerConnection = null;
    this.dataChannel = null;
  }
}

// Export for use in other modules
window.PerformanceMonitor = PerformanceMonitor; 