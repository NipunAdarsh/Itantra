# 🎙️ iTantra — Neural Offline Walkie-Talkie

<div align="center">

> **Zero-Cloud. Zero-Server. Fully Offline Neural Voice Walkie-Talkie for Android.**  
> *P2P Auto-Discovery • Dual-Engine Speech-to-Text • High-Fidelity Neural TTS • Full-Duplex Local Network Comms*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ONNX](https://img.shields.io/badge/sherpa--onnx-v1.13.6-005CED.svg?style=for-the-badge&logo=onnx&logoColor=white)](https://github.com/k2-fsa/sherpa-onnx)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg?style=for-the-badge)](LICENSE)

</div>

---

## 🌟 Key Capabilities

* 🌐 **100% Offline & Private:** Zero telemetry, zero cloud dependencies, zero external APIs. Complete neural inference runs directly on the device's CPU/NPU.
* 🤖 **Dual-Engine On-Device STT:**
  * **English:** [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) Int8 (ultra-fast, inverse text normalization, emotion token filtering).
  * **Hindi:** [OpenAI Whisper-Base](https://github.com/openai/whisper) Int8 (forced Hindi transcription, high accuracy, low latency).
* 🔊 **Piper Neural Text-to-Speech (TTS):**
  * **English Voice:** Piper Amy-Low (`en_US`).
  * **Hindi Voice:** Piper Pratham-Medium (`hi_IN`) with unified `espeak-ng-data` phonemization.
* 📡 **Zero-Config P2P Auto-Discovery:** Instant UDP discovery beacons over port `9999` automatically connect peers on the same local Wi-Fi or mobile hotspot.
* ⚡ **Full-Duplex TCP Sockets:** Non-blocking asynchronous network pipeline over port `8888` with explicit `UTF-8` serialization and exponential backoff.
* 🚨 **Tactical Emergency Broadcast:** Audio stream volume override to `STREAM_ALARM` with distinctive visual flashing alerts.
* 🎨 **Neural Tactical UI:** Built with Jetpack Compose & Material 3, following Apple Human Interface Guidelines (HIG) with color-blind accessible visual cues and tactile haptics.

---

## 📸 Interface & Visual Showcase

| 1. Connected & Ready (Idle) | 2. Live Neural Recording |
| :---: | :---: |
| <img src="docs/screenshots/01_connected_idle.png" width="360" alt="Connected - Idle State" /> | <img src="docs/screenshots/02_connected_recording.png" width="360" alt="Connected - Recording State" /> |
| **Connected & Idle (English Voice)**<br/>*Peer IP auto-discovered, signal indicators active, monospace timestamped transcript history.* | **Active Neural Recording (Hindi Voice)**<br/>*Neon green radial glow, live waveform bars, PTT utterance accumulation.* |

| 3. Emergency Alert Broadcast | 4. Searching & Auto-Discovery |
| :---: | :---: |
| <img src="docs/screenshots/03_emergency_recording.png" width="360" alt="Emergency Alert State" /> | <img src="docs/screenshots/04_searching_empty.png" width="360" alt="Searching - Empty State" /> |
| **Emergency Priority Broadcast**<br/>*Pulsing red core, high-contrast ALERT pill, automatic `STREAM_ALARM` volume boost.* | **UDP Discovery in Progress**<br/>*Triple-redundant status indicator (`↻ Searching`) broadcasting on port 9999.* |

---

## 🏗️ System Architecture

```mermaid
flowchart TD
    subgraph DeviceA ["📱 Device A (Local)"]
        MicA["🎤 Microphone (16kHz PCM)"] --> AudioAccA["Audio Accumulator (PTT Hold)"]
        AudioAccA --> STTA{"Dual-Engine STT"}
        
        STTA -- "English Mode" --> SenseVoiceA["SenseVoice Int8 (useITN=true)"]
        STTA -- "Hindi Mode" --> WhisperA["Whisper-Base Int8 (task=transcribe)"]
        
        SenseVoiceA --> CleanA["Sanitize Text & Strip Tokens"]
        WhisperA --> CleanA
        
        CleanA --> NetSendA["TCP Socket Client :8888 (UTF-8)"]
        UDPSendA["UDP Beacon Sender :9999"] -. "Discovery" .-> WiFiNet["Local Wi-Fi / Hotspot"]
        
        NetRecvA["TCP Socket Server :8888"] <-- "Incoming UTF-8" -- WiFiNet
        NetRecvA --> ScriptDetA{"Script Detector"}
        ScriptDetA -- "Latin" --> PiperEngA["Piper Amy-Low TTS"]
        ScriptDetA -- "Devanagari" --> PiperHinA["Piper Pratham TTS"]
        
        PiperEngA --> AudioTrackA["AudioTrack (MODE_STREAM)"]
        PiperHinA --> AudioTrackA
        AudioTrackA --> SpkA["🔊 Speaker / Alarm Stream"]
    end

    subgraph DeviceB ["📱 Device B (Remote Peer)"]
        NetSendA ==>|Full-Duplex TCP| NetRecvB["TCP Socket Server :8888"]
        NetRecvB --> TTSB["Piper Neural TTS (Auto-Detect Script)"]
        TTSB --> SpkB["🔊 Speaker Output"]
        
        MicB["🎤 Microphone"] --> STTB["SenseVoice / Whisper STT"]
        STTB --> NetSendB["TCP Socket Client :8888"]
        NetSendB ==>|Full-Duplex TCP| NetRecvA
    end
```

---

## 🧠 Dual-Engine Neural Pipeline

```
                                   ┌──────────────────────────────────────────────┐
                                   │              Input Audio Stream              │
                                   │           (16,000 Hz, 16-bit Mono)           │
                                   └──────────────────────┬───────────────────────┘
                                                          │
                                         ┌────────────────┴────────────────┐
                                         ▼                                 ▼
                         ┌──────────────────────────────┐  ┌──────────────────────────────┐
                         │   AppLanguage.ENGLISH        │  │   AppLanguage.HINDI          │
                         │   SenseVoice Small Int8      │  │   Whisper-Base Int8          │
                         ├──────────────────────────────┤  ├──────────────────────────────┤
                         │ • Language: "en" (Hardcoded) │  │ • Language: "hi" (Hardcoded) │
                         │ • Inverse Text Normalization │  │ • Task: "transcribe"         │
                         │ • Emotion Token Sanitizer    │  │ • Greedy Decoding Search     │
                         │ • Execution: Dynamic Multi-  │  │ • Execution: Dynamic Multi-  │
                         │   Threaded (2-4 Cores)       │  │   Threaded (2-4 Cores)       │
                         └──────────────┬───────────────┘  └──────────────┬───────────────┘
                                        │                                 │
                                        └────────────────┬────────────────┘
                                                         ▼
                                   ┌──────────────────────────────────────────────┐
                                   │              Sanitized UTF-8 Text            │
                                   │        (Transmitted via TCP Port 8888)       │
                                   └──────────────────────┬───────────────────────┘
                                                          ▼
                                   ┌──────────────────────────────────────────────┐
                                   │           Piper VITS Neural TTS              │
                                   │   Shared espeak-ng-data (Amy & Pratham)      │
                                   └──────────────────────┬───────────────────────┘
                                                          ▼
                                   ┌──────────────────────────────────────────────┐
                                   │         AudioTrack (MODE_STREAM) 16kHz       │
                                   └──────────────────────────────────────────────┘
```

---

## 📱 State & Interaction Reference

| State | Visual Indicator | PTT Button | Audio Channel | Underlying Action |
|---|---|---|---|---|
| **Searching** | `↻ Searching` (Amber) | Static Dark Circle + Green Border | Standby | Broadcasting UDP beacons on port `9999` across active network interfaces. |
| **Connected - Idle** | `✓ Connected IP` (Green) | Static Dark Circle + Green Border | Standby | Ready for zero-latency Push-to-Talk transmission. |
| **Recording (Active)** | `✓ Connected IP` (Green) | Pulsing Neon Green + Waveform Bars | Mic Input (16kHz PCM) | Audio buffer accumulates full utterance in memory until release. |
| **Transmitting** | `✓ Connected IP` (Green) | Amber Ring + "▶ Transmitting" | TCP Socket Client | Dispatches sanitized UTF-8 payload over TCP port `8888`. |
| **Emergency Mode** | `⚠ ALERT` (Red Badge) | Pulsing Red Core + Ambient Halo | `STREAM_ALARM` Override | Dispatches `[ALERT]` prefixed payload; triggers maximum alarm volume on receiver. |

---

## ⚡ APK Optimization & Performance

Through rigorous resource analysis and model selection, iTantra delivers state-of-the-art on-device AI in an optimized mobile package:

* **Packaging Resource Filter:** Excluded ~107 MB of unwanted desktop binaries (OSX `.dylib`, Windows `.dll`) bundled in upstream Java dependencies.
* **Model Weight Optimization:** Replaced large 374 MB Whisper models with optimized 153 MB Whisper-base Int8 weights, boosting decode speed by ~2.5× while maintaining high Hindi transcription accuracy.
* **Asset Pruning:** Stripped non-English/non-Hindi dictionary files from `espeak-ng-data`, saving ~18 MB.
* **Total APK Size:** Reduced from **635 MB** down to **~439 MB** (~30% overall size reduction with zero feature loss).

---

## 🛠️ Tech Stack & Components

| Component | Technology | Description |
|---|---|---|
| **Language & Tooling** | Kotlin 2.0+ / Coroutines / Flow | Asynchronous concurrency, supervisor jobs, thread locks. |
| **UI Framework** | Jetpack Compose / Material 3 | Declarative tactical UI with custom canvas waveforms. |
| **Neural Runtime** | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) v1.13.6 JNI | Hardware-accelerated on-device neural inference runtime. |
| **English STT** | SenseVoice Small Int8 | Fast offline speech recognition with Inverse Text Normalization. |
| **Hindi STT** | OpenAI Whisper-Base Int8 | Robust multilingual speech recognition optimized for Hindi. |
| **Voice Activity Detection** | Silero VAD v5 | High-precision speech boundary detection. |
| **Neural TTS** | Piper VITS (`en_US-amy-low`, `hi_IN-pratham-medium`) | Natural offline voice synthesis with unified `espeak-ng` tables. |
| **Local Networking** | Java/Android Sockets | UDP Discovery (Port `9999`), TCP Full-Duplex Comms (Port `8888`). |

---

## 🚀 Getting Started

### 1. Requirements
* Two physical Android devices running **Android 8.0 (API 26) or higher** (ARM64-v8a architecture).
* Both devices connected to the **same Wi-Fi network** or one connected to the other's **Mobile Hotspot**.
* [Git LFS](https://git-lfs.github.com/) installed on your machine for cloning model weights.

### 2. Clone Repository with Git LFS
```bash
# Clone the repository
git clone https://github.com/NipunAdarsh/Itantra.git
cd Itantra

# Pull on-device neural model weights
git lfs pull
```

### 3. Build & Run
Open the project in **Android Studio (Ladybug 2024.2.1+)** or build using the Gradle command line:

```bash
# Compile and build the debug APK
./gradlew assembleDebug

# Install on connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. How to Use
1. Install and launch **iTantra** on both phones.
2. Grant the required **Microphone** and **Location/Nearby** permissions.
3. The app will automatically discover the peer device within 1–2 seconds and display `✓ Connected <Peer_IP>`.
4. **Hold the central PTT button** to speak; release when finished.
5. Your speech is transcribed locally, sent over the local network, and spoken aloud on the peer device in real time!

---

## 🔒 Security & Privacy

* 🛡️ **Zero Cloud Exposure:** Audio never touches any external server, cloud endpoint, or analytics tracker.
* 🔒 **Air-Gapped Operation:** Works seamlessly in isolated, private networks with no internet connection required.
* 📦 **Direct Socket Communication:** P2P traffic flows point-to-point directly between device IP addresses on the local subnet.

---

## ⚖️ License & Acknowledgements

This project is open-source under the Apache 2.0 License.

Special thanks to the open-source voice AI community:
* [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) by Next-gen Kaldi / K2-FSA team
* [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) by FunAudioLLM / Alibaba
* [Whisper](https://github.com/openai/whisper) by OpenAI
* [Piper TTS](https://github.com/rhasspy/piper) by Rhasspy team
* [Silero VAD](https://github.com/snakers4/silero-vad) by Silero team
