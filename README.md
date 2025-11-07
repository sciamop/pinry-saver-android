# Pinry Saver

A beautiful Android app for browsing and saving images to your Pinry instance.

<p align="center">
  <img src="https://www.whistlehog.com/images/pinry/pinry_gallery.png" width="250" alt="Gallery View">
  <img src="https://www.whistlehog.com/images/pinry/pinry_image.png" width="250" alt="Fullscreen View">
  <img src="https://www.whistlehog.com/images/pinry/pinry_settings.png" width="250" alt="Settings">
</p>

## Features

### Gallery & Browsing
- **Masonry Grid Layout**: Browse your pins in a responsive masonry layout (2 columns portrait, 3 landscape)
- **Fullscreen Viewer**: Tap any pin to view it fullscreen with smooth transitions
- **Pinch to Zoom**: Zoom up to 5x on any image with pan support
- **Smart Gestures**: 
  - Swipe left/right (outer 33%) to navigate between pins
  - Swipe down (middle 33%) to dismiss fullscreen view
  - Pinch anywhere to zoom (locks paging during zoom)
- **Tag Display**: View ML-generated tags on any pin (tap tag icon in fullscreen)
- **Pull to Refresh**: Swipe down on gallery to refresh your pins
- **Infinite Scroll**: Automatically loads more pins as you scroll
- **Smart Prefetching**: Preloads next page during slow scrolling for smooth browsing

### Sharing & Upload
- **Share Extension**: Save images from any app via the Android share sheet
- **Live Progress UI**: Beautiful modal dialog shows upload progress with thumbnail preview
- **AI-Powered Tagging**: Automatically tags images using Google ML Kit image classification
- **Smart Upload**: Handles both image files and URLs intelligently
- **Secure Storage**: API tokens encrypted using Android's EncryptedSharedPreferences
- **Settings Validation**: Validates Pinry server connection before saving settings


## Setup

1. **Get your Pinry API Token**:
   - Requires Pinry (github.com/pinry)
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

### Browsing Your Pins
1. Open the app to view your Pinry gallery
2. Tap the Pinry logo (upper left) to scroll to top
3. Tap the gear icon (upper right) to access settings
4. Tap any pin to view fullscreen
5. In fullscreen:
   - Pinch to zoom (up to 5x)
   - Double-tap to reset zoom
   - Swipe left/right on edges to navigate
   - Swipe down in center to dismiss
   - Tap tag icon to view AI-generated tags

### Saving Images
1. **From Chrome/Firefox**: When viewing an image, tap share → "Pinry Saver"
2. **From Photos**: Select an image, tap share → "Pinry Saver"
3. **From any app**: Share any image → "Pinry Saver"

The app will show a progress dialog with:
- Thumbnail preview of the image
- Upload status (analyzing, uploading, creating pin)
- AI-generated tags (automatically applied to the pin)
- Success/error messages

## Building

This is a standard Android project. Open it in Android Studio and build as usual.

### Requirements
- Android SDK 24+ (Android 7.0)
- Kotlin 1.9.10+
- OkHttp 4.12.0+
- AndroidX Security Crypto 1.1.0+
- Google ML Kit Vision 17.0.2+
- Coil Image Loading 2.5.0+

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Release build (with ProGuard optimization)
./gradlew assembleRelease
```

The release build includes:
- ProGuard optimization and obfuscation
- Automatic log stripping
- Resource shrinking
- Optimized APK size

## Security

- API tokens are encrypted using Android's EncryptedSharedPreferences
- No sensitive data is stored in plain text
- Network requests use HTTPS
- Sensitive data is excluded from backups

## Troubleshooting

- **"Please configure Pinry settings first"**: Make sure you've entered your Pinry URL and API token in settings
- **"Upload failed"**: Verify your API token and Pinry URL are correct
- **Gallery won't load**: Check your Pinry URL is accessible and properly formatted
- **Images not loading**: Ensure your device has internet connectivity
- **Tags not showing**: Only pins with ML-generated tags will show the tag icon in magenta
- **Zoom feels sticky**: Wait 200ms after pinching before swiping to navigate

## Technical Details

### Architecture
- **MVVM Pattern**: ViewModel manages gallery state and data loading
- **Repository Pattern**: Centralized data access through PinryRepository
- **Coroutines**: Async operations using Kotlin coroutines
- **LiveData**: Reactive UI updates
- **RecyclerView**: Efficient masonry grid with StaggeredGridLayoutManager
- **ViewPager2**: Smooth fullscreen image swiping
- **Custom Views**: ZoomableImageView for pinch-to-zoom functionality

### Image Loading
- **Coil**: Modern image loading with memory/disk caching
- **Progressive Loading**: Shows thumbnails immediately, loads full-size in background
- **Smart Caching**: Prevents re-downloading on configuration changes
- **Optimized Placeholders**: Rotating Pinry logo with delayed visibility

### ML Features
- **Google ML Kit**: On-device image labeling (no cloud processing)
- **Confidence Threshold**: 0.6 minimum confidence for tag suggestions
- **Tag Limit**: Top 6 most confident tags per image
- **Privacy-First**: All ML processing happens on-device
