// UI Components Manager
console.log("✅ ui-components.js loaded");

class UIComponents {
  constructor() {
    this.videoStreams = {};
    this.currentScreen = 'connect';
    this.elements = {};
  }

  // Create stream window component
  createStreamWindow(label, containerElement) {
    const streamWindow = document.createElement('div');
    const isCamera = label.toLowerCase() === 'camera';
    const isScreen = label.toLowerCase() === 'screen';
    const isMic = label.toLowerCase() === 'mic';
    // Use aspect-video and w-full only for camera, not screen
    streamWindow.className = 'border border-zinc-600 bg-zinc-900 rounded overflow-hidden relative transition-all duration-300 animate-fadeIn' + (isCamera ? ' aspect-video w-full' : '');
    streamWindow.id = `stream-${label.toLowerCase()}`;
    
    // Create media element
    const mediaElement = `<video 
       autoplay 
       playsinline 
       muted 
       class="w-full h-full object-cover hidden"
       id="video-${label.toLowerCase()}"
     ></video>`;
    
    // No audio element in camera window
    const audioElement = '';
    
    // No audio controls in camera window
    const audioControls = '';
    
    streamWindow.innerHTML = `
      <!-- Stream Container -->
      <div class="w-full h-full flex items-center justify-center text-zinc-500">
        ${mediaElement}
        ${audioElement}
        <span class="stream-placeholder" id="placeholder-${label.toLowerCase()}">${label} Stream</span>
      </div>
      
      <!-- Controls Overlay -->
      <div class="absolute top-2 right-2 flex gap-2 z-10">
        <button 
          onclick="toggleFullscreen('${label.toLowerCase()}')"
          class="bg-black/60 hover:bg-black/80 text-white px-2 py-1 rounded text-xs border border-white transition"
        >
          ↙️↗️
        </button>
      </div>
      
      ${audioControls}
      
      <!-- Stream Control Button -->
      <div class="absolute bottom-2 right-2 z-10">
        <button 
          onclick="toggleStream('${label.toLowerCase()}')"
          class="stream-control-btn text-xs font-semibold px-3 py-1 rounded transition border disabled:opacity-40 disabled:cursor-not-allowed bg-green-600 text-white border-green-700 hover:bg-green-700"
          id="btn-${label.toLowerCase()}"
          disabled
        >
          Start
        </button>
      </div>
      
      <!-- Label bottom left -->
      <div class="absolute bottom-2 left-2 text-xs bg-black/60 px-2 py-1 rounded text-zinc-300">
        ${label}
      </div>
    `;
    
    containerElement.appendChild(streamWindow);
    return streamWindow;
  }

  // Update stream in video element - simplified since WebRTC manager handles this directly
  updateStream(type, stream) {
    console.log(`🎬 Stream update request for ${type} - handled by WebRTC manager`);
    // WebRTC manager now handles video elements directly
    this.videoStreams[type] = stream;
  }

  // Enable/disable stream control buttons
  enableStreamControls(enabled) {
    const buttons = document.querySelectorAll('.stream-control-btn');
    buttons.forEach(btn => {
      btn.disabled = !enabled;
    });
    
    // Also enable/disable audio control buttons
    const audioButtons = document.querySelectorAll('.audio-control-btn');
    audioButtons.forEach(btn => {
      btn.disabled = !enabled;
    });
    
    // Enable/disable quick control buttons in top bar
    const quickButtons = document.querySelectorAll('#quick-camera, #quick-screen, #quick-location');
    quickButtons.forEach(btn => {
      btn.disabled = !enabled;
    });
  }

  // Update button state
  updateButtonState(type, active) {
    const button = document.getElementById(`btn-${type}`);
    if (button) {
      if (type === 'mic') {
        // Handle microphone button (integrated with camera)
        button.textContent = active ? '🔴 Live' : '🎤 Mic';
        button.className = `audio-control-btn text-xs font-semibold px-3 py-1 rounded transition border disabled:opacity-40 disabled:cursor-not-allowed ${
          active 
            ? 'bg-red-600 text-white border-red-700 hover:bg-red-700'
            : 'bg-blue-600 text-white border-blue-700 hover:bg-blue-700'
        }`;
      } else {
        // Handle other stream buttons
        button.textContent = active ? 'Stop' : 'Start';
        button.className = `stream-control-btn text-xs font-semibold px-3 py-1 rounded transition border disabled:opacity-40 disabled:cursor-not-allowed ${
          active 
            ? 'bg-red-600 text-white border-red-700 hover:bg-red-700'
            : 'bg-green-600 text-white border-green-700 hover:bg-green-700'
        }`;
      }
    }
  }

  // Show/hide screens
  showScreen(screenName) {
    const screens = ['auth', 'connect', 'dashboard'];
    screens.forEach(screen => {
      const element = document.getElementById(`${screen}-screen`);
      if (element) {
        element.classList.toggle('hidden', screen !== screenName);
      }
    });
    this.currentScreen = screenName;
  }

  // Show loading state
  showLoading(element, loading) {
    if (loading) {
      element.disabled = true;
      element.textContent = 'Connecting...';
    } else {
      element.disabled = false;
      element.textContent = 'CONNECT';
    }
  }

  // Show notification
  showNotification(message, type = 'info') {
    const notification = document.createElement('div');
    notification.className = `fixed top-4 right-4 z-50 p-4 rounded-lg shadow-lg text-white font-mono text-sm max-w-sm animate-slideInRight`;
    
    // Set background color based on type
    const bgColors = {
      info: 'bg-blue-600',
      success: 'bg-green-600',
      warning: 'bg-yellow-600',
      error: 'bg-red-600'
    };
    
    notification.className += ` ${bgColors[type] || bgColors.info}`;
    notification.textContent = message;
    
    document.body.appendChild(notification);
    
    // Auto remove after 3 seconds
    setTimeout(() => {
      notification.remove();
    }, 3000);
  }


  // Update connection status
  updateConnectionStatus(childId, connected) {
    const statusElement = document.getElementById('connection-status');
    const childIdElement = document.getElementById('child-id-display');
    
    if (statusElement) {
      statusElement.textContent = connected ? '🔴' : '⚫';
      statusElement.className = `text-lg ${connected ? 'text-red-500 animate-pulse' : 'text-gray-500'}`;
    }
    
    if (childIdElement) {
      childIdElement.textContent = childId || 'Not Connected';
    }
  }

  // Clear all streams
  clearAllStreams() {
    Object.keys(this.videoStreams).forEach(type => {
      const videoElement = document.getElementById(`video-${type}`);
      const placeholderElement = document.getElementById(`placeholder-${type}`);
      
      if (videoElement) {
        videoElement.srcObject = null;
        videoElement.classList.add('hidden');
      }
      
      if (placeholderElement) {
        placeholderElement.classList.remove('hidden');
      }
    });
    
    this.videoStreams = {};
  }
}

// Global functions for button handlers
window.toggleFullscreen = (type) => {
  const streamWindow = document.getElementById(`stream-${type}`);
  if (streamWindow) {
    streamWindow.classList.toggle('fullscreen');
  }
};

window.toggleStream = (type) => {
  const app = window.parentApp;
  if (app) {
    app.toggleStream(type);
  }
};

window.sendCommand = (command) => {
  const app = window.parentApp;
  if (app) {
    app.sendCommand(command);
  }
};

window.toggleLocationStreaming = () => {
  const app = window.parentApp;
  if (app && app.dashboard) {
    app.dashboard.toggleLocationStreaming();
  }
};

// --- Sidebar and Feature Window Logic ---

// Utility: Make an element draggable (header drag)
function makeDraggable(windowEl, headerEl) {
  let offsetX = 0, offsetY = 0, isDragging = false;
  headerEl.onmousedown = function(e) {
    // Fix the window size during drag to prevent resizing
    const rect = windowEl.getBoundingClientRect();
    windowEl.style.width = rect.width + 'px';
    windowEl.style.height = rect.height + 'px';
    isDragging = true;
    offsetX = e.clientX - windowEl.offsetLeft;
    offsetY = e.clientY - windowEl.offsetTop;
    document.onmousemove = function(e) {
      if (!isDragging) return;
      const sidebar = document.getElementById('sidebar');
      let minLeft = 0;
      if (sidebar && windowEl.offsetParent) {
        const sidebarRight = sidebar.getBoundingClientRect().right;
        const parentLeft = windowEl.offsetParent.getBoundingClientRect().left;
        minLeft = sidebarRight - parentLeft;
        if (minLeft < 0) minLeft = 0;
      }
      let newLeft = e.clientX - offsetX;
      if (newLeft < minLeft) newLeft = minLeft;
      // --- Top boundary logic ---
      const topBar = document.querySelector('.top-bar');
      let minTop = 0;
      if (topBar && windowEl.offsetParent) {
        const topBarRect = topBar.getBoundingClientRect();
        const parentTop = windowEl.offsetParent.getBoundingClientRect().top;
        minTop = topBarRect.bottom - parentTop;
        if (minTop < 0) minTop = 0;
      }
      let newTop = e.clientY - offsetY;
      if (newTop < minTop) newTop = minTop;
      windowEl.style.left = newLeft + 'px';
      windowEl.style.top = newTop + 'px';
    };
    document.onmouseup = function() {
      isDragging = false;
      document.onmousemove = null;
      document.onmouseup = null;
    };
  };
}

// Utility: Make an element resizable (native CSS resize is used, but you can add custom logic if needed)
// ---

// Make sidebar feature buttons draggable and order persistent
function enableSidebarDragAndDrop() {
  const sidebar = document.getElementById('sidebar');
  if (!sidebar) return;
  const btnSelector = '.sidebar-btn:not(.fixed-browser-btn)';
  let dragSrcEl = null;

  // Restore order from localStorage
  const savedOrder = JSON.parse(localStorage.getItem('sidebarBtnOrder') || '[]');
  if (savedOrder.length) {
    const btns = Array.from(sidebar.querySelectorAll(btnSelector));
    savedOrder.forEach(id => {
      const btn = btns.find(b => b.dataset.feature === id);
      if (btn) sidebar.appendChild(btn);
    });
  }

  sidebar.querySelectorAll(btnSelector).forEach(btn => {
    btn.setAttribute('draggable', 'true');
    btn.ondragstart = function(e) {
      dragSrcEl = this;
      e.dataTransfer.effectAllowed = 'move';
      e.dataTransfer.setData('text/plain', this.dataset.feature);
      this.classList.add('dragging');
    };
    btn.ondragend = function() {
      this.classList.remove('dragging');
    };
    btn.ondragover = function(e) {
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      this.classList.add('drag-over');
    };
    btn.ondragleave = function() {
      this.classList.remove('drag-over');
    };
    btn.ondrop = function(e) {
      e.preventDefault();
      this.classList.remove('drag-over');
      if (dragSrcEl && dragSrcEl !== this) {
        if (this.nextSibling === dragSrcEl) {
          sidebar.insertBefore(dragSrcEl, this);
        } else {
          sidebar.insertBefore(dragSrcEl, this.nextSibling);
        }
        // Save new order
        const order = Array.from(sidebar.querySelectorAll(btnSelector)).map(b => b.dataset.feature);
        localStorage.setItem('sidebarBtnOrder', JSON.stringify(order));
      }
    };
  });
}

// Browser dropdown functionality
function initBrowserDropdown() {
  const browserBtn = document.getElementById('browser-dropdown-btn');
  const dropdown = document.getElementById('browser-dropdown');
  
  if (!browserBtn || !dropdown) return;
  
  // Toggle dropdown on button click
  browserBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    dropdown.classList.toggle('show');
  });
  
  // Handle dropdown item clicks
  dropdown.addEventListener('click', (e) => {
    if (e.target.classList.contains('dropdown-item')) {
      e.preventDefault();
      e.stopPropagation();
      
      const url = e.target.dataset.url;
      if (url === 'custom') {
        // Open system's default browser with a simple page
        window.open('https://www.google.com', '_blank');
      } else {
        // Open specific URL in default browser
        window.open(url, '_blank');
      }
      
      // Close dropdown
      dropdown.classList.remove('show');
    }
  });
  
  // Close dropdown when clicking outside
  document.addEventListener('click', (e) => {
    if (!browserBtn.contains(e.target) && !dropdown.contains(e.target)) {
      dropdown.classList.remove('show');
    }
  });
}

// Profile dropdown functionality
function initProfileDropdown() {
  const profileBtn = document.getElementById('profile-icon-btn');
  const profileDropdown = document.getElementById('profile-dropdown');
  const profileEmail = document.getElementById('profile-email');
  const signOutBtn = document.getElementById('profile-signout-btn');
  const deleteBtn = document.getElementById('profile-delete-btn');
  
  if (!profileBtn || !profileDropdown) return;
  
  // Update profile email when auth state changes
  function updateProfileEmail() {
    if (window.authManager && window.authManager.currentUser) {
      profileEmail.textContent = window.authManager.currentUser.email;
    }
  }
  
  // Toggle dropdown on button click
  profileBtn.addEventListener('click', (e) => {
    e.preventDefault();
    e.stopPropagation();
    profileDropdown.classList.toggle('show');
    updateProfileEmail();
  });
  
  // Handle sign out
  if (signOutBtn) {
    signOutBtn.addEventListener('click', async (e) => {
      e.preventDefault();
      e.stopPropagation();
      
      try {
        if (window.authManager) {
          await window.authManager.signOut();
          // Close dropdown
          profileDropdown.classList.remove('show');
          // Redirect to auth screen like delete account does
          if (window.parentApp) {
            window.parentApp.showAuthScreen();
          }
        }
      } catch (error) {
        console.error('❌ Sign out failed:', error);
      }
    });
  }
  
  // Handle delete account
  if (deleteBtn) {
    deleteBtn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      profileDropdown.classList.remove('show');
      showDeleteConfirmation();
    });
  }
  
  // Close dropdown when clicking outside
  document.addEventListener('click', (e) => {
    if (!profileBtn.contains(e.target) && !profileDropdown.contains(e.target)) {
      profileDropdown.classList.remove('show');
    }
  });
  
  // Initial email update
  updateProfileEmail();
}

// Show delete account confirmation dialog
function showDeleteConfirmation() {
  // Create overlay
  const overlay = document.createElement('div');
  overlay.className = 'delete-confirmation-overlay';
  
  // Create dialog
  const dialog = document.createElement('div');
  dialog.className = 'delete-confirmation-dialog';
  dialog.innerHTML = `
    <div class="delete-confirmation-title">⚠️ Delete Account</div>
    <div class="delete-confirmation-message">
      Are you sure you want to permanently delete your account?<br>
      This action cannot be undone and all your data will be lost.
    </div>
    <div class="delete-confirmation-buttons">
      <button class="delete-confirmation-btn cancel">Cancel</button>
      <button class="delete-confirmation-btn delete">Delete</button>
    </div>
  `;
  
  overlay.appendChild(dialog);
  document.body.appendChild(overlay);
  
  // Handle button clicks
  const cancelBtn = dialog.querySelector('.cancel');
  const deleteBtn = dialog.querySelector('.delete');
  
  cancelBtn.addEventListener('click', () => {
    document.body.removeChild(overlay);
  });
  
  deleteBtn.addEventListener('click', async () => {
    try {
      if (window.authManager && window.authManager.currentUser) {
        // First, prompt for re-authentication
        const password = await promptForPassword();
        if (!password) {
          // User cancelled password prompt
          return;
        }
        
        // Re-authenticate the user
        const credential = firebase.auth.EmailAuthProvider.credential(
          window.authManager.currentUser.email,
          password
        );
        
        await window.authManager.currentUser.reauthenticateWithCredential(credential);
        
        // Now proceed with deletion
        // Delete user data from Firebase
        const userRef = window.firebaseDB.ref(`users/${window.authManager.currentUser.uid}`);
        await userRef.remove();
        
        // Delete the user account
        await window.authManager.currentUser.delete();
        
        // Show success message and redirect to auth screen
        if (window.parentApp && window.parentApp.ui) {
          window.parentApp.ui.showNotification('✅ Account deleted successfully', 'success');
        }
        
        // Remove overlay and redirect to auth
        document.body.removeChild(overlay);
        if (window.parentApp) {
          window.parentApp.showAuthScreen();
        }
      }
    } catch (error) {
      console.error('❌ Failed to delete account:', error);
      
      let errorMessage = 'Failed to delete account';
      if (error.code === 'auth/wrong-password') {
        errorMessage = 'Incorrect password. Please try again.';
      } else if (error.code === 'auth/requires-recent-login') {
        errorMessage = 'Authentication required. Please try again.';
      } else {
        errorMessage = error.message || 'Failed to delete account';
      }
      
      // Show error message
      if (window.parentApp && window.parentApp.ui) {
        window.parentApp.ui.showNotification('❌ ' + errorMessage, 'error');
      }
      
      // Don't remove overlay on error, let user try again
    }
  });
  
  // Close on overlay click
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) {
      document.body.removeChild(overlay);
    }
  });
  
  // Close on Escape key
  const handleEscape = (e) => {
    if (e.key === 'Escape') {
      document.body.removeChild(overlay);
      document.removeEventListener('keydown', handleEscape);
    }
  };
  document.addEventListener('keydown', handleEscape);
}

