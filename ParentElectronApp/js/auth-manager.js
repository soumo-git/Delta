// Authentication Manager
console.log("✅ auth-manager.js loaded");

class AuthManager {
  constructor() {
    this.auth = window.firebaseAuth;
    this.db = window.firebaseDB;
    this.currentUser = null;
    this.userSubscription = null;
    this.authStateListener = null;
    this.otpData = {
      email: null,
      otp: null,
      expiresAt: null,
      attempts: 0
    };
    this.init();
  }

  init() {
    console.log('🔐 Initializing Auth Manager...');
    
    // Initialize Email Service
    this.initEmailService();
    
    // Set up auth state listener
    this.authStateListener = this.auth.onAuthStateChanged((user) => {
      this.handleAuthStateChange(user);
    });
    
    // Initialize particles background for auth screen
    this.initAuthParticles();
    // Set up form event listeners
    this.initAuthForms();
  }

  initEmailService() {
    try {
      // For development, use local email service
      // For production, use the Render deployment
      const isDevelopment = window.location.hostname === 'localhost' || 
                           window.location.hostname === '127.0.0.1';
      
      this.emailServiceUrl = isDevelopment 
        ? 'http://localhost:3000' 
        : 'YOUR_EMAIL_SERVICE_URL';
      
      console.log(`✅ Email service initialized: ${this.emailServiceUrl}`);
    } catch (error) {
      console.error('❌ Email service initialization failed:', error);
    }
  }

  initAuthParticles() {
    const canvas = document.getElementById('auth-particles-canvas');
    if (canvas && window.ParticlesBackground) {
      new window.ParticlesBackground(canvas);
    }
  }

