# iTantra - Offline Walkie-Talkie Prototype

iTantra is an offline Android walkie-talkie application that enables private, secure, and server-less communication between two devices on the same WiFi network. It leverages state-of-the-art AI models for local Speech-to-Text (STT) and Text-to-Speech (TTS) without requiring an internet connection.

## 🚀 Key Features

- **100% Offline:** Works entirely on a local WiFi network. No cloud, no servers, no data leaves your device.
- **AI-Powered STT:** Uses `sherpa-onnx` with the **Whisper Tiny** model for fast and accurate local transcription.
- **Neural TTS:** Uses the **Piper** TTS engine for high-quality, natural-sounding voice playback.
- **Smart Voice Detection:** Integrated **Silero VAD** (Voice Activity Detection) automatically detects when you start and stop speaking.
- **Emergency Alert Mode:** A special toggle that prefixes messages with `[ALERT]`, forcing the receiver's device to maximum volume for critical announcements.
- **Dual Mode UI:** Easily switch between **SENDER** and **RECEIVER** roles.

## 🛠️ Prerequisites

Before you begin, ensure you have the following:

1.  **Hardware:** Two Android devices (API 24+) connected to the same WiFi router (or one device connected to the other's Hotspot).
2.  **Git LFS:** This project uses **Git Large File Storage (LFS)** to manage AI models. Ensure it is installed on your machine.
    - [Install Git LFS](https://git-lfs.github.com/)
3.  **Android Studio:** Latest version (Ladybug or newer recommended).

## 📥 Installation & Setup

### 1. Clone the Repository
```bash
git clone https://github.com/NipunAdarsh/Itantra.git
cd Itantra
```

### 2. Pull Large Files (Models)
Since the ONNX models are stored in Git LFS, you must pull them manually after cloning:
```bash
git lfs pull
```

### 3. Open in Android Studio
- Open Android Studio and select **Open Project**.
- Navigate to the `Itantra` folder.
- Wait for the **Gradle Sync** to complete.

### 4. Build and Run
- Connect your Android devices.
- Build the project (`Build > Make Project`).
- Install the APK on both devices.

## 📱 How to Use

1.  **Permission:** Grant the **Microphone (RECORD_AUDIO)** permission on launch.
2.  **Receiver Setup:**
    - On Device B, tap **RECEIVER**.
    - Note the IP address of Device B (you can find this in your Android WiFi settings).
3.  **Sender Setup:**
    - On Device A, tap **SENDER**.
    - Enter Device B's IP address in the text field.
4.  **Communicate:**
    - On Device A, **Press and Hold** the red **PTT** button.
    - Speak clearly. Release the button when finished.
    - Device A will transcribe your speech and send the text.
    - Device B will receive the text and speak it out loud using TTS.

## 🧠 Technical Stack

- **UI:** Jetpack Compose (Material 3)
- **Concurrency:** Kotlin Coroutines
- **AI Engine:** [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- **Models:**
  - **VAD:** Silero VAD (ONNX)
  - **STT:** Whisper Tiny (ONNX)
  - **TTS:** Piper (vits-piper-en_US-amy-low)
- **Networking:** Standard TCP Sockets (Port 8888)

## ⚖️ License

This project is a prototype built for educational and demonstration purposes. Check individual model licenses (Whisper, Piper, Silero) for commercial usage.
