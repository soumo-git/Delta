# 🔐 Firebase Functions Email Setup Guide

## Overview
This guide will help you set up Gmail to send OTP emails through Firebase Functions using Nodemailer.

## Step 1: Enable 2-Factor Authentication on Gmail

1. Go to your [Google Account settings](https://myaccount.google.com/)
2. Click on **"Security"** in the left sidebar
3. Under **"Signing in to Google"**, click on **"2-Step Verification"**
4. Follow the steps to enable 2-factor authentication

## Step 2: Generate App Password

1. Go to your [Google Account settings](https://myaccount.google.com/)
2. Click on **"Security"** in the left sidebar
3. Under **"Signing in to Google"**, click on **"App passwords"**
4. Select **"Mail"** as the app and **"Other"** as the device
5. Enter a name like **"Project Delta Firebase Functions"**
6. Click **"Generate"**
7. **Copy the 16-character password** (it will look like: `abcd efgh ijkl mnop`)

## Step 3: Update Firebase Functions Configuration

1. Open `functions/index.js`
2. Replace the placeholder credentials:

```javascript
const transporter = nodemailer.createTransporter({
  service: 'gmail',
  auth: {
    user: 'your-actual-email@gmail.com',    // Replace with your Gmail
    pass: 'your-16-char-app-password'       // Replace with the app password
  }
});
```

3. Also update the `from` field in both functions:

```javascript
from: 'your-actual-email@gmail.com', // Replace with your Gmail
```

## Step 4: Deploy Firebase Functions

1. Open terminal in the root directory (`E:\DeltaApp`)
2. Run the deployment command:

```bash
firebase deploy --only functions
```

## Step 5: Update Parent App Configuration

1. Open `ParentElectronApp/js/auth-manager.js`
2. Replace the EmailJS implementation with Firebase Functions:

```javascript
// Remove EmailJS initialization
// Remove sendViaEmailJS method

// Update sendOtpEmail method:
async sendOtpEmail(email, otp) {
  try {
    // Call Firebase Function
    const sendOtpFunction = firebase.functions().httpsCallable('sendOtpEmail');
    const result = await sendOtpFunction({ email, otp });
    
    console.log('Email sent successfully:', result.data);
    
    // Store email data for verification
    const emailDataRef = this.db.ref(`email_verification/${email.replace(/[.#$[\]]/g, '_')}`);
    await emailDataRef.set({
      email: email,
      otp: otp,
      sentAt: Date.now(),
      status: 'sent'
    });
    
  } catch (error) {
    console.error('Error sending email:', error);
    throw new Error('Failed to send email');
  }
}
```

## Step 6: Test the Setup

1. Deploy the functions: `firebase deploy --only functions`
2. Test with the test function in Firebase Console
3. Try signing up in your Parent app

## Troubleshooting

### Common Issues:

1. **"Invalid login" error**: Make sure you're using the App Password, not your regular Gmail password
2. **"Less secure app access" error**: You must use App Passwords, not regular passwords
3. **Function not found**: Make sure you deployed the functions correctly
4. **Permission denied**: Check that your Firebase project has Functions enabled

### Security Notes:

- ✅ **App Passwords are secure** - They can only send emails, not access your account
- ✅ **Functions are serverless** - No server maintenance required
- ✅ **Completely free** - No email limits with Firebase Functions
- ✅ **Production ready** - Used by thousands of apps

## Alternative Email Providers

If you prefer not to use Gmail, you can use:

- **Outlook/Hotmail**: Change `service: 'gmail'` to `service: 'outlook'`
- **Yahoo**: Change `service: 'gmail'` to `service: 'yahoo'`
- **Custom SMTP**: Use your own SMTP server configuration

## Support

If you encounter any issues:
1. Check the Firebase Functions logs in the Firebase Console
2. Verify your Gmail App Password is correct
3. Ensure 2-factor authentication is enabled
4. Check that the function is deployed successfully 