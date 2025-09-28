# Pinry Share

A collection of tools for sharing images to Pinry:
- **Android App**: Save images from any app via Android's share system
- **Firefox Extension**: Right-click images to save them to Pinry with alt/title text extraction
- **WordPress Plugin**: Display Pinry boards on your WordPress site

## Android App Features

- **Secure Storage**: Uses Android's EncryptedSharedPreferences to securely store your API token and board ID
- **Share Integration**: Appears in the share sheet of Chrome, Firefox, Photos, and other image-sharing apps
- **Smart Descriptions**: Automatically extracts domain names from image URLs for better organization
- **Simple Setup**: Just enter your Pinry URL, API token, and default board ID
- **Background Upload**: Images are uploaded in the background without keeping the app open

## Firefox Extension Features

- **Right-click Integration**: Right-click any image to save it to Pinry
- **Smart Descriptions**: Extracts alt text, title attributes, and page context automatically
- **Rich Metadata**: Combines image metadata with source domain for better descriptions
- **Toast Notifications**: Visual feedback when images are saved successfully

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

### Android App
1. **From Chrome/Firefox**: When viewing an image, tap the share button and select "Pinry Saver"
2. **From Photos**: Select an image, tap share, and choose "Pinry Saver"
3. **From any app**: Share any image and select "Pinry Saver" from the share sheet

The image will be uploaded to your configured Pinry board automatically with the domain name as the description.

### Firefox Extension
1. **Install the extension** from the Firefox Add-ons store (when published)
2. **Configure your settings**: Right-click the extension icon to set your Pinry URL, API token, and default board
3. **Save images**: Right-click any image on any website and select "Save to Pinry"

The image will be saved with rich metadata including alt text, title attributes, and source domain.

### WordPress Plugin
1. **Upload the plugin** to your WordPress site's plugins directory
2. **Activate the plugin** in your WordPress admin panel
3. **Configure your Pinry settings** in the plugin options
4. **Display boards** using shortcodes or widgets

## Building

### Android App
This is a standard Android project. Open it in Android Studio and build as usual.

**Requirements:**
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9.10+
- OkHttp 4.12.0+
- AndroidX Security Crypto 1.1.0+

### Firefox Extension
1. Navigate to the `firefox-plugin/` directory
2. Load the extension in Firefox Developer Edition:
   - Open `about:debugging`
   - Click "This Firefox"
   - Click "Load Temporary Add-on"
   - Select the `manifest.json` file

### WordPress Plugin
1. Navigate to the `wordpress-plugin/` directory
2. Upload the entire directory to your WordPress site's `/wp-content/plugins/` folder
3. Activate the plugin in your WordPress admin panel

## Security

- **Android App**: API tokens are encrypted using Android's EncryptedSharedPreferences
- **Firefox Extension**: Settings are stored in Firefox's secure storage
- **WordPress Plugin**: Settings are stored in WordPress database
- No sensitive data is stored in plain text
- Network requests use HTTPS
- Sensitive data is excluded from backups

## Troubleshooting

### Android App
- **"Please configure Pinry settings first"**: Make sure you've entered all required settings in the main app
- **"Upload failed"**: Check your API token and Pinry URL are correct
- **App doesn't appear in share sheet**: Make sure you've granted the necessary permissions
- **Bad request errors**: The app now properly handles special characters in descriptions

### Firefox Extension
- **Extension not working**: Make sure you've configured your Pinry URL and API token
- **Images not saving**: Check that your API token has the correct permissions
- **Missing descriptions**: The extension extracts alt text and titles from img tags

## Project Structure

```
pinry-share/
├── app/                          # Android app source code
│   ├── src/main/java/           # Kotlin source files
│   └── src/main/res/            # Android resources
├── firefox-plugin/              # Firefox extension (ignored by git)
├── wordpress-plugin/            # WordPress plugin (ignored by git)
├── .gitignore                   # Git ignore rules
└── README.md                    # This file
```
