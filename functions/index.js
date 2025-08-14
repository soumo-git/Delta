const functions = require('firebase-functions');
const nodemailer = require('nodemailer');

// Create a transporter using Gmail
// You'll need to replace these with your actual Gmail credentials
const transporter = nodemailer.createTransporter({
  service: 'gmail',
  auth: {
    user: 'your-email@gmail.com', // Replace with your Gmail address
    pass: 'your-app-password'     // Replace with your Gmail App Password
  }
});

// Function to send OTP email
exports.sendOtpEmail = functions.https.onCall(async (data, context) => {
  try {
    const { email, otp } = data;
    
    // Validate input
    if (!email || !otp) {
      throw new functions.https.HttpsError('invalid-argument', 'Email and OTP are required');
    }
    
    // Email template
    const mailOptions = {
      from: 'your-email@gmail.com', // Replace with your Gmail address
      to: email,
      subject: '🔐 Your Project Delta Verification Code',
      html: `
        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; background-color: #000; color: #fff;">
          <div style="text-align: center; margin-bottom: 30px;">
            <h1 style="color: #22c55e; margin: 0; font-size: 24px;">Project Delta</h1>
            <p style="color: #666; margin: 10px 0;">Verification Code</p>
          </div>
          
          <div style="background-color: #111; border: 2px solid #22c55e; border-radius: 10px; padding: 30px; text-align: center; margin: 20px 0;">
            <h2 style="color: #22c55e; margin: 0 0 20px 0; font-size: 18px;">Your Verification Code</h2>
            <div style="background-color: #000; border: 1px solid #22c55e; border-radius: 8px; padding: 20px; margin: 20px 0;">
              <span style="font-size: 32px; font-weight: bold; color: #22c55e; letter-spacing: 8px; font-family: 'Courier New', monospace;">${otp}</span>
            </div>
            <p style="color: #fbbf24; margin: 10px 0; font-size: 14px;">⚠️ This code will expire in 5 minutes</p>
          </div>
          
          <div style="margin-top: 30px; padding: 20px; background-color: #111; border-radius: 8px;">
            <p style="color: #666; margin: 0; font-size: 14px; line-height: 1.5;">
              If you didn't request this verification code, please ignore this email. 
              This code is for your Project Delta account verification.
            </p>
          </div>
          
          <div style="text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #333;">
            <p style="color: #666; margin: 0; font-size: 12px;">
              Project Delta Team<br>
              Parental Control Dashboard
            </p>
          </div>
        </div>
      `,
      text: `
Project Delta - Verification Code

Your verification code is: ${otp}

This code will expire in 5 minutes.

If you didn't request this code, please ignore this email.

Best regards,
Project Delta Team
      `
    };
    
    // Send email
    const info = await transporter.sendMail(mailOptions);
    
    console.log('Email sent successfully:', info.messageId);
    
    return {
      success: true,
      messageId: info.messageId
    };
    
  } catch (error) {
    console.error('Error sending email:', error);
    throw new functions.https.HttpsError('internal', 'Failed to send email', error);
  }
});

// Test function to verify setup
exports.testEmail = functions.https.onCall(async (data, context) => {
  try {
    const { email } = data;
    
    const mailOptions = {
      from: 'your-email@gmail.com', // Replace with your Gmail address
      to: email,
      subject: '🧪 Project Delta - Email Test',
      text: 'This is a test email from Project Delta Firebase Functions. If you receive this, your email setup is working correctly!'
    };
    
    const info = await transporter.sendMail(mailOptions);
    
    return {
      success: true,
      messageId: info.messageId,
      message: 'Test email sent successfully!'
    };
    
  } catch (error) {
    console.error('Error sending test email:', error);
    throw new functions.https.HttpsError('internal', 'Failed to send test email', error);
  }
});
