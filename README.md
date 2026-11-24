# Elsayyad Gesture

Android app project for hand-gesture controls, branded in a black-and-gold style.

## Planned gesture mapping
- Thumb + index fingertip: scroll upward
- Thumb + little fingertip: scroll downward
- Thumb + middle fingertip: increase media volume
- Thumb + ring fingertip: decrease media volume

## Current foundation
- Native Android/Kotlin project scaffold
- Black-and-gold dashboard and Arabic labels
- Locally saved preferences for response delay, sensitivity, floating-button preference, and hand-lost behavior
- Accessibility service declaration and scroll/volume action helpers
- GitHub Actions workflow configured to build a debug APK and upload it as an artifact

## Important status
This is an initial project foundation, not a finished gesture-control app yet. Camera capture, MediaPipe hand landmark inference, background camera foreground-service lifecycle, floating overlay, and end-to-end gesture-to-action wiring still need implementation and device testing. Android requires explicit user-granted camera and Accessibility permissions; camera use while other apps are in front is subject to Android version and device restrictions.

## Build
Run `gradle assembleDebug` with JDK 17 and Gradle 8.9. The APK output is `app/build/outputs/apk/debug/app-debug.apk`.
