# Pinry Saver

An Android app that allows you to quickly save images to your Pinry instance by sharing them from other apps.

## Features

- **Secure Storage**: Uses Android's EncryptedSharedPreferences to securely store your API token and board ID
- **Share Integration**: Appears in the share sheet of Safari, Firefox, Photos, and other image-sharing apps
- **Simple Setup**: Just enter your Pinry URL, API token, and default board ID
- **Background Upload**: Images are uploaded in the background without keeping the app open

## Setup

1. **Get your Pinry API Token**:
   - Log into your Pinry instance
   - Go to Settings → API Tokens
   - Create a new token

2. **Get your Board ID**:
   - Navigate to the board where you want to save images
   - The board ID is in the URL: `https://your-pinry.com/board/BOARD_ID/`

3. **Configure the App**:
   - Open Pinry Saver
   - Enter your Pinry URL (e.g., `https://your-pinry.com`)
   - Enter your API token
   - Enter your default board ID
   - Tap "Save Settings"

## Usage

1. **From Safari/Firefox**: When viewing an image, tap the share button and select "Pinry Saver"
2. **From Photos**: Select an image, tap share, and choose "Pinry Saver"
3. **From any app**: Share any image and select "Pinry Saver" from the share sheet

The image will be uploaded to your configured Pinry board automatically.

## Building

This is a standard Android project. Open it in Android Studio and build as usual.

### Requirements
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9.10+
- OkHttp 4.12.0+
- AndroidX Security Crypto 1.1.0+

## Security

- API tokens are encrypted using Android's EncryptedSharedPreferences
- No data is stored in plain text
- Network requests use HTTPS
- Sensitive data is excluded from backups

## Troubleshooting

- **"Please configure Pinry settings first"**: Make sure you've entered all required settings in the main app
- **"Upload failed"**: Check your API token and Pinry URL are correct
- **App doesn't appear in share sheet**: Make sure you've granted the necessary permissions
