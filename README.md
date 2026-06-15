# Surakshini: On-Device AI Safety App

Surakshini is a personal safety Android app that runs entirely on-device. It uses three lightweight ML models — motion, location, and audio — to detect potential threats in real time and automatically collect evidence (photos, audio, location) without needing an internet connection for detection.

The goal was to build something that actually works without draining the battery, which meant being deliberate about *when* expensive things like the microphone or camera turn on.

---

## How it works: a 3-stage pipeline

Instead of running all models constantly (which would kill the battery in a few hours), Surakshini uses a tiered approach where each stage only activates the next if something looks wrong:

1. **Always-on (cheap):** Accelerometer is monitored continuously to track motion patterns and check if the user has left their usual "safe zones."
2. **Conditional (audio):** If the motion sensor flags something unusual, the microphone turns on briefly to check for distress sounds like screaming.
3. **Action (only on confirmed threat):** Camera capture, audio recording, and emergency alerts trigger only when both signals agree something's wrong.

This cascading design means the mic and camera — the two biggest battery drains — stay off unless there's a real reason to use them.

---

## The three models

### 1. Location anomaly detection (autoencoder)
A small autoencoder learns the user's normal movement patterns (home, work, regular routes) by compressing GPS coordinates into a lower-dimensional representation and reconstructing them. If the reconstruction error (MSE) goes above a tuned threshold, the current location doesn't match the learned "normal" pattern — flagging a possible anomaly.

### 2. Audio distress detection (CNN)
Raw audio is converted into MFCC spectrograms (a standard way to represent sound as an image-like matrix) and fed into a small 2D CNN. It classifies the sound into categories like ambient noise, normal conversation, or vocal distress (e.g. screaming).

### 3. Motion/struggle detection (SMV thresholding)
Uses the accelerometer's x/y/z axes to compute the signal magnitude vector: SMV = sqrt(x² + y² + z²)

A sudden spike above ~15 m/s² sustained over a short window suggests physical struggle — a strong contrast from normal walking or phone handling.

---

## When a threat is confirmed

If the motion and audio models agree something's wrong, the app:

- Starts recording audio in the background (AAC, 44.1kHz)
- Captures front and rear camera frames via CameraX
- Uploads the audio/photos to a Supabase backend
- Sends an SMS to emergency contacts with live location + a link to the uploaded evidence

All of this happens without blocking the UI, so the app stays usable even mid-emergency.

---

## Tech stack

- **Language:** Kotlin
- **ML inference:** TensorFlow Lite (on-device)
- **Camera:** CameraX
- **Networking:** OkHttp
- **Backend:** Supabase
- **Local storage:** Room

---

## What I'd improve next

- Reduce false positives in the motion detector (e.g. distinguish "phone dropped" from "actual struggle")
- On-device fine-tuning so the location autoencoder adapts faster to new routines
- Battery profiling across different Android OEMs (some throttle background sensors more aggressively)
