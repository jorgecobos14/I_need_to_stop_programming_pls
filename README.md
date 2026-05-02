# Acho Chat App

Acho Chat App is an open-source Android client that provides access to the Acho chat platform through a lightweight WebView-based interface.

## Overview

This application is a minimal Android client designed to offer a direct and efficient way to access the Acho chat web platform.

It prioritizes simplicity, performance, and transparency, avoiding unnecessary features or background processing.

## Core functionality

- Loads the Acho chat platform using Android WebView
- Provides in-app back navigation
- Enables JavaScript and DOM storage for web compatibility
- Uses a lightweight native Android interface
- Supports sharing photos, videos, and files with the community and chat

## Privacy

This application does not collect, store, or transmit personal data on its own.

All chat content, network requests, and account interactions occur directly through the remote Acho web service loaded inside the WebView.

## Permissions

- `INTERNET` — required to load the web platform
- `READ_EXTERNAL_STORAGE` — allows the user to select files from their device to share in the community or chat (Android 7.1.2 - 12)
- `WRITE_EXTERNAL_STORAGE` — allows saving files downloaded from the platform (Android 7.1.2 - 9)
- `READ_MEDIA_IMAGES` — allows selecting images from the device to share (Android 13+)
- `READ_MEDIA_VIDEO` — allows selecting videos from the device to share (Android 13+)
- `READ_MEDIA_AUDIO` — allows selecting audio files from the device to share (Android 13+)

Uploaded files are managed by the platform administrator. Any file may be removed if it violates the usage policies, provided the administrator gives a justification and the content creator confirms the removal.

## Technical details

- Native Android application
- Written in Java
- Uses Android WebView
- Built with Android Gradle tooling
- Designed to be simple, transparent, and easy to maintain

## Build

The project can be built with standard Android development tools or through a continuous integration system such as GitHub Actions.

## Source code

This repository contains the full source code for the application.

## License

See the `LICENSE` file for licensing information.

