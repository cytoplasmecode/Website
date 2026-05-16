# Plant Watering — Setup Guide

See [`docs/SETUP.md`](docs/SETUP.md) for the full step-by-step guide covering Google Cloud project setup, OAuth credentials, SHA-1 fingerprints, and shared calendar configuration.

---

## Quick summary

1. Create a GCP project, enable **Google Calendar API**, create an Android OAuth 2.0 credential with your package name (`com.cytoplasmecode.plantwatering`) and debug SHA-1
2. Open the `android/` folder in Android Studio and run
3. Sign in → pick a calendar (including shared ones) → add plants

## App behaviour

| Action | What happens |
|---|---|
| Add plant "Basil", every 3 days | Creates `Water Basil` all-day event in the selected calendar |
| Tap **Water Now 💧** | Renames the event to `DONE by Alice - Basil`; creates the next `Water Basil` event |
| Edit interval (✏) | Deletes the pending event only; creates a new one with the new schedule |
| Delete plant | Removes the pending calendar event and the local record |
| **⋮ → Change calendar** | Shows the calendar picker again; new events go to the newly selected calendar |
| Daily background check | Sends a notification for every plant that is due or overdue |
| Device reboot | WorkManager reminder is automatically rescheduled |

## Using a shared calendar

Share a Google Calendar with everyone in your household (grant **"Make changes to events"** access). Each person signs into the app with their **own** account, then picks the shared calendar in the picker. All watering events land there, and the event title records who watered each plant.
