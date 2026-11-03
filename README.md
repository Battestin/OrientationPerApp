# Orientation per App

Android app that lets you choose which apps always open in landscape (or portrait),
regardless of the system auto-rotate setting. Built for the Samsung Galaxy Tab S10 FE,
but it should work on other Android 11+ devices.

## How it works

An accessibility service detects which app is in the foreground and, if that app is
configured, adds an invisible overlay window that requests the chosen screen orientation.
The service only reads the foreground app's package name. It does not read screen content
and the app has no internet permission.

## Install

1. Download `app-debug.apk` from the latest [Release](../../releases).
2. Allow installing from unknown sources and install it.
3. Open the app, then enable **Orientation per App** in
   Settings > Accessibility > Installed apps.
   If it is greyed out: App info > ⋮ > Allow restricted settings.
4. Tap an app in the list and choose *Always landscape*, *Always portrait* or *Automatic*.

## Limitations

- Behavior may vary across Android/One UI versions and some apps.
- Aggressive battery optimization can stop the service: set the app to "Unrestricted".

## Build from source

Push to GitHub and run the **Build APK** workflow (Actions tab), or open the project
in Android Studio and run `gradle assembleDebug`.