// Prompt for password function
function promptForPassword() {
  return new Promise((resolve) => {
    // Create password prompt overlay
    const overlay = document.createElement('div');
    overlay.className = 'delete-confirmation-overlay';
    
    const dialog = document.createElement('div');
    dialog.className = 'delete-confirmation-dialog';
    dialog.innerHTML = `
      <div class="delete-confirmation-title">🔐 Re-authentication Required</div>
      <div class="delete-confirmation-message">
        Please enter your password to confirm account deletion.
      </div>
      <div style="margin-bottom: 20px;">
        <input type="password" id="delete-password-input" 
               placeholder="Enter your password" 
               style="width: 100%; padding: 10px; background: #000; border: 1px solid #666; color: #fff; border-radius: 4px; font-family: 'Fira Mono', monospace;">
      </div>
      <div class="delete-confirmation-buttons">
        <button class="delete-confirmation-btn cancel" id="password-cancel">Cancel</button>
        <button class="delete-confirmation-btn delete" id="password-confirm">Confirm</button>
      </div>
    `;
    
    overlay.appendChild(dialog);
    document.body.appendChild(overlay);
    
    const passwordInput = dialog.querySelector('#delete-password-input');
    const cancelBtn = dialog.querySelector('#password-cancel');
    const confirmBtn = dialog.querySelector('#password-confirm');
    
    // Focus on password input
    setTimeout(() => passwordInput.focus(), 100);
    
    // Handle confirm button
    const handleConfirm = () => {
      const password = passwordInput.value.trim();
      if (password) {
        document.body.removeChild(overlay);
        resolve(password);
      } else {
        passwordInput.focus();
      }
    };
    
    // Handle cancel button
    const handleCancel = () => {
      document.body.removeChild(overlay);
      resolve(null);
    };
    
    // Handle Enter key
    const handleEnter = (e) => {
      if (e.key === 'Enter') {
        handleConfirm();
      }
    };
    
    // Handle Escape key
    const handleEscape = (e) => {
      if (e.key === 'Escape') {
        handleCancel();
      }
    };
    
    confirmBtn.addEventListener('click', handleConfirm);
    cancelBtn.addEventListener('click', handleCancel);
    passwordInput.addEventListener('keydown', handleEnter);
    document.addEventListener('keydown', handleEscape);
    
    // Close on overlay click
    overlay.addEventListener('click', (e) => {
      if (e.target === overlay) {
        handleCancel();
      }
    });
  });
}

