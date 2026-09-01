# iTantra: Neural Offline Walkie-Talkie

<div align="center">

**Zero-Cloud, Fully Offline Neural Voice Communication System for Android**  
*Peer-to-Peer Auto-Discovery | Dual-Engine Speech Recognition | Neural Voice Synthesis | Full-Duplex Socket Protocol*

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF.svg?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4.svg?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ONNX](https://img.shields.io/badge/sherpa--onnx-v1.13.6-005CED.svg?style=flat-square&logo=onnx&logoColor=white)](https://github.com/k2-fsa/sherpa-onnx)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-3DDC84.svg?style=flat-square&logo=android&logoColor=white)](https://android.com)
[![Architecture](https://img.shields.io/badge/Architecture-ARM64--v8a-blueviolet.svg?style=flat-square)](https://developer.android.com/ndk)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](LICENSE)

</div>

---

## Executive Summary

iTantra is an offline-first, peer-to-peer neural voice communication application engineered for high-assurance, low-latency transmission across air-gapped local area networks. By eliminating external cloud dependencies, remote servers, and intermediate proxies, the entire computational pipeline—including Voice Activity Detection (VAD), Automatic Speech Recognition (ASR/STT), Socket Transport, and Text-to-Speech (TTS)—executes entirely on-device using quantized ONNX neural networks.

---

## Core Technical Highlights

* **Air-Gapped Privacy & Security:** Zero telemetry, no data egress, and no cloud infrastructure dependencies. All voice recognition and synthesis tasks operate locally on the device CPU/NPU.
* **Dual-Engine On-Device STT:**
  * **English Engine:** SenseVoice Small Int8 with Inverse Text Normalization (`useITN=true`) and automated non-linguistic token sanitization.
  * **Hindi Engine:** OpenAI Whisper-Base Int8 with forced language decoding (`language="hi"`, `task="transcribe"`) offering high accuracy for Devanagari speech.
* **Piper Neural TTS Synthesis:**
  * **English Voice Model:** Piper Amy-Low (`en_US`).
  * **Hindi Voice Model:** Piper Pratham-Medium (`hi_IN`) with unified `espeak-ng` phonemization dictionary tables.
* **Zero-Configuration P2P Auto-Discovery:** Broadcasts UDP discovery packets over port `9999` with automatic `WifiManager.MulticastLock` management to seamlessly discover and connect active nodes on the subnet.
* **Full-Duplex Socket Protocol:** Concurrent asynchronous TCP client/server architecture over port `8888` featuring non-blocking coroutine dispatch, UTF-8 byte serialization, and exponential backoff retry policies.
* **Emergency Broadcast Protocol:** Priority messaging mechanism that intercepts standard audio routing, overrides receiver volume to `STREAM_ALARM`, and displays high-visibility visual alert states.
* **Tactical Interface Architecture:** Developed using Jetpack Compose and Material 3, incorporating Apple Human Interface Guidelines (HIG) with color-blind accessible visual indicators and tactile haptic feedback.

---

## User Interface & Visual States

| 1. Connected & Ready (Idle) | 2. Live Neural Recording |
| :---: | :---: |
| <img src="docs/screenshots/01_connected_idle.png" width="360" alt="Connected - Idle State" /> | <img src="docs/screenshots/02_connected_recording.png" width="360" alt="Connected - Recording State" /> |
| **Connected & Idle (English Voice)**<br/>*Peer IP resolved via UDP broadcast, system readiness indicators active, monospace timestamped transcript history.* | **Active Neural Recording (Hindi Voice)**<br/>*Radial glow state, dynamic waveform visualizer, full-utterance buffer accumulation.* |

| 3. Emergency Alert Broadcast | 4. Searching & Discovery |
| :---: | :---: |
| <img src="docs/screenshots/03_emergency_recording.png" width="360" alt="Emergency Alert State" /> | <img src="docs/screenshots/04_searching_empty.png" width="360" alt="Searching - Empty State" /> |
| **Emergency Priority Broadcast**<br/>*Pulsing core indicator, high-contrast ALERT banner, automated `STREAM_ALARM` receiver volume boost.* | **UDP Network Discovery**<br/>*Triple-redundant status indicator (`Searching`) broadcasting beacons across local network interfaces.* |

---

## End-to-End System Architecture

```mermaid
flowchart TD
    subgraph DeviceA ["Device A (Local Node)"]
        MicA["Microphone Input (16kHz PCM)"] --> AudioAccA["Audio Buffer Accumulator (PTT)"]
        AudioAccA --> STTA{"Dual-Engine STT Selector"}
        
        STTA -- "English Active" --> SenseVoiceA["SenseVoice Small Int8 (useITN=true)"]
        STTA -- "Hindi Active" --> WhisperA["Whisper-Base Int8 (task=transcribe)"]
        
        SenseVoiceA --> CleanA["Sanitize Text & Strip Tokens"]
        WhisperA --> CleanA
        
        CleanA --> NetSendA["TCP Socket Client :8888 (UTF-8)"]
        UDPSendA["UDP Beacon Broadcaster :9999"] -. "Discovery" .-> Subnet["Local Subnet / Wi-Fi / Hotspot"]
        
        NetRecvA["TCP Socket Server :8888"] <-- "Incoming UTF-8" -- Subnet
        NetRecvA --> ScriptDetA{"Script Analyzer"}
        ScriptDetA -- "Latin" --> PiperEngA["Piper Amy-Low TTS"]
        ScriptDetA -- "Devanagari" --> PiperHinA["Piper Pratham TTS"]
        
        PiperEngA --> AudioTrackA["AudioTrack (MODE_STREAM)"]
        PiperHinA --> AudioTrackA
        AudioTrackA --> SpkA["Speaker / Alarm Stream"]
    end

    subgraph DeviceB ["Device B (Remote Peer Node)"]
        NetSendA ==>|Full-Duplex TCP Socket| NetRecvB["TCP Socket Server :8888"]
        NetRecvB --> TTSB["Piper Neural TTS (Auto-Switch Voice)"]
        TTSB --> SpkB["Speaker Output"]
        
        MicB["Microphone Input"] --> STTB["SenseVoice / Whisper STT"]
        STTB --> NetSendB["TCP Socket Client :8888"]
        NetSendB ==>|Full-Duplex TCP Socket| NetRecvA
    end
```

---

## Neural Inference Pipeline Specification

```
                                   +----------------------------------------------+
                                   |              Input Audio Stream              |
                                   |           (16,000 Hz, 16-bit Mono)           |
                                   +----------------------┬-----------------------+
                                                          |
                                         +----------------┴----------------+
                                         |                                 |
                                         v                                 v
                         +------------------------------+  +------------------------------+
                         |   AppLanguage.ENGLISH        |  |   AppLanguage.HINDI          |
                         |   SenseVoice Small Int8      |  |   Whisper-Base Int8          |
                         +------------------------------+  +------------------------------+
                         | * Language: "en" (Hardcoded) |  | * Language: "hi" (Hardcoded) |
                         | * Inverse Text Normalization |  | * Task: "transcribe"         |
                         | * Emotion Token Sanitizer    |  | * Greedy Search Decoding     |
                         | * Multi-Threaded Inference   |  | * Multi-Threaded Inference   |
                         |   (2-4 CPU Cores)            |  |   (2-4 CPU Cores)            |
                         +--------------┬---------------+  +--------------┬---------------+
                                        |                                 |
                                        +----------------┬----------------+
                                                         |
                                                         v
                                   +----------------------------------------------+
                                   |              Sanitized UTF-8 Text            |
                                   |        (Transmitted via TCP Port 8888)       |
                                   +----------------------┬-----------------------+
                                                          |
                                                          v
                                   +----------------------------------------------+
                                   |            Piper VITS Neural TTS             |
                                   |   Shared espeak-ng-data (Amy & Pratham)      |
                                   +----------------------┬-----------------------+
                                                          |
                                                          v
                                   +----------------------------------------------+
                                   |        AudioTrack (MODE_STREAM) 16kHz        |
                                   +----------------------------------------------+
```

---

## State Machine & Interaction Matrix

| Operational State | Visual Cue | Push-to-Talk Component | Audio Routing | System Behavior |
|---|---|---|---|---|
| **Searching** | `Searching` (Amber) | Dark Core + Emerald Stroke | Inactive | Broadcasts UDP beacons over port `9999` across active network interfaces. |
| **Connected - Idle** | `Connected [IP]` (Green) | Dark Core + Emerald Stroke | Standby | Node registered and ready for zero-latency Push-to-Talk activation. |
| **Recording (Active)** | `Connected [IP]` (Green) | Radial Pulse + Waveform Bars | Mic Input (16kHz PCM) | Accumulates unchunked audio buffers in memory throughout PTT hold. |
| **Transmitting** | `Connected [IP]` (Green) | Amber Ring + `Transmitting` | TCP Socket Client | Dispatches sanitized UTF-8 payload over TCP port `8888`. |
| **Emergency Mode** | `ALERT` (Red Pill) | Pulsing Red Core + Ambient Halo | `STREAM_ALARM` Override | Prepends `[ALERT]` token; forces maximum alarm volume on receiver. |

---

## Packaging & Binary Optimization

Through structural dependency auditing and model quantization, the binary footprint has been streamlined for resource-constrained edge devices:

* **Desktop Native Library Exclusion:** Excluded ~107 MB of unused desktop binaries (OSX `.dylib` and Windows `.dll`) inadvertently packaged by upstream Java libraries.
* **Optimized Whisper Model Sizing:** Replaced 374 MB Whisper-small weights with 153 MB Whisper-base Int8 weights, improving decoding latency by ~2.5x with negligible impact on Hindi transcription fidelity.
* **Dictionary Table Pruning:** Removed redundant non-English/non-Hindi phonetic dictionaries from `espeak-ng-data`, reclaiming ~18 MB of storage.
* **Net Package Reduction:** Total APK size decreased from **635 MB** to **~439 MB** (~30% overall size reduction).

---

## Technical Stack & Library Manifest

| Layer | Component | Specification |
|---|---|---|
| **Platform Language** | Kotlin 2.0+ | Coroutine concurrency, StateFlow, SupervisorJobs, thread synchronization locks. |
| **UI Framework** | Jetpack Compose / Material 3 | Declarative components, custom Canvas waveform visualizers, adaptive layouts. |
| **Inference Engine** | sherpa-onnx v1.13.6 | Hardware-accelerated JNI runtime interfacing ONNX Runtime Mobile. |
| **English STT** | SenseVoice Small Int8 | End-to-end non-autoregressive acoustic model with ITN support. |
| **Hindi STT** | OpenAI Whisper-Base Int8 | Sequence-to-sequence encoder-decoder model optimized for Hindi speech. |
| **Voice Activity Detection** | Silero VAD v5 | Low-latency neural voice activity and speech segment detection. |
| **Neural TTS** | Piper VITS (`en_US`, `hi_IN`) | High-quality text-to-phoneme-to-waveform neural voice synthesis. |
| **Network Protocol** | Standard Java/Android Sockets | UDP Discovery (Port `9999`), TCP Stream Comms (Port `8888`). |

---

## Setup & Deployment Guide

### Prerequisites
* Two physical Android devices running **Android 8.0 (API level 26) or newer** on `arm64-v8a` hardware.
* Both devices associated with the **same local Wi-Fi network** or interconnected via a **Mobile Hotspot**.
* [Git LFS](https://git-lfs.github.com/) installed locally to pull ONNX model checkpoints.

### 1. Clone Repository & Pull Model Checkpoints
```bash
# Clone the repository
git clone https://github.com/NipunAdarsh/Itantra.git
cd Itantra

# Retrieve LFS-tracked neural model binaries
git lfs pull
```

### 2. Build via Gradle
```bash
# Compile and build the debug APK package
./gradlew assembleDebug

# Deploy to connected device via ADB
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Operational Workflow
1. Launch **iTantra** on both target devices.
2. Grant requested **Microphone** and **Location/Nearby Devices** runtime permissions.
3. Observe the top status bar automatically transition to `Connected <Peer_IP>` within 1–2 seconds.
4. **Press and hold the PTT button** to record speech. Release to automatically transcribe, transmit, and synthesize voice on the peer terminal.

---

## Security, Privacy & Network Integrity

* **Zero Cloud Ingestion:** Audio waveforms and text payloads never leave the local area network.
* **Air-Gapped Readiness:** Fully operational in restricted, off-grid environments without public internet access.
* **Point-to-Point Architecture:** Socket traffic is routed directly between the source and destination IP addresses on the local subnet.

---

## License & Acknowledgements

This project is licensed under the Apache License 2.0.

Acknowledgements to the open-source speech AI initiatives:
* [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) (Next-gen Kaldi / K2-FSA)
* [SenseVoice](https://github.com/FunAudioLLM/SenseVoice) (FunAudioLLM / Alibaba)
* [Whisper](https://github.com/openai/whisper) (OpenAI)
* [Piper TTS](https://github.com/rhasspy/piper) (Rhasspy Voice Assistant)
* [Silero VAD](https://github.com/snakers4/silero-vad) (Silero Team)
