# iTantra - High-Accuracy Offline Walkie-Talkie

iTantra is an offline Android walkie-talkie application designed for private, high-fidelity communication over local WiFi. It integrates advanced on-device AI models for Speech-to-Text (STT) and Text-to-Speech (TTS), ensuring that no data ever leaves your network.

## ✨ Latest Updates (Version 1)
- **SenseVoice Integration**: Upgraded from Whisper to the **SenseVoice** model, offering superior accuracy for Indian-accented English and regional languages.
- **Full Bundling**: All AI models are now bundled directly within the APK. The app works **100% offline immediately upon installation**—no first-run downloads required.
- **Optimized Storage**: Uses **int8 quantization** for the SenseVoice model to balance high accuracy with a reasonable app size (~290MB).
- **Emergency Audio Routing**: Alert messages now trigger the `STREAM_ALARM` channel, ensuring critical announcements are heard even if media volume is low.

## 🚀 Key Features
- **Push-To-Talk (PTT)**: Simple, physical-style walkie-talkie interface.
- **SenseVoice STT**: Blazing fast, local transcription with automatic language detection.
- **Piper TTS**: Natural neural voices for message playback.
- **Emergency Alert Mode**: Overrides receiver volume for urgent broadcasts.
- **Serverless**: Uses peer-to-peer TCP communication on port 8888.

## 🛠️ Prerequisites
- **Hardware**: Two Android devices (API 26+) on the same WiFi or one using the other's Hotspot.
- **Git LFS**: **Mandatory.** The project uses Git Large File Storage for the `.onnx` models.
- **Android Studio**: Ladybug (2024.2.1) or newer recommended.

## 📥 Detailed Installation Steps

### 1. Install Git LFS
If you don't have Git LFS installed, download it from [git-lfs.com](https://git-lfs.github.com/). This is required to download the AI models correctly.

### 2. Clone the Repository
```bash
git clone https://github.com/NipunAdarsh/Itantra.git
cd Itantra
```

### 3. Fetch the AI Models
After cloning, you **must** run this command to download the actual model files (otherwise you will only have small pointer files):
```bash
git lfs pull
```

### 4. Open and Sync
- Launch Android Studio.
- Select **Open** and choose the `Itantra` folder.
- Wait for the **Gradle Sync** to finish. If prompted about missing SDKs or tools, click the links to install them.

### 5. Build and Deploy
- Connect your first Android device.
- Click **Run 'app'**.
- Repeat for the second device.

## 📱 How to Use
1. **Permissions**: Allow **Microphone** access on both devices.
2. **Receiver**:
   - Set Device B to **RECEIVER** mode.
   - Note the **IP Address** shown in your phone's WiFi settings (e.g., `192.168.1.5`).
3. **Sender**:
   - Set Device A to **SENDER** mode.
   - Enter Device B's IP address into the text field.
4. **Talk**:
   - **Hold** the red **PTT** button on Device A.
   - Speak your message.
   - **Release** the button. Transcription will happen locally, and the text will be sent to Device B, where it will be spoken aloud.

## 🧠 Technical Stack
- **UI**: Jetpack Compose (Material 3)
- **AI Engine**: [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) v1.13.6
- **Models**:
  - **STT**: SenseVoice Small (Int8 Quantized)
  - **TTS**: Piper (Amy-Low)
  - **VAD**: Silero VAD v5
- **Networking**: Kotlin Coroutines + TCP Sockets

## ⚖️ License
This project is a prototype for educational use. Please refer to the licenses of the underlying models (SenseVoice, Piper, Silero) for commercial redistribution.
