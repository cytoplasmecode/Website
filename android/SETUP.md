# Plant Watering App — Setup Guide

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- A device or emulator running Android 8.0 (API 26) or higher with Google Play Services

## 1. Google Cloud Platform Setup

### Create a project and enable the Calendar API

1. Go to [console.cloud.google.com](https://console.cloud.google.com/)
2. Create a new project (or select an existing one)
3. Navigate to **APIs & Services → Library**
4. Search for **Google Calendar API** and click **Enable**

### Create OAuth credentials for Android

1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. Select **Android** as the application type
4. Enter the package name: `com.cytoplasmecode.plantwatering`
5. Enter your SHA-1 signing certificate fingerprint (see below)
6. Click **Create**

### Get your debug SHA-1 fingerprint

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

Copy the `SHA1:` value from the output and paste it into the GCP credentials form.

## 2. Open and Build the Project

1. Open Android Studio
2. Click **File → Open** and select the `android/` folder from this repository
3. Let Gradle sync complete (it will download all dependencies automatically)
4. Run the app on your device or emulator

## 3. First Launch

1. Tap **Sign in with Google**
2. Select your Google account
3. Grant Calendar access when prompted
4. Tap **+** to add your first plant (name + watering interval in days)

## App Behaviour

| Action | What happens |
|---|---|
| Add plant "Basil" with 3-day interval | Creates a "Water Basil" all-day event in Google Calendar 3 days from now |
| Tap **Water Now 💧** | Renames the pending Calendar event to **"DONE - Basil"**, creates a new "Water Basil" event 3 days later |
| Delete a plant | Removes the pending Calendar event and the local record |
| Daily background check | Sends a notification for every plant that is due or overdue |
| Device reboot | WorkManager reminder is automatically rescheduled |

## Notes

- Calendar events are created in the signed-in user's **primary** Google Calendar.
- The app uses an offline Room database to track plants locally so the list loads instantly.
- Network calls (Calendar API) happen on background threads and silently retry on the next watering cycle if they fail.
