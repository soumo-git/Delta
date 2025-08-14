# 🚀 Project Delta Email Service

A Node.js email service for sending OTP verification emails, deployed on Render.

## 🎯 Features

- ✅ **OTP Email Sending** - Professional verification emails
- ✅ **Test Email Endpoint** - Verify your setup
- ✅ **Health Check** - Monitor service status
- ✅ **CORS Enabled** - Works with web apps
- ✅ **Environment Variables** - Secure configuration
- ✅ **Professional Templates** - Beautiful email design

## 🚀 Deploy to Render

### Step 1: Create GitHub Repository

1. Create a new GitHub repository
2. Push this `email-service` folder to your repository
3. Make sure the repository is public (Render free tier requirement)

### Step 2: Deploy on Render

1. Go to [Render.com](https://render.com/) and sign up
2. Click **"New +"** → **"Web Service"**
3. Connect your GitHub repository
4. Configure the service:

```
Name: project-delta-email-service
Environment: Node
Build Command: npm install
Start Command: npm start
```

### Step 3: Set Environment Variables

In Render dashboard, go to **"Environment"** tab and add:

```
GMAIL_USER=your-email@gmail.com
GMAIL_APP_PASSWORD=your-16-char-app-password
```

### Step 4: Deploy

Click **"Create Web Service"** and wait for deployment.

## 📧 Gmail Setup

### Step 1: Enable 2-Factor Authentication

1. Go to [Google Account settings](https://myaccount.google.com/)
2. Click **"Security"** → **"2-Step Verification"**
3. Enable 2-factor authentication

### Step 2: Generate App Password

1. Go to **"Security"** → **"App passwords"**
2. Select **"Mail"** and **"Other"**
3. Name it **"Project Delta Email Service"**
4. Copy the 16-character password

### Step 3: Update Environment Variables

In Render dashboard, update:
- `GMAIL_USER`: Your Gmail address
- `GMAIL_APP_PASSWORD`: The 16-character app password

## 🔗 API Endpoints

### Health Check
```
GET https://your-service.onrender.com/
```

### Send OTP Email
```
POST https://your-service.onrender.com/send-otp
Content-Type: application/json

{
  "email": "user@example.com",
  "otp": "123456"
}
```

### Send Password Reset Email
```
POST https://your-service.onrender.com/send-password-reset
Content-Type: application/json

{
  "email": "user@example.com",
  "actionUrl": "https://your-app-url.com" // Optional: URL to redirect after password reset
}
```

This endpoint uses the Firebase Admin SDK to generate a password reset link and sends it via the custom email service. It's particularly useful for Microsoft 365/Outlook email addresses that often block Firebase's default password reset emails.

### Test Email
```
POST https://your-service.onrender.com/test-email
Content-Type: application/json

{
  "email": "user@example.com"
}
```

## 🔧 Update Parent App

Once deployed, update `ParentElectronApp/js/auth-manager.js`:

```javascript
async sendOtpEmail(email, otp) {
  try {
    // Call Render email service
    const response = await fetch('https://your-service.onrender.com/send-otp', {
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
```

## 🧪 Testing

### Test Locally
```bash
cd email-service
npm install
npm run dev
```

### Test Deployed Service
```bash
curl -X POST https://your-service.onrender.com/test-email \
  -H "Content-Type: application/json" \
  -d '{"email": "your-email@gmail.com"}'
```

## 💰 Pricing

- ✅ **Free Tier**: 750 hours/month (enough for 24/7 operation)
- ✅ **No email limits**: Send unlimited emails
- ✅ **Custom domain**: Professional email addresses
- ✅ **Auto-scaling**: Handles traffic spikes

## 🔒 Security

- ✅ **Environment variables**: Credentials are secure
- ✅ **CORS protection**: Only authorized domains
- ✅ **Input validation**: Prevents abuse
- ✅ **Error handling**: Graceful failures

## 🚨 Troubleshooting

### Common Issues:

1. **"Invalid login"**: Check your Gmail App Password
2. **"Service not found"**: Verify the Render URL is correct
3. **"CORS error"**: Make sure CORS is enabled
4. **"Environment variables"**: Check Render dashboard settings
5. **"Microsoft 365/Outlook blocking emails"**: The service now automatically detects Microsoft email domains and uses a custom email handler to bypass filtering issues

### Microsoft 365/Outlook Email Issues:

Microsoft 365 and Outlook often block Firebase authentication emails. This service includes a custom solution that:

- Detects Microsoft email domains automatically
- Uses Firebase Admin SDK to generate password reset links
- Sends emails through our custom SMTP server instead of Firebase
- Uses professional email templates that are less likely to be filtered

### Logs:
Check Render dashboard → **"Logs"** tab for detailed error information.

## 📞 Support

If you encounter issues:
1. Check Render logs
2. Verify Gmail credentials
3. Test with the health check endpoint
4. Ensure environment variables are set correctly