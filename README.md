# Acho Chat App

A native Android client for Acho, a self-hosted social network and chat platform.

## Overview

Acho Chat App provides Android access to the Acho platform. The application connects to the server dynamically by retrieving its URL from a remote configuration database, allowing server changes to propagate to all clients without requiring an application update.

## Features

### Core
- Renders the Acho platform via WebView with JavaScript and DOM Storage enabled
- **Dynamic server URL**: the server address is retrieved from a remote configuration database and automatically refreshed every 5 seconds in the event of a change. If an error occurs upon opening the application, we recommend closing it completely and waiting approximately 2 minutes before trying again. If the issue persists, please contact technical support at +526601670314
- In-app back navigation with full WebView history support and fullscreen mode
- Hardware-accelerated rendering for improved performance

### Native Notifications
- **In-app notifications**: the server may invoke `AchoApp.showNotification(title, body)` via the JavaScript bridge to display native Android notifications
- **Background service** (`MessageCheckService`): a foreground service that remains active even when the application is closed, polling the server every 30 seconds using the session token to detect and notify new messages
- Dedicated notification channels: standard (`acho_notifications`) and low-priority background (`acho_bg`)
- Automatic `POST_NOTIFICATIONS` permission prompt on Android 13 and above

### Home Screen Widget

> **Battery advisory:** The widget periodically fetches data from the server in order to stay up to date, which introduces a recurring background network operation. On devices with ample battery capacity, the impact is negligible. On devices with limited battery capacity, we recommend refraining from placing the widget on the home screen. Users are free to do so regardless — this notice is purely informational.

- Fully resizable widget, adjustable from a compact tile up to the size of a full application on the home screen
- Displays the most recent community posts, including author initials, message preview, timestamp, and a new posts counter badge
- Tapping the widget opens the application directly

### Share Sheet Integration
- The application is capable of **receiving shared content** from third-party apps, including text, images, video, audio, and PDF files
- Shared content is forwarded to the WebView via `window.receiveSharedContent(text, uri)` once the page has finished loading
- The server may invoke `AchoApp.shareContent(text)` or `AchoApp.shareUrl(url, title)` to trigger the native Android share sheet

### File Picker
- Full file upload support from within the WebView, including images, videos, audio, and documents

## Permissions

| Permission | Purpose |
|---|---|
| `INTERNET` | Connect to the Acho server |
| `POST_NOTIFICATIONS` | Display message notifications |
| `READ_MEDIA_IMAGES` | Upload images (Android 13+) |
| `READ_MEDIA_VIDEO` | Upload videos (Android 13+) |
| `READ_MEDIA_AUDIO` | Upload audio (Android 13+) |
| `READ_EXTERNAL_STORAGE` | Upload files (Android 12 and below) |
| `FOREGROUND_SERVICE` | Maintain the background notification service |
| `FOREGROUND_SERVICE_DATA_SYNC` | Foreground service classification |

## JavaScript Bridge (`AchoApp`)

The server may interact with native Android functionality via the `AchoApp` object injected into the WebView:

```javascript
// Initialize the background notification service
AchoApp.startBackgroundService(authToken);

// Display a native notification
AchoApp.showNotification("New message", "Hey!");

// Open the native share sheet with text
AchoApp.shareContent("Check this out");

// Open the native share sheet with a URL and title
AchoApp.shareUrl("https://example.com", "Title");
```

## Technical Details

- **Language**: Java
- **Minimum SDK**: Android 7.1 (API 25)
- **Target SDK**: Android 15 (API 35)
- **Universal APK**: a single release APK supporting both 32-bit and 64-bit architectures
- Signed release builds produced via GitHub Actions CI

## Privacy

The application does not collect or transmit user data independently. All network activity is directed exclusively to the Acho server. The session token is stored locally in `SharedPreferences` solely for the purpose of the background notification service.

## License

MIT — see `LICENSE` file.

