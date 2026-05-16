# Setup Guide

Step-by-step instructions for configuring Google Cloud and running the app for the first time.

---

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **Java 17** (bundled with Android Studio)
- A device or emulator running **Android 8.0 (API 26)** or higher with Google Play Services
- A Google account

---

## 1. Create a Google Cloud project

1. Go to [console.cloud.google.com](https://console.cloud.google.com/)
2. Click the project dropdown → **New Project**
3. Name it (e.g. "Plant Watering") and click **Create**

---

## 2. Enable the Google Calendar API

1. In your new project, go to **APIs & Services → Library**
2. Search for **Google Calendar API**
3. Click it and then click **Enable**

---

## 3. Create an OAuth consent screen

1. Go to **APIs & Services → OAuth consent screen**
2. Choose **External** and click **Create**
3. Fill in the required fields:
   - App name: `Plant Watering`
   - User support email: your email
   - Developer contact: your email
4. On the **Scopes** step, click **Add or remove scopes** and add:
   ```
   https://www.googleapis.com/auth/calendar
   ```
5. On the **Test users** step, add the Google accounts that will use the app during development
6. Click **Save and Continue** through to the summary

---

## 4. Create an Android OAuth 2.0 credential

1. Go to **APIs & Services → Credentials**
2. Click **Create Credentials → OAuth client ID**
3. Select **Android** as the application type
4. Enter:
   - **Package name**: `com.cytoplasmecode.plantwatering`
   - **SHA-1 certificate fingerprint**: see below
5. Click **Create**

### Getting your SHA-1 fingerprint

**Debug keystore** (for development):

```bash
keytool -list -v \
  -keystore ~/.android/debug.keystore \
  -alias androiddebugkey \
  -storepass android \
  -keypass android
```

Copy the `SHA1:` line from the output.

**Release keystore** (for production):

```bash
keytool -list -v \
  -keystore /path/to/your/release.keystore \
  -alias your-alias
```

> **Tip:** You need a separate OAuth credential for each signing key (debug and release). Both can be added to the same GCP project.

---

## 5. Open and build in Android Studio

1. Open Android Studio
2. Click **File → Open** and select the `android/` folder from this repository
3. Wait for the Gradle sync to complete — all dependencies download automatically
4. Connect a device or start an emulator
5. Click **Run ▶**

---

## 6. First launch

1. Tap **Continue with Google**
2. Select the Google account you added as a test user in step 3
3. Grant Calendar access when prompted
4. Tap **+** to add your first plant — enter a name and how many days between waterings
5. The app creates a `Water <plant>` event in your primary Google Calendar

---

## Multiple users

For two or more people to share the same plant schedule:

1. Each person signs into the app with **the same Google account** (or an account that has edit access to the primary calendar)
2. That's it — watering events are written to the shared calendar, and the event title records who watered (`DONE by <name> - <plant>`)

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Sign-in immediately fails | Confirm your Google account is listed as a test user on the OAuth consent screen |
| Calendar events not appearing | Verify the Calendar API is enabled and the `calendar` scope is on the consent screen |
| `CERTIFICATE_VERIFY_FAILED` | The SHA-1 in GCP doesn't match the keystore used to sign the installed APK |
| No notifications | Grant `POST_NOTIFICATIONS` permission in system settings (required on Android 13+) |