// Sidebar interaction and window management
window.addEventListener('DOMContentLoaded', () => {
  // Initialize browser dropdown
  initBrowserDropdown();
  
  // Initialize profile dropdown after a short delay to ensure auth manager is ready
  setTimeout(() => {
    initProfileDropdown();
  }, 1000);
  
  const sidebar = document.getElementById('sidebar');
  const featureWindows = document.getElementById('feature-windows');
  const openWindows = {};

  // Sidebar toggle logic (logo only)
  const sidebarLogo = sidebar.querySelector('.sidebar-logo');
  let sidebarOpen = true;
  sidebarLogo.style.cursor = 'pointer';
  let originalLogoSrc = sidebarLogo.src;
  let toggleIcon = null;

  // Remove the sidebar-toggle-btn from DOM if present
  const sidebarToggleBtn = document.getElementById('sidebar-toggle-btn');
  if (sidebarToggleBtn) sidebarToggleBtn.remove();

  function collapseSidebar() {
    sidebarOpen = false;
    sidebar.style.width = '0';
    sidebar.classList.add('sidebar-collapsed');
    sidebar.querySelectorAll('.sidebar-btn').forEach(btn => btn.style.display = 'none');
    sidebarLogo.style.position = 'fixed';
    sidebarLogo.style.left = '0.5rem'; // Move icon closer to heading
    sidebarLogo.style.top = '12px'; // Align icon with heading
    sidebarLogo.style.zIndex = '1000';
    sidebarLogo.style.margin = '0';
  }
  function expandSidebar() {
    sidebarOpen = true;
    sidebar.style.width = '';
    sidebar.classList.remove('sidebar-collapsed');
    sidebar.querySelectorAll('.sidebar-btn').forEach(btn => btn.style.display = 'block');
    sidebarLogo.style.position = '';
    sidebarLogo.style.left = '';
    sidebarLogo.style.top = ''; // Reset icon position
    sidebarLogo.style.zIndex = '';
    sidebarLogo.style.margin = '1rem 0 1rem 1rem';
    if (toggleIcon) {
      sidebarLogo.parentNode.removeChild(toggleIcon);
      toggleIcon = null;
    }
    sidebarLogo.src = originalLogoSrc;
  }

  // Click logo to expand/collapse
  sidebarLogo.onclick = () => {
    if (sidebarOpen) {
      collapseSidebar();
    } else {
      expandSidebar();
    }
  };

  // Hover on logo in collapsed state: show ☰ icon exactly behind the logo
  sidebarLogo.onmouseenter = () => {
    if (!sidebarOpen) {
      if (!toggleIcon) {
        toggleIcon = document.createElement('span');
        toggleIcon.textContent = '☰';
        toggleIcon.style.position = 'fixed';
        toggleIcon.style.left = sidebarLogo.style.left;
        toggleIcon.style.top = sidebarLogo.style.top;
        const iconSize = sidebarLogo.offsetWidth ? `${sidebarLogo.offsetWidth}px` : '28px';
        toggleIcon.style.width = iconSize;
        toggleIcon.style.height = iconSize;
        toggleIcon.style.fontSize = '1.1em';
        toggleIcon.style.color = '#fbbf24';
        toggleIcon.style.pointerEvents = 'none';
        toggleIcon.style.zIndex = '999';
        toggleIcon.style.opacity = '0.7';
        toggleIcon.style.textAlign = 'center';
        toggleIcon.style.lineHeight = iconSize;
        sidebarLogo.parentNode.appendChild(toggleIcon);
      }
      sidebarLogo.style.opacity = '0'; // Make logo fully transparent
    }
  };
  sidebarLogo.onmouseleave = () => {
    if (toggleIcon) {
      toggleIcon.parentNode.removeChild(toggleIcon);
      toggleIcon = null;
    }
    sidebarLogo.style.opacity = '1'; // Restore logo
  };

  // Sidebar button click handler
  sidebar.querySelectorAll('.sidebar-btn').forEach(btn => {
    btn.onclick = () => {
      // Skip browser button - it has its own dropdown functionality
      if (btn.id === 'browser-dropdown-btn') {
        return;
      }
      
      const feature = btn.getAttribute('data-feature');
      if (!feature) {
        return; // Skip buttons without data-feature attribute
      }
      
      if (openWindows[feature]) {
        // Focus window if already open
        openWindows[feature].style.zIndex = 100 + Object.keys(openWindows).length;
        return;
      }
      // Create window
      const win = document.createElement('div');
      win.className = 'feature-window flicker-in';
      win.style.left = (260 + 40 * Object.keys(openWindows).length) + 'px';
      win.style.top = (80 + 40 * Object.keys(openWindows).length) + 'px';
      win.setAttribute('data-feature', feature);
      win.innerHTML = `
        <div class="feature-window-header">
          <span>${getFeatureTitle(feature)}</span>
          <button class="feature-window-close">×</button>
        </div>
        <div class="feature-window-content" id="feature-content-${feature}">
          <!-- Feature-specific content will be injected here -->
        </div>
      `;
      // Close button
      win.querySelector('.feature-window-close').onclick = () => {
        win.classList.remove('flicker-in');
        win.classList.add('flicker-out');
        setTimeout(() => {
          featureWindows.removeChild(win);
          delete openWindows[feature];
        }, 300);
      };
      // Draggable
      makeDraggable(win, win.querySelector('.feature-window-header'));
      // Add to DOM and track
      featureWindows.appendChild(win);
      openWindows[feature] = win;
      // Inject feature content
      injectFeatureContent(feature, win.querySelector('.feature-window-content'));
    };
  });

  // Sidebar resize logic
  const resizeHandle = document.getElementById('sidebar-resize-handle');
  let isResizing = false;
  resizeHandle.addEventListener('mousedown', function(e) {
    isResizing = true;
    document.body.style.cursor = 'ew-resize';
    document.onmousemove = function(e) {
      if (!isResizing) return;
      let newWidth = e.clientX;
      if (newWidth < 120) newWidth = 120;
      if (newWidth > 400) newWidth = 400;
      sidebar.style.width = newWidth + 'px';
    };
    document.onmouseup = function() {
      isResizing = false;
      document.body.style.cursor = '';
      document.onmousemove = null;
      document.onmouseup = null;
    };
  });
});

document.addEventListener('DOMContentLoaded', enableSidebarDragAndDrop);

// Helper: Get feature window title
function getFeatureTitle(feature) {
  switch (feature) {
    case 'camera': return 'Monitor Camera';
    case 'screen': return 'Monitor Screen';
    case 'location': return 'Monitor Location';
    case 'sms': return 'Monitor SMS';
    default: return feature;
  }
}

// Export for use in other modules
window.UIComponents = UIComponents;