  initAuthForms() {
    // Switch to sign up
    const showSignupBtn = document.getElementById('show-signup-btn');
    if (showSignupBtn) {
      showSignupBtn.addEventListener('click', () => {
        this.showSignUp();
      });
    }
    // Switch to sign in
    const showSigninBtn = document.getElementById('show-signin-btn');
    if (showSigninBtn) {
      showSigninBtn.addEventListener('click', () => {
        this.showSignIn();
      });
    }
    // Sign in form
    const signinForm = document.getElementById('email-signin-form');
    if (signinForm) {
      signinForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('signin-email').value.trim();
        const password = document.getElementById('signin-password').value;
        this.emailSignIn(email, password);
      });
    }
    
    // Forgot password button
    const forgotPasswordBtn = document.getElementById('forgot-password-btn');
    if (forgotPasswordBtn) {
      forgotPasswordBtn.addEventListener('click', () => {
        this.showForgotPasswordDialog();
      });
    }
    
    // Forgot password form
    const forgotPasswordForm = document.getElementById('forgot-password-form');
    if (forgotPasswordForm) {
      forgotPasswordForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const email = document.getElementById('forgot-password-email').value.trim();
        this.sendPasswordResetEmail(email);
      });
    }
    
    // Forgot password cancel button
    const forgotPasswordCancelBtn = document.getElementById('forgot-password-cancel-btn');
    if (forgotPasswordCancelBtn) {
      forgotPasswordCancelBtn.addEventListener('click', () => {
        this.hideForgotPasswordDialog();
      });
    }
    
    // Set up email verification
    this.setupEmailVerification();
    
    // Set up password strength indicator
    this.setupPasswordStrength();
    
    // Sign up form
    const signupForm = document.getElementById('email-signup-form');
    if (signupForm) {
      signupForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const name = document.getElementById('signup-name').value.trim();
        const email = document.getElementById('signup-email').value.trim();
        const password = document.getElementById('signup-password').value;
        const confirm = document.getElementById('signup-confirm').value;
        
        // Validate email and OTP
        if (!this.isEmailVerified) {
          this.showSignUpError('Please verify your email address first.');
          return;
        }
        
        if (!this.isOtpVerified) {
          this.showSignUpError('Please verify your email with OTP first.');
          return;
        }
        
        // Validate password strength
        const passwordStrength = this.checkPasswordStrength(password);
        if (passwordStrength.score < 2) {
          this.showSignUpError('Password is too weak. Please choose a stronger password.');
          return;
        }
        
        if (password !== confirm) {
          this.showSignUpError('Passwords do not match.');
          return;
        }
        
        try {
          const userCredential = await this.auth.createUserWithEmailAndPassword(email, password);
          const user = userCredential.user;
          // Save name to user profile
          if (user) {
            await user.updateProfile({ displayName: name });
            await user.reload();
            this.currentUser = this.auth.currentUser;
            console.log('✅ User profile updated with name:', name);
          }
          // The user data will be loaded by handleAuthStateChange
        } catch (err) {
          this.showSignUpError(err.message);
        }
      });
    }
  }

  async handleAuthStateChange(user) {
    console.log('👤 Auth state changed:', user ? 'User signed in' : 'User signed out');
    
    if (user) {
      this.currentUser = user;
      await this.loadUserData(user);
      this.showUserInfo();
    } else {
      this.currentUser = null;
      this.userSubscription = null;
      this.showSignIn();
    }
  }

  async loadUserData(user) {
    try {
      console.log('📊 Loading user data for:', user.email);
      
      // Check if user exists in our database
      const userRef = this.db.ref(`users/${user.uid}`);
      const snapshot = await userRef.once('value');
      
      if (snapshot.exists()) {
        const userData = snapshot.val();
        this.userData = userData;
        this.userSubscription = userData.subscription || userData;
        console.log('✅ User data loaded:', this.userData);
      } else {
        // Create new user record
        await this.createNewUser(user);
      }
    } catch (error) {
      console.error('❌ Error loading user data:', error);
    }
  }

  async createNewUser(user) {
    try {
      console.log('🆕 Creating new user record for:', user.email);
      console.log('🆕 User displayName:', user.displayName);
      
      // Ensure we have the latest user data
      await user.reload();
      const updatedUser = this.auth.currentUser;
      
      const userData = {
        email: updatedUser.email,
        name: updatedUser.displayName || '',
        displayName: updatedUser.displayName || '',
        photoURL: updatedUser.photoURL || '',
        createdAt: Date.now(),
        subscription: {
          status: 'trial',
          plan: 'trial',
          trialEnds: Date.now() + (7 * 24 * 60 * 60 * 1000), // 7 days trial
          createdAt: Date.now()
        }
      };
      
      await this.db.ref(`users/${user.uid}`).set(userData);
      this.userData = userData;
      this.userSubscription = userData.subscription;
      
      console.log('✅ New user created with trial subscription');
    } catch (error) {
      console.error('❌ Error creating new user:', error);
    }
  }

  async emailSignIn(email, password) {
    this.showSignInError('');
    try {
      await this.auth.signInWithEmailAndPassword(email, password);
      // Success will be handled by auth state change
    } catch (error) {
      console.error('❌ Email sign-in failed:', error);
      this.showSignInError(error.message || 'Sign-in failed.');
    }
  }

  async emailSignUp(email, password) {
    this.showSignUpError('');
    try {
      await this.auth.createUserWithEmailAndPassword(email, password);
      // Success will be handled by auth state change
    } catch (error) {
      console.error('❌ Email sign-up failed:', error);
      this.showSignUpError(error.message || 'Sign-up failed.');
    }
  }

  showLoading() {
    document.getElementById('auth-loading').classList.remove('hidden');
    document.getElementById('auth-signin').classList.add('hidden');
    document.getElementById('auth-signup').classList.add('hidden');
    document.getElementById('auth-user-info').classList.add('hidden');
    this.showSignInError('');
    this.showSignUpError('');
  }

  showSignIn() {
    document.getElementById('auth-loading').classList.add('hidden');
    document.getElementById('auth-signin').classList.remove('hidden');
    document.getElementById('auth-signup').classList.add('hidden');
    document.getElementById('auth-user-info').classList.add('hidden');
    this.showSignInError('');
    this.showSignUpError('');
  }

  showSignUp() {
    document.getElementById('auth-loading').classList.add('hidden');
    document.getElementById('auth-signin').classList.add('hidden');
    document.getElementById('auth-signup').classList.remove('hidden');
    document.getElementById('auth-user-info').classList.add('hidden');
    this.showSignInError('');
    this.showSignUpError('');
  }

  showUserInfo() {
    document.getElementById('auth-loading').classList.add('hidden');
    document.getElementById('auth-signin').classList.add('hidden');
    document.getElementById('auth-signup').classList.add('hidden');
    document.getElementById('auth-user-info').classList.remove('hidden');
    this.showSignInError('');
    this.showSignUpError('');
    this.updateUserInfoDisplay();
  }

  showSignInError(message) {
    let errorDiv = document.getElementById('auth-signin-error');
    if (errorDiv) {
      errorDiv.textContent = message || '';
      errorDiv.style.display = message ? 'block' : 'none';
    }
  }

  showSignUpError(message) {
    let errorDiv = document.getElementById('auth-signup-error');
    if (errorDiv) {
      errorDiv.textContent = message || '';
      errorDiv.style.display = message ? 'block' : 'none';
    }
  }
  
  showForgotPasswordDialog() {
    const dialog = document.getElementById('forgot-password-dialog');
    if (dialog) {
      // Clear previous messages
      this.showForgotPasswordError('');
      this.showForgotPasswordSuccess('');
      
      // Clear email field
      const emailInput = document.getElementById('forgot-password-email');
      if (emailInput) {
        // Try to pre-fill with the email from sign-in form if available
        const signinEmail = document.getElementById('signin-email');
        if (signinEmail && signinEmail.value.trim()) {
          emailInput.value = signinEmail.value.trim();
        } else {
          emailInput.value = '';
        }
      }
      
      // Show dialog
      dialog.classList.remove('hidden');
    }
  }
  
  hideForgotPasswordDialog() {
    const dialog = document.getElementById('forgot-password-dialog');
    if (dialog) {
      dialog.classList.add('hidden');
    }
  }
  
  showForgotPasswordError(message) {
    let errorDiv = document.getElementById('forgot-password-error');
    if (errorDiv) {
      errorDiv.textContent = message || '';
      errorDiv.style.display = message ? 'block' : 'none';
    }
  }
  
  showForgotPasswordSuccess(message) {
    let successDiv = document.getElementById('forgot-password-success');
    if (successDiv) {
      successDiv.textContent = message || '';
      successDiv.style.display = message ? 'block' : 'none';
    }
  }
  
  async sendPasswordResetEmail(email) {
    if (!email) {
      this.showForgotPasswordError('Please enter your email address.');
      return;
    }
    
    // Clear previous messages
    this.showForgotPasswordError('');
    this.showForgotPasswordSuccess('');
    
    try {
      // Check if the email domain is Outlook/Microsoft 365
      const isOutlookEmail = this.isOutlookOrMicrosoftEmail(email);
      
      if (isOutlookEmail) {
        // Use custom email service for Outlook/Microsoft emails
        await this.sendCustomPasswordResetEmail(email);
      } else {
        // Use default Firebase method for other email providers
        await this.auth.sendPasswordResetEmail(email);
      }
      
      this.showForgotPasswordSuccess('If an account exists with this email, a password reset link will be sent. Please check your inbox.');
      
      // Auto-close dialog after 5 seconds
      setTimeout(() => {
        this.hideForgotPasswordDialog();
      }, 5000);
    } catch (error) {
      console.error('❌ Password reset failed:', error);
      // Don't expose specific error messages that could reveal if an email exists
      this.showForgotPasswordError('Failed to send password reset email. Please try again later.');
    }
  }
  
  isOutlookOrMicrosoftEmail(email) {
    if (!email) return false;
    
    const domain = email.split('@')[1]?.toLowerCase();
    if (!domain) return false;
    
    // Check for Outlook, Hotmail, Live, MSN, or Microsoft domains
    return domain.includes('outlook') || 
           domain.includes('hotmail') || 
           domain.includes('live') || 
           domain.includes('msn') || 
           domain.includes('microsoft');
  }
  
  async sendCustomPasswordResetEmail(email) {
    try {
      // Since we can't generate a password reset link directly from the client SDK,
      // we'll send the email to our custom email service which will handle the reset process
      
      console.log(`🔗 Sending password reset request to: ${this.emailServiceUrl}/send-password-reset`);
      
      // Send the email via our custom email service
      const response = await fetch(`${this.emailServiceUrl}/send-password-reset`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ 
          email,
          // We'll pass the current URL as the action URL for the reset process
          actionUrl: window.location.href
        })
      });
      
      const result = await response.json();
      
      if (!result.success) {
        console.error('❌ Server returned error:', result.error, result.details || '');
        throw new Error(result.error || 'Failed to send password reset email');
      }
      
      console.log('✅ Custom password reset email sent successfully');
      if (result.messageId) console.log('📧 Message ID:', result.messageId);
      if (result.resetLink) console.log('🔗 Reset link (dev only):', result.resetLink);
      
      return result;
    } catch (error) {
      console.error('❌ Custom password reset failed:', error);
      // Re-throw the error so the calling function can handle it
      throw error;
    }
  }

  updateUserInfoDisplay() {
    if (!this.currentUser) return;
    
    // Update user info
    const avatar = document.getElementById('user-avatar');
    const name = document.getElementById('user-name');
    const email = document.getElementById('user-email');
    const status = document.getElementById('subscription-status');
    const plan = document.getElementById('subscription-plan');
    
    if (avatar) avatar.src = this.currentUser.photoURL || 'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iNjQiIGhlaWdodD0iNjQiIHZpZXdCb3g9IjAgMCA2NCA2NCIgZmlsbD0ibm9uZSIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj4KPGNpcmNsZSBjeD0iMzIiIGN5PSIzMiIgcj0iMzIiIGZpbGw9IiM2QjcyODAiLz4KPHN2ZyB4PSIxNiIgeT0iMTYiIHdpZHRoPSIzMiIgaGVpZ2h0PSIzMiIgdmlld0JveD0iMCAwIDI0IDI0IiBmaWxsPSJ3aGl0ZSI+CjxwYXRoIGQ9Ik0xMiAxMmMyLjIxIDAgNC0xLjc5IDQtNHMtMS43OS00LTQtNC00IDEuNzktNCA0IDEuNzkgNCA0IDR6bTAgMmMtMi42NyAwLTggMS4zNC04IDR2MmgxNnYtMmMwLTIuNjYtNS4zMy00LTgtNHoiLz4KPC9zdmc+Cjwvc3ZnPgo=';
    if (name) {
      // Try to get name from displayName first, then from database, then fallback to email prefix
      let displayName = this.currentUser.displayName;
      if (!displayName && this.userData && this.userData.name) {
        displayName = this.userData.name;
      }
      if (!displayName && this.currentUser.email) {
        // Extract name from email (everything before @)
        displayName = this.currentUser.email.split('@')[0];
        // Capitalize first letter
        displayName = displayName.charAt(0).toUpperCase() + displayName.slice(1);
      }
      name.textContent = 'Observer - ' + (displayName || 'User');
    }
    if (email) email.textContent = this.currentUser.email;
    
    // Update subscription info
    if (this.userSubscription) {
      const isActive = this.isSubscriptionActive();
      const statusText = isActive ? 'Active' : 'Expired';
      const statusColor = isActive ? 'text-green-400' : 'text-red-400';
      
      if (status) {
        status.textContent = statusText;
        status.className = `${statusColor} text-sm font-semibold`;
      }
      
      if (plan && this.userSubscription.plan) {
        plan.textContent = this.userSubscription.plan.charAt(0).toUpperCase() + this.userSubscription.plan.slice(1);
      } else if (plan) {
        plan.textContent = '';
      }
    } else {
      if (status) status.textContent = '';
      if (plan) plan.textContent = '';
    }
  }

  isSubscriptionActive() {
    if (!this.userSubscription) return false;
    
    if (this.userSubscription.status === 'trial') {
      return Date.now() < this.userSubscription.trialEnds;
    }
    
    if (this.userSubscription.status === 'active') {
      return true;
    }
    
    return false;
  }

  getCurrentUser() {
    return this.currentUser;
  }

  getUserSubscription() {
    return this.userSubscription;
  }

  isAuthenticated() {
    return !!this.currentUser;
  }

  canAccessApp() {
    return this.isAuthenticated() && this.isSubscriptionActive();
  }

  // Method to upgrade subscription (for future use)
  async upgradeSubscription(plan) {
    try {
      console.log('⬆️ Upgrading subscription to:', plan);
      
      const userRef = this.db.ref(`users/${this.currentUser.uid}/subscription`);
      await userRef.update({
        status: 'active',
        plan: plan,
        upgradedAt: Date.now()
      });
      
      // Reload user data
      await this.loadUserData(this.currentUser);
      this.updateUserInfoDisplay();
      
      console.log('✅ Subscription upgraded successfully');
    } catch (error) {
      console.error('❌ Error upgrading subscription:', error);
      throw error;
    }
  }

  // Add signOut method for app.js compatibility
  async signOut() {
    try {
      await this.auth.signOut();
    } catch (error) {
      console.error('❌ Sign-out failed:', error);
      throw error;
    }
  }

  setupEmailVerification() {
    this.isEmailVerified = false;
    this.isOtpVerified = false;
    const emailInput = document.getElementById('signup-email');
    const verificationStatus = document.getElementById('email-verification-status');
    const verificationIcon = document.getElementById('email-verification-icon');
    const verificationText = document.getElementById('email-verification-text');
    const otpSection = document.getElementById('otp-verification-section');
    
    let verificationTimeout;
    
    emailInput.addEventListener('input', (e) => {
      const email = e.target.value.trim();
      
      // Clear previous timeout
      if (verificationTimeout) {
        clearTimeout(verificationTimeout);
      }
      
      // Hide verification status if email is empty
      if (!email) {
        verificationStatus.classList.add('hidden');
        otpSection.classList.add('hidden');
        emailInput.classList.remove('email-verifying', 'email-verified', 'email-invalid');
        this.isEmailVerified = false;
        this.isOtpVerified = false;
        return;
      }
      
      // Show verifying status
      verificationStatus.classList.remove('hidden');
      verificationIcon.className = 'w-4 h-4 rounded-full bg-yellow-500 animate-pulse';
      verificationText.textContent = 'Verifying email...';
      verificationText.className = 'text-yellow-400';
      emailInput.classList.add('email-verifying');
      emailInput.classList.remove('email-verified', 'email-invalid');
      
      // Debounce verification
      verificationTimeout = setTimeout(async () => {
        try {
          const isValid = await this.verifyEmail(email);
          if (isValid) {
            verificationIcon.className = 'w-4 h-4 rounded-full bg-green-500';
            verificationText.textContent = 'Email verified ✓ - Send OTP';
            verificationText.className = 'text-green-400';
            emailInput.classList.remove('email-verifying');
            emailInput.classList.add('email-verified');
            this.isEmailVerified = true;
            
            // Show OTP section
            this.showOtpSection(email);
          } else {
            verificationIcon.className = 'w-4 h-4 rounded-full bg-red-500';
            verificationText.textContent = 'Invalid email address';
            verificationText.className = 'text-red-400';
            emailInput.classList.remove('email-verifying');
            emailInput.classList.add('email-invalid');
            this.isEmailVerified = false;
            this.isOtpVerified = false;
            otpSection.classList.add('hidden');
          }
        } catch (error) {
          verificationIcon.className = 'w-4 h-4 rounded-full bg-red-500';
          verificationText.textContent = 'Verification failed';
          verificationText.className = 'text-red-400';
          emailInput.classList.remove('email-verifying');
          emailInput.classList.add('email-invalid');
          this.isEmailVerified = false;
          this.isOtpVerified = false;
          otpSection.classList.add('hidden');
        }
      }, 1000);
    });
  }

  async verifyEmail(email) {
    // Basic email format validation
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
      return false;
    }
    
    // Check for disposable email domains
    const disposableDomains = [
      '10minutemail.com', 'tempmail.org', 'guerrillamail.com', 'mailinator.com',
      'yopmail.com', 'throwaway.email', 'temp-mail.org', 'fakeinbox.com',
      'temp-mail.io', 'sharklasers.com', 'guerrillamailblock.com', 'pokemail.net',
      'spam4.me', 'bccto.me', 'chacuo.net', 'dispostable.com', 'mailnesia.com',
      'mintemail.com', 'yopmail.net', 'getairmail.com', 'maildrop.cc'
    ];
    
    const domain = email.split('@')[1];
    if (disposableDomains.includes(domain)) {
      return false;
    }
    
    // Check if domain has valid MX records (real email server)
    try {
      const isValidDomain = await this.checkDomainMX(domain);
      if (!isValidDomain) {
        return false;
      }
      
      // Additional check: verify email format and common patterns
      const [localPart, domainPart] = email.split('@');
      
      // Check local part (before @)
      if (localPart.length < 1 || localPart.length > 64) {
        return false;
      }
      
      // Check for invalid characters in local part
      const localPartRegex = /^[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+$/;
      if (!localPartRegex.test(localPart)) {
        return false;
      }
      
      // Check domain part
      if (domainPart.length < 3 || domainPart.length > 253) {
        return false;
      }
      
      // Check for valid TLD (top-level domain)
      const validTLDs = [
        'com', 'org', 'net', 'edu', 'gov', 'mil', 'int', 'io', 'co', 'me', 'tv',
        'info', 'biz', 'name', 'pro', 'museum', 'coop', 'aero', 'jobs', 'mobi',
        'travel', 'cat', 'asia', 'post', 'tel', 'xxx', 'arpa', 'root', 'local',
        'test', 'invalid', 'example', 'localhost', 'onion', 'bit', 'coin', 'libre',
        'wikipedia', 'wikimedia', 'mozilla', 'google', 'amazon', 'microsoft',
        'apple', 'facebook', 'twitter', 'instagram', 'linkedin', 'github',
        'stackoverflow', 'reddit', 'youtube', 'twitch', 'discord', 'slack',
        'zoom', 'teams', 'skype', 'whatsapp', 'telegram', 'signal', 'protonmail',
        'tutanota', 'gmail', 'yahoo', 'hotmail', 'outlook', 'icloud', 'aol',
        'live', 'msn', 'ymail', 'rocketmail', 'fastmail', 'zoho', 'mail',
        'gmx', 'web', 't-online', 'freenet', 'arcor', 'tiscali', 'virgilio',
        'libero', 'alice', 'tiscali', 'virgilio', 'libero', 'alice', 'tiscali',
        'virgilio', 'libero', 'alice', 'tiscali', 'virgilio', 'libero', 'alice'
      ];
      
      const tld = domainPart.split('.').pop().toLowerCase();
      if (!validTLDs.includes(tld)) {
        return false;
      }
      
      return true;
    } catch (error) {
      console.error('Email verification error:', error);
      return false;
    }
  }

  async checkDomainMX(domain) {
    try {
      // Use a public DNS API to check MX records
      const response = await fetch(`https://dns.google/resolve?name=${domain}&type=MX`);
      const data = await response.json();
      
      // Check if MX records exist
      if (data.Answer && data.Answer.length > 0) {
        return true;
      }
      
      // If no MX records, check for A records (some domains use A records for email)
      const aResponse = await fetch(`https://dns.google/resolve?name=${domain}&type=A`);
      const aData = await aResponse.json();
      
      return aData.Answer && aData.Answer.length > 0;
    } catch (error) {
      console.error('DNS check error:', error);
      // Fallback: assume valid if we can't check DNS
      return true;
    }
  }

  showOtpSection(email) {
    const otpSection = document.getElementById('otp-verification-section');
    const otpEmailDisplay = document.getElementById('otp-email-display');
    
    otpSection.classList.remove('hidden');
    otpEmailDisplay.textContent = email;
    
    // Set up OTP input handling
    this.setupOtpInputs();
    
    // Initialize otpData with email
    this.otpData.email = email;
    
    // Send OTP automatically
    this.sendOtp(email);
  }

  setupOtpInputs() {
    const otpInputs = [
      document.getElementById('otp-input-1'),
      document.getElementById('otp-input-2'),
      document.getElementById('otp-input-3'),
      document.getElementById('otp-input-4'),
      document.getElementById('otp-input-5'),
      document.getElementById('otp-input-6')
    ];

    // Handle input navigation
    otpInputs.forEach((input, index) => {
      input.addEventListener('input', (e) => {
        const value = e.target.value;
        
        // Only allow numbers
        if (!/^\d*$/.test(value)) {
          e.target.value = '';
          return;
        }
        
        if (value) {
          input.classList.add('otp-input-filled');
          input.classList.remove('otp-input-error');
          
          // Move to next input
          if (index < otpInputs.length - 1) {
            otpInputs[index + 1].focus();
          }
        } else {
          input.classList.remove('otp-input-filled', 'otp-input-error');
        }
        
        // Check if all inputs are filled
        this.checkOtpComplete();
      });

      input.addEventListener('keydown', (e) => {
        if (e.key === 'Backspace' && !e.target.value && index > 0) {
          otpInputs[index - 1].focus();
        }
      });

      input.addEventListener('paste', (e) => {
        e.preventDefault();
        const pastedData = e.clipboardData.getData('text');
        const numbers = pastedData.replace(/\D/g, '').slice(0, 6);
        
        numbers.split('').forEach((num, i) => {
          if (otpInputs[i]) {
            otpInputs[i].value = num;
            otpInputs[i].classList.add('otp-input-filled');
            otpInputs[i].classList.remove('otp-input-error');
          }
        });
        
        this.checkOtpComplete();
      });
    });

    // Set up verify and resend buttons
    const verifyBtn = document.getElementById('verify-otp-btn');
    const resendBtn = document.getElementById('resend-otp-btn');
    
    verifyBtn.addEventListener('click', () => this.verifyOtp());
    resendBtn.addEventListener('click', () => this.resendOtp());
  }

  checkOtpComplete() {
    const otpInputs = [
      document.getElementById('otp-input-1'),
      document.getElementById('otp-input-2'),
      document.getElementById('otp-input-3'),
      document.getElementById('otp-input-4'),
      document.getElementById('otp-input-5'),
      document.getElementById('otp-input-6')
    ];
    
    const isComplete = otpInputs.every(input => input.value.length === 1);
    const verifyBtn = document.getElementById('verify-otp-btn');
    
    if (isComplete) {
      verifyBtn.disabled = false;
      verifyBtn.classList.remove('opacity-50', 'cursor-not-allowed');
    } else {
      verifyBtn.disabled = true;
      verifyBtn.classList.add('opacity-50', 'cursor-not-allowed');
    }
  }

  async sendOtp(email) {
    try {
      console.log('📧 Sending OTP to email:', email);
      
      if (!email) {
        throw new Error('Email parameter is required');
      }
      
      const expiresAt = Date.now() + (5 * 60 * 1000); // 5 minutes
      
      // Send request to email service to generate and send OTP
      console.log('🌐 Sending request to:', `${this.emailServiceUrl}/send-otp`);
      console.log('📤 Request body:', { email });
      
      const response = await fetch(`${this.emailServiceUrl}/send-otp`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email })
      });
      
      const result = await response.json();
      
      if (!result.success) {
        throw new Error(result.error);
      }
      
      // Store OTP data for verification (OTP will be retrieved from Firebase)
      this.otpData = {
        email: this.otpData.email || email, // Use existing email or fallback
        otp: null, // Will be retrieved from Firebase during verification
        expiresAt: expiresAt,
        attempts: 0
      };
      
      // Email service will handle Firebase storage
      console.log('📧 Email service will handle Firebase storage');
      
      // Start timer
      this.startOtpTimer();
      
      // Show success message
      this.showOtpMessage('OTP sent successfully! Check your email.', 'success');
      
    } catch (error) {
      console.error('Error sending OTP:', error);
      this.showOtpMessage('Failed to send OTP. Please try again.', 'error');
    }
  }

  async sendOtpEmail(email, otp) {
    try {
      // Call Render email service
      const response = await fetch(`${this.emailServiceUrl}/send-otp`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ email, otp })
      });
      
      const result = await response.json();
      
      if (!result.success) {
        throw new Error(result.error);
      }
      
      console.log('✅ Email sent successfully:', result.messageId);
      
      // Store email data for verification
      const emailDataRef = this.db.ref(`email_verification/${email.replace(/[.#$[\]]/g, '_')}`);
      await emailDataRef.set({
        email: email,
        otp: otp,
        sentAt: Date.now(),
        status: 'sent'
      });
      
    } catch (error) {
      console.error('❌ Error sending email:', error);
      throw new Error('Failed to send email');
    }
  }



  // Example implementation for Firebase Functions
  /*
  async sendViaFirebaseFunctions(email, otp) {
    const response = await fetch('https://your-firebase-function-url.com/send-otp', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        email: email,
        otp: otp
      })
    });
    
    if (!response.ok) {
      throw new Error('Failed to send email');
    }
    
    return response.json();
  }
  */

  async verifyOtp() {
    const otpInputs = [
      document.getElementById('otp-input-1'),
      document.getElementById('otp-input-2'),
      document.getElementById('otp-input-3'),
      document.getElementById('otp-input-4'),
      document.getElementById('otp-input-5'),
      document.getElementById('otp-input-6')
    ];
    
    const enteredOtp = otpInputs.map(input => input.value).join('');
    
    if (enteredOtp.length !== 6) {
      this.showOtpMessage('Please enter the complete 6-digit OTP.', 'error');
      return;
    }
    
    try {
      // Retrieve OTP from Firebase with retry mechanism
      const emailHash = this.otpData.email.replace(/[.#$[\]]/g, '_');
      const otpRef = this.db.ref(`otp/${emailHash}`);
      
      let otpData = null;
      let retryCount = 0;
      const maxRetries = 3;
      
      // Debug: Check entire otp node
      try {
        console.log('🌐 Parent App Firebase URL:', this.db.app.options.databaseURL);
        const allOtpsSnapshot = await this.db.ref('otp').once('value');
        const allOtps = allOtpsSnapshot.val();
        console.log('🔍 All OTPs in Firebase:', JSON.stringify(allOtps, null, 2));
      } catch (error) {
        console.error('❌ Error reading all OTPs:', error);
      }
      
      while (retryCount < maxRetries) {
        try {
          const snapshot = await otpRef.once('value');
          otpData = snapshot.val();
        } catch (firebaseError) {
          console.error('❌ Firebase read error:', firebaseError);
          otpData = null;
        }
        
        console.log(`🔍 Checking OTP for email: ${this.otpData.email} (attempt ${retryCount + 1})`);
        console.log('🔍 Email hash:', emailHash);
        console.log('🔍 Firebase path:', `otp/${emailHash}`);
        console.log('🔍 Firebase data:', JSON.stringify(otpData, null, 2));
        console.log('🔍 Has OTP field:', otpData && otpData.otp ? 'YES' : 'NO');
        console.log('🔍 OTP value:', otpData && otpData.otp ? otpData.otp : 'NOT FOUND');
        console.log('🔍 Data type:', typeof otpData);
        console.log('🔍 Data keys:', otpData ? Object.keys(otpData) : 'null');
        
        if (otpData && otpData.otp) {
          console.log('✅ OTP found in Firebase:', otpData.otp);
          break;
        }
        
        if (retryCount < maxRetries - 1) {
          console.log('⏳ OTP not found, retrying in 1 second...');
          await new Promise(resolve => setTimeout(resolve, 1000));
        }
        
        retryCount++;
      }
      
      if (!otpData || !otpData.otp) {
        console.log('❌ OTP not found in Firebase after all retries');
        this.showOtpMessage('OTP not found. Please request a new one.', 'error');
        return;
      }
      
      // Check if OTP is expired
      if (Date.now() > otpData.expiresAt) {
        this.showOtpMessage('OTP has expired. Please request a new one.', 'error');
        return;
      }
      
      // Check attempts
      if (otpData.attempts >= 3) {
        this.showOtpMessage('Too many attempts. Please request a new OTP.', 'error');
        return;
      }
      
      // Verify OTP
      if (enteredOtp === otpData.otp) {
        this.isOtpVerified = true;
        this.showOtpMessage('Email verified successfully! ✓', 'success');
        
        // Update verification text
        const verificationText = document.getElementById('email-verification-text');
        verificationText.textContent = 'Email & OTP verified ✓';
        verificationText.className = 'text-green-400';
        
        // Add success animation
        const otpSection = document.getElementById('otp-verification-section');
        otpSection.classList.add('otp-verification-success');
        
        // Store email before clearing OTP data
        const emailToRemove = this.otpData.email;
        
        // Clear OTP data
        this.otpData = { email: null, otp: null, expiresAt: null, attempts: 0 };
        
        // Remove OTP from Firebase
        if (emailToRemove) {
          const otpRef = this.db.ref(`otp/${emailToRemove.replace(/[.#$[\]]/g, '_')}`);
          await otpRef.remove();
        }
        
      } else {
        // Update attempts in Firebase
        await otpRef.update({ attempts: otpData.attempts + 1 });
        
        // Show error animation
        otpInputs.forEach(input => {
          input.classList.add('otp-input-error');
          setTimeout(() => {
            input.classList.remove('otp-input-error');
          }, 300);
        });
        
        this.showOtpMessage(`Invalid OTP. ${3 - (otpData.attempts + 1)} attempts remaining.`, 'error');
        
        // Clear inputs
        otpInputs.forEach(input => {
          input.value = '';
          input.classList.remove('otp-input-filled');
        });
        otpInputs[0].focus();
      }
      
    } catch (error) {
      console.error('Error verifying OTP:', error);
      this.showOtpMessage('Error verifying OTP. Please try again.', 'error');
    }
  }

  async resendOtp() {
    const resendBtn = document.getElementById('resend-otp-btn');
    resendBtn.disabled = true;
    resendBtn.textContent = 'Sending...';
    
    try {
      // Check if email exists
      if (!this.otpData.email) {
        console.error('❌ No email found in otpData:', this.otpData);
        throw new Error('No email available for resend');
      }
      
      console.log('🔄 Resending OTP to:', this.otpData.email);
      await this.sendOtp(this.otpData.email);
      
      // Clear previous inputs
      const otpInputs = [
        document.getElementById('otp-input-1'),
        document.getElementById('otp-input-2'),
        document.getElementById('otp-input-3'),
        document.getElementById('otp-input-4'),
        document.getElementById('otp-input-5'),
        document.getElementById('otp-input-6')
      ];
      
      otpInputs.forEach(input => {
        input.value = '';
        input.classList.remove('otp-input-filled', 'otp-input-error');
      });
      
      otpInputs[0].focus();
      
    } catch (error) {
      console.error('Error resending OTP:', error);
      this.showOtpMessage('Failed to resend OTP. Please try again.', 'error');
    } finally {
      resendBtn.disabled = false;
      resendBtn.textContent = 'Resend OTP';
    }
  }

  startOtpTimer() {
    const timerElement = document.getElementById('otp-timer');
    const resendBtn = document.getElementById('resend-otp-btn');
    
    const updateTimer = () => {
      const now = Date.now();
      const timeLeft = this.otpData.expiresAt - now;
      
      if (timeLeft <= 0) {
        timerElement.textContent = 'OTP expired';
        resendBtn.disabled = false;
        resendBtn.textContent = 'Resend OTP';
        return;
      }
      
      const minutes = Math.floor(timeLeft / 60000);
      const seconds = Math.floor((timeLeft % 60000) / 1000);
      
      timerElement.textContent = `Expires in: ${minutes}:${seconds.toString().padStart(2, '0')}`;
      resendBtn.disabled = true;
      resendBtn.textContent = 'Resend OTP';
      
      setTimeout(updateTimer, 1000);
    };
    
    updateTimer();
  }

  showOtpMessage(message, type) {
    const errorElement = document.getElementById('otp-error');
    errorElement.textContent = message;
    errorElement.className = `mt-3 p-2 rounded text-xs text-center ${
      type === 'error' 
        ? 'bg-red-900/20 border border-red-500/30 text-red-400' 
        : 'bg-green-900/20 border border-green-500/30 text-green-400'
    }`;
    errorElement.classList.remove('hidden');
    
    // Auto-hide success messages
    if (type === 'success') {
      setTimeout(() => {
        errorElement.classList.add('hidden');
      }, 3000);
    }
  }

  setupPasswordStrength() {
    const passwordInput = document.getElementById('signup-password');
    const strengthContainer = document.getElementById('password-strength-container');
    const strengthBars = [
      document.getElementById('strength-bar-1'),
      document.getElementById('strength-bar-2'),
      document.getElementById('strength-bar-3'),
      document.getElementById('strength-bar-4')
    ];
    const strengthText = document.getElementById('strength-text');
    
    passwordInput.addEventListener('input', (e) => {
      const password = e.target.value;
      
      if (!password) {
        strengthContainer.classList.add('hidden');
        return;
      }
      
      strengthContainer.classList.remove('hidden');
      const strength = this.checkPasswordStrength(password);
      this.updatePasswordStrengthDisplay(strength, strengthBars, strengthText);
    });
  }

  checkPasswordStrength(password) {
    const requirements = {
      length: password.length >= 8,
      uppercase: /[A-Z]/.test(password),
      lowercase: /[a-z]/.test(password),
      number: /\d/.test(password),
      special: /[!@#$%^&*(),.?":{}|<>]/.test(password)
    };
    
    const metRequirements = Object.values(requirements).filter(Boolean).length;
    let score = 0;
    let strength = 'weak';
    
    if (metRequirements >= 5) {
      score = 4;
      strength = 'strong';
    } else if (metRequirements >= 4) {
      score = 3;
      strength = 'good';
    } else if (metRequirements >= 3) {
      score = 2;
      strength = 'fair';
    } else {
      score = 1;
      strength = 'weak';
    }
    
    return { score, strength, requirements };
  }

  updatePasswordStrengthDisplay(strength, strengthBars, strengthText) {
    // Update strength bars with staggered animation
    strengthBars.forEach((bar, index) => {
      bar.className = 'w-2 h-2 rounded-full transition-all duration-300';
      
      setTimeout(() => {
        if (index < strength.score) {
          bar.classList.add(`strength-${strength.strength}`, 'strength-bar-active');
          
          // Add wave animation for active bars
          setTimeout(() => {
            bar.classList.add('strength-bar-wave');
          }, 300);
        } else {
          bar.classList.add('bg-gray-600');
        }
      }, index * 100);
    });
    
    // Update strength text with animation
    const strengthLabels = {
      weak: 'Weak',
      fair: 'Fair',
      good: 'Good',
      strong: 'Strong'
    };
    
    strengthText.style.opacity = '0';
    strengthText.style.transform = 'translateY(-10px)';
    
    setTimeout(() => {
      strengthText.textContent = strengthLabels[strength.strength];
      strengthText.className = `text-xs text-${strength.strength === 'weak' ? 'red' : strength.strength === 'fair' ? 'yellow' : 'green'}-400 transition-all duration-300`;
      strengthText.style.opacity = '1';
      strengthText.style.transform = 'translateY(0)';
    }, strengthBars.length * 100 + 200);
    
    // Update requirement indicators with check animation
    Object.entries(strength.requirements).forEach(([req, met], index) => {
      const reqElement = document.getElementById(`req-${req}`);
      if (reqElement) {
        const indicator = reqElement.querySelector('span:first-child');
        if (indicator) {
          setTimeout(() => {
            const wasMet = indicator.classList.contains('requirement-met');
            indicator.className = `w-2 h-2 rounded-full transition-all duration-300 ${met ? 'requirement-met' : 'bg-gray-600'}`;
            
            // Add check animation if requirement was just met
            if (met && !wasMet) {
              indicator.classList.add('requirement-checked');
              setTimeout(() => {
                indicator.classList.remove('requirement-checked');
              }, 500);
            }
          }, index * 150);
        }
      }
    });
  }

  // Cleanup method
  cleanup() {
    if (this.authStateListener) {
      this.authStateListener();
    }
  }
}

// Initialize auth manager when script loads
document.addEventListener('DOMContentLoaded', () => {
  window.authManager = new AuthManager();
});

// Export for use in other modules
window.AuthManager = AuthManager;