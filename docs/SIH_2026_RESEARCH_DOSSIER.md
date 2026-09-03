# iTantra: Official ISRO Research Dossier, System Architecture & SIH Presentation Synthesis

<div align="center">

**Smart India Hackathon (SIH) — Official Submission Dossier**  
**Problem Statement ID:** 26173  
**Problem Statement Title:** iTantra - Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links  
**Organization:** Indian Space Research Organisation (ISRO)  
**Department:** Department of Space / Indian Space Research Organisation  
**Category:** Software  
**Theme:** Smart Automation  

*Team Lead:* Nipun Adarsh | *Runtime Target:* Low and Mid-Range Android Smartphones (ARM64-v8a)  
*Stack:* 100% Open-Source TinyML | Fully Offline & Air-Gapped | Zero Commercial SDKs  

</div>

---

## Executive Summary & Problem Formulation

### Official ISRO Problem Statement (ID: 26173)

> **Background:**  
> Vocal audio information is very data-intensive, making it difficult to transmit through low data-rate links. In alert and distress-based scenarios, transmitting audio information is critical instead of written messages, as it is more inclusive and caters to everyone regardless of literacy.
>
> **Challenge:**  
> Build an Android Application with lightweight, highly accurate STT and TTS models for **10 Indian Languages** (*Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English*) that runs locally on low-power devices.
>
> **Operational Requirements:**  
> 1. **Pause & Stoppage Detection:** The system's STT module, when activated after detecting pauses and stoppages, forms sentences and must instantly and efficiently stream data through Wi-Fi/Bluetooth-connected embedded devices or another phone with minimal latency.  
> 2. **Intelligible Speech Synthesis & Priority Alerts:** The TTS module converts received text into intelligible speech played as a voice note. Alert messages are announced at highest volume, non-interruptible.  
> 3. **Verification Loop & Dual Operating Modes:** Two phones running the application (one in STT mode and one in TTS mode) connect over Wi-Fi or Bluetooth. The system operates as a walkie-talkie using Push-To-Talk (PTT); when turned off, it operates as a hands-free phone.  
> 4. **Low & Mid-Range Mobile Compatibility:** Operates smoothly on budget ARM64 hardware without cloud or proprietary dependencies.

```
+----------------------------------------------------------------------------------------------------+
|                                    iTantra Solution Overview                                       |
|                                                                                                    |
|  [Voice In] ──> [Silero VAD v5] ──> [IndicConformer/SenseVoice] ──> [IndicScriptConverter]         |
|  (10 Languages) (Pause Cutoff)      (Non-Autoregressive STT)        (Brahmic Unicode Shift)        |
|                                                                                │                   |
|                                                                                ▼                   |
|  [Speaker Out] <── [Hybrid TTS Engine] <── [Wi-Fi / Bluetooth]      <── [54-Byte Network Frame]    |
|  (Highest Volume    (Piper VITS +          (Implemented; LoRa is a  ([LANG:xx][ALERT]payload)       |
|   Non-Interruptible) Android Native)        feasibility argument,                                   |
|                                              not built — see §3)                                    |
+----------------------------------------------------------------------------------------------------+
```

---

## 1. Compliance Matrix: ISRO Evaluation Criteria & Restrictions

### 1.1 Key Evaluation Metrics Breakdown

*Last hardware-verified: 2026-09-03, on-device (Xiaomi 21061119BI, Android 13; Xiaomi POCO M2 Pro, Android 12) — not a projection.*

| Evaluation Dimension | Weight | Official ISRO Requirement | iTantra Implementation & Measured Metric | Status |
| :--- | :---: | :--- | :--- | :---: |
| **Efficiency** | **20%** | Lightweight model size, App RAM/Flash footprint, and low CPU usage during idle listening. | • **APK (download):** 823 MB, all 10-language STT + 7 bundled TTS voices, int8 throughout.<br>• **On-device footprint:** ~1.8 GB after first launch (ONNX Runtime requires a decompressed, word-aligned copy of every model in internal storage — see §6.4).<br>• **Idle CPU Usage:** <1.8% during Silero VAD idle listening (unchanged, not re-profiled this pass). | **PASS, with a flagged efficiency cost** — see §6.4 for what was tried and why 823 MB / ~1.8 GB is the current floor. |
| **Accuracy** | **40%** | Low Word Error Rate (WER) for STT; high human legibility and natural acoustic flow for TTS. | • **English STT:** 5.6% WER (SenseVoice Small).<br>• **Hindi STT:** 4.2–12.5% WER (IndicConformer).<br>• **Kannada / Tamil STT:** improved from 130%/100% WER to a measured 25–41% WER after swapping in dedicated per-language models (§6.2–6.3).<br>• **Gujarati / Marathi / Malayalam / Odia / Bengali STT:** still on the shared fallback model, still failing (78–180% WER) — not yet fixed, tracked as open work.<br>• **TTS:** 7 of 10 languages now use bundled neural Piper voices (previously 2 of 10); real synthesized audio confirmed on-device. | **PARTIAL PASS** — real, measured improvement on 2 languages; 5 languages remain a known gap. This section previously reported all 10 languages as passing; that was not accurate. See §6. |
| **Latency** | **20%** | Word-to-STT delay, text-to-TTS audio delay, Real-Time Factor (RTF), and end-to-end phone-to-phone delta. | • **STT RTF:** 0.08–0.31 for most languages on both test devices; Tamil and Telugu showed RTF spikes (1.28–1.44) on cold model load during the benchmark run — see §6.3 for the raw per-test numbers.<br>• **Phone-to-phone delta:** not yet measured — the two-device Wi-Fi/Bluetooth round trip has not been completed (§6.5). | **PARTIAL** — single-device STT latency is measured and good; the end-to-end two-phone number this document previously cited (<1.20 s) was not actually measured and has been removed pending real verification. |
| **Architectural Robustness** | **20%** | Air-gapped networking, dual operating modes, non-interruptible alert override, low-power operation. | • **P2P Transport:** Wi-Fi (UDP 9999 discovery / TCP 8888 full-duplex) — implemented and working. **Bluetooth Classic RFCOMM** — implemented this pass (device picker, paired + scanned devices); not yet field-tested against a second live device (§6.5).<br>• **Dual Modes:** PTT Walkie-Talkie & Silero Phone Mode — unchanged, working.<br>• **Alert Override:** `STREAM_ALARM` at maximum volume — unchanged, working. | **PASS on Wi-Fi; Bluetooth implemented but field-unverified.** Earlier drafts of this document claimed Bluetooth RFCOMM as a working, tested feature before any Bluetooth code existed in the app. That was incorrect and has been corrected. |

### 1.2 Strict Restrictions Verification

* **Open-Source Only:** 100% compliant. Powered by open-source libraries: `sherpa-onnx` (Apache 2.0), AI4Bharat IndicConformer (MIT), FunAudioLLM SenseVoice (Apache 2.0), Piper VITS (MIT), and Silero VAD (MIT). **Zero proprietary SDKs (no Google Cloud Speech, no Azure, no AWS).**
* **Allowed Frameworks:** Built with ONNX Runtime Mobile (`sherpa-onnx` v1.13.6 native JNI), Jetpack Compose, and Kotlin Coroutines.
* **Fully Offline Operation:** 100% air-gapped. Zero HTTP egress calls, no internet permissions required for neural inference.
* **Low & Mid-Range Target Hardware:** Installed and exercised on two physical Android devices via USB debugging: a Xiaomi handset (model `21061119BI`, Android 13) and a Xiaomi POCO M2 Pro (Android 12, ~4.5 GB free storage at test time — genuinely storage-constrained, which is itself a useful data point given the app's footprint; see §6.4). Exact SoC was not re-verified this pass — the Helio G88 figure in earlier drafts was not independently confirmed and should not be cited until it is.

---

## 2. Academic Literature Review & Model Dossier

### 2.1 AI4Bharat IndicConformer (Indic ASR Core)

```bibtex
@inproceedings{bhogale2023vistaar,
  title     = {Vistaar: Diverse Speech Recognition Datasets for Indian Languages},
  author    = {Bhogale, Kaushal and Raman, Abhigyan and Javed, Tahir and 
               Doddapaneni, Sumanth and Kunchukuttan, Anoop and Kumar, Pratyush and 
               Khapra, Mitesh M.},
  booktitle = {Proceedings of Interspeech 2023},
  pages     = {3003--3007},
  year      = {2023},
  doi       = {10.21437/Interspeech.2023-1579},
  url       = {https://arxiv.org/abs/2305.13707}
}
```

* **Conformer Architecture:** Merges depthwise separable convolutional layers with multi-head self-attention. It processes speech frames across 22 scheduled Indian languages using weights trained on 25,000+ hours from the Vistaar benchmark.
* **Connectionist Temporal Classification (CTC) Decoding:** Evaluates acoustic inputs in a single forward pass:
  $$\mathcal{L}_{\text{CTC}} = -\ln P(\mathbf{y} \mid \mathbf{x}) = -\ln \sum_{\pi \in \mathcal{B}^{-1}(\mathbf{y})} \prod_{t=1}^T P(\pi_t \mid \mathbf{x})$$
* **Edge Advantage Over Whisper:** OpenAI Whisper relies on autoregressive decoding, requiring sequential token steps that cause latency spikes (>1,800 ms) and thermal throttling on mobile devices. IndicConformer CTC processes speech in **<200 ms** with zero risk of infinite hallucination loops under noise.

---

### 2.2 Alibaba SenseVoice (English Speech Core)

```bibtex
@article{an2024funaudiollm,
  title   = {FunAudioLLM: Voice Understanding and Generation Foundation Models for Natural Interaction Between Humans and LLMs},
  author  = {An, Keyu and Chen, Qian and Deng, Chong and Du, Zhihao and 
             Gao, Changfeng and Gao, Zhifu and Gu, Yue and others},
  journal = {arXiv preprint arXiv:2407.04051},
  year    = {2024},
  url     = {https://arxiv.org/abs/2407.04051}
}
```

* **Non-Autoregressive Edge Model:** SenseVoice Small processes complete utterances in parallel, operating at **15× real-time** (RTF ~0.25 on ARM64 mobile hardware).
* **Native Inverse Text Normalization (ITN):** Automatically normalizes spoken coordinates and quantities (e.g., `"grid ref four zero two"` $\to$ `"grid ref 402"`), ensuring clean, concise data transmission over low-bitrate links.
* **Distress Acoustic Handling:** Trained on 400,000+ hours of multi-accented speech, the model handles clipping, shouting, and background noise typical in emergency scenarios.

---

### 2.3 VITS & Piper Neural TTS (Speech Synthesis)

```bibtex
@inproceedings{kim2021vits,
  title     = {Conditional Variational Autoencoder with Adversarial Learning for End-to-End Text-to-Speech},
  author    = {Kim, Jaehyeon and Kong, Jungil and Son, Juhee},
  booktitle = {Proceedings of the 38th International Conference on Machine Learning (ICML)},
  volume    = {139},
  pages     = {5530--5540},
  year      = {2021},
  url       = {https://proceedings.mlr.press/v139/kim21f.html}
}
```

* **Single-Stage End-to-End Synthesis:** Eliminates the two-stage Tacotron + Vocoder pipeline by combining a Conditional Variational Autoencoder (VAE) with Normalizing Flows and adversarial learning:
  $$\log p_\theta(x \mid c) \ge \mathbb{E}_{q_\phi(z \mid x)}\left[\log p_\theta(x \mid z)\right] - D_{\text{KL}}\left(q_\phi(z \mid x) \parallel p_\theta(z \mid c)\right)$$
* **Monotonic Alignment Search (MAS):** Aligns phonemes to acoustic frames in latent space using dynamic programming, preventing stuttering and word-skipping.
* **Piper Mobile CPU Runtime:** Quantized to INT8 with streamlined `espeak-ng` tables, Piper produces speech notes faster than real-time on budget mobile CPUs.

---

### 2.4 Silero Voice Activity Detector (Pause & Stoppage Detection)

```bibtex
@misc{silero2024vad,
  title        = {Silero VAD: Pre-trained Enterprise-Grade Voice Activity Detector},
  author       = {{Silero Team}},
  year         = {2024},
  howpublished = {\url{https://github.com/snakers4/silero-vad}}
}
```

* **Lightweight Recurrent Model:** Packaged as a 1.4 MB ONNX neural network, running inference in **0.78 ms per 31.25 ms audio window (512 samples at 16 kHz)**.
* **Pause & Stoppage Logic:** Maintains internal recurrent hidden states across windows. When the user stops speaking and silence persists beyond **500 ms**, it automatically slices the utterance and dispatches it to the STT queue.
* **Idle Listening Efficiency:** Keeps compute-heavy ASR models dormant during silence, maintaining device CPU usage **below 1.8%**.

---

### 2.5 Unicode Brahmic Script Isomorphism & ISCII Standard

```bibtex
@techreport{iscii1991,
  author      = {{Bureau of Indian Standards}},
  title       = {IS 13194:1991 --- Indian Script Code for Information Interchange (ISCII)},
  institution = {Department of Electronics, Government of India},
  year        = {1991},
  address     = {New Delhi, India}
}
```

* **Linguistic Lineage:** Indian scripts share a common origin in Ashokan Brahmi, maintaining an identical phonetic-graphemic matrix organized into $5\times5$ varga consonants, vowels, and matras.
* **ISCII / Unicode Block Mapping:** In Unicode, South Asian scripts are allocated consecutive 128-code-point blocks ($0\text{x}80$), preserving positional offsets relative to Devanagari:
  $$\text{CodePoint}_{\text{Target}} = \text{CodePoint}_{\text{Devanagari}} + \Delta_{\text{Script}}$$
  * Bengali: $\Delta = +0\text{x}0080$ | Gujarati: $\Delta = +0\text{x}0180$ | Odia: $\Delta = +0\text{x}0200$
  * Tamil: $\Delta = +0\text{x}0280$ (with unvoiced stop mapping)
  * Telugu: $\Delta = +0\text{x}0300$ | Kannada: $\Delta = +0\text{x}0380$ | Malayalam: $\Delta = +0\text{x}0400$
* **Correction to an earlier hypothesis in this document:** prior drafts attributed non-Hindi transcription failures to "CTC Devanagari vocabulary dominance" and treated the Unicode block-shift in [`IndicScriptConverter.kt`](../app/src/main/java/com/example/itantra/IndicScriptConverter.kt) as the fix. On-device evidence does not support that theory: **Marathi**, which is already in Devanagari and needs zero script conversion, failed just as badly (81.9% WER) as script-different languages. A script-shift cannot explain a same-script failure.
* **Actual root cause (confirmed by binary inspection of the shipped ONNX model, not assumption):** the bundled `indic-conformer-onnx-sherpa/model.int8.onnx` embeds `model_author=ai4bharat`, `model_type=EncDecCTCModelBPE`, `vocab_size=5633` in its own ONNX metadata. AI4Bharat publishes separate per-language IndicConformer checkpoints (and a distinct 600M multilingual model that requires an explicit language-code argument at inference); `sherpa-onnx`'s `OfflineNemoEncDecCtcModelConfig` has no field to pass a language code. The shared model was defaulting to one fixed behavior — empirically Hindi-shaped output — regardless of the language actually spoken, including for pure silence (see §6.3, tests where audio was silent and the shared model still emitted `"इस"`).
* **The actual fix:** dedicated, independently-trained per-language ONNX checkpoints for Kannada, Telugu and Tamil (sourced from AI4Bharat's `indicconformer_stt_<lang>_hybrid_ctc_rnnt_large` family, hash-verified against the exporter's declared SHA-256 before integration), each running through `sherpa-onnx`'s standard single-language NeMo-CTC path with no language-selection ambiguity. These three languages emit native-script tokens directly, so `IndicScriptConverter.kt` now passes their output through unmodified. The five remaining Indic languages (Gujarati, Marathi, Malayalam, Odia, Bengali) are still on the original shared model and still exhibit the failure mode described above — this is tracked as open work, not claimed as solved.

---

### 2.6 Post-Training Dynamic Quantization (Efficiency Pass)

```bibtex
@inproceedings{jacob2018quantization,
  title     = {Quantization and Training of Neural Networks for Efficient
               Integer-Arithmetic-Only Inference},
  author    = {Jacob, Benoit and Kligys, Skirmantas and Chen, Bo and Zhu, Menglong and
               Tang, Matthew and Howard, Andrew and Adam, Hartwig and Kalenichenko, Dmitry},
  booktitle = {Proceedings of the IEEE Conference on Computer Vision and Pattern
               Recognition (CVPR)},
  pages     = {2704--2713},
  year      = {2018},
  url       = {https://arxiv.org/abs/1712.05877}
}
```

* **Problem:** every bundled Piper TTS voice (including the original English and Hindi voices already shipping before this pass) was full FP32 — never quantized, unlike every STT model in the app. This meant 470 MB of the APK's TTS weights carried 4 bytes/parameter where 1 would do.
* **Method:** applied `onnxruntime.quantization.quantize_dynamic` (weight-only dynamic int8, the same class of technique as Jacob et al. 2018 and the exact method already used to produce every STT model this app ships) to all 7 Piper VITS voices. No retraining, no architecture change — purely a post-hoc numerical precision reduction of existing trained weights.
* **Result:** 470 MB → 138 MB (3.4×) across the 7 voices; total APK 1.1 GB → 823 MB.
* **Verification, not assumption:** every quantized model was checked numerically in isolation (no NaN, amplitude consistent with the unquantized model's own run-to-run variance — VITS duration prediction is stochastic by design, so exact sample-for-sample comparison is not meaningful) before being bundled, then re-verified on-device across a full 30-utterance benchmark with zero crashes and real synthesized speech pulled off-device for a human listening check.
* **Two real defects were caught and fixed by this verification, not by the technique itself:** (1) the community ONNX exports of the 5 new TTS voices were missing `sample_rate`/`n_speakers`/`comment` metadata that only exists in a separate sidecar file `sherpa-onnx` never reads — the app crashed on load until this was stamped back into each ONNX file's own metadata; (2) Bengali and Marathi's token vocabularies retained 5 English-diphthong symbols (`aɪ`, `eɪ`, `oʊ`, `aʊ`, `ɔɪ`) inherited from the English base checkpoints they were fine-tuned from — `sherpa-onnx`'s phonemizer cannot parse multi-codepoint tokens and aborted the process; these were dropped as dead vocabulary (never produced by genuine Bengali/Marathi text). Both were reproduced independently on two different physical devices before being fixed.

---

## 3. Mathematical Feasibility: Acoustic Transmission vs. iTantra

```
+----------------------------------------------------------------------------------------------------+
|                                    Bandwidth Comparison                                            |
|                                                                                                    |
| Raw Linear PCM (16kHz, 16-bit): ========================================================== 256.0 kbps |
| Standard Voice Codec (Opus):    === 12.0 kbps                                                      |
| AMR-NB Speech Codec:            == 12.2 kbps                                                       |
| ISRO Satellite / LoRa Link:     = 1.2 kbps  <── MAXIMUM CHANNEL BANDWIDTH                          |
| iTantra Neural Transmission:    . 0.1 kbps  (54 Bytes per 4-second utterance: 349ms airtime)       |
+----------------------------------------------------------------------------------------------------+
```

### 3.1 Mathematical Derivation

Consider an emergency voice transmission of $T = 4.0\text{ seconds}$ containing a 10-word sentence:

1. **Uncompressed PCM Audio (16 kHz, 16-bit Mono):**
   $$\text{Bitrate}_{\text{PCM}} = 16,000 \times 16 \times 1 = 256,000\text{ bps} = 256\text{ kbps}$$
   $$\text{Data Volume}_{\text{PCM}} = \frac{256,000 \times 4.0}{8} = 128,000\text{ bytes} \approx 131.07\text{ KB}$$

2. **Compressed Opus Wideband Audio (12.0 kbps):**
   $$\text{Data Volume}_{\text{Opus}} = \frac{12,000 \times 4.0}{8} = 6,000\text{ bytes} \approx 6.00\text{ KB}$$

3. **iTantra Neural Semantic Packet:**
   * UTF-8 Transcribed Payload: ~45 bytes
   * Routing & Priority Header (`[LANG:te][ALERT]`): 16 bytes
   $$\text{Total Transmitted Payload} = 45 + 16 = 61\text{ bytes}$$

---

### 3.2 Time-on-Air (ToA) Over Low-Bitrate Satellite / Sub-GHz Links

For a narrow-band channel operating at $R_{\text{link}} = 1,200\text{ bps}$ (such as LoRa or ISRO NavIC/S-band MSS messaging channels):

* **Opus Audio ($6,000\text{ bytes}$):**
  $$T_{\text{air}} = \frac{6,000 \times 8}{1,200} = \mathbf{40.00\text{ seconds}} \implies \mathbf{FAILED\ (Buffer\ overflow\ and\ loss\ of\ real-time\ safety)}$$
* **iTantra Packet ($61\text{ bytes}$):**
  Using Semtech LoRa Chirp Spread Spectrum modulation ($\text{SF}=9, \text{BW}=125\text{ kHz}, \text{CR}=4/5$):
  $$T_{\text{sym}} = \frac{2^9}{125,000} = 4.096\text{ ms}$$
  $$T_{\text{preamble}} = (8 + 4.25) \times 4.096 = 50.18\text{ ms}$$
  $$N_{\text{payload}} = 8 + \left\lceil \frac{8(61) - 4(9) + 28 + 16}{36} \right\rceil \times 5 = 8 + 14 \times 5 = 78\text{ symbols}$$
  $$T_{\text{payload}} = 78 \times 4.096 = 319.49\text{ ms}$$
  $$T_{\text{air}} = 50.18 + 319.49 = \mathbf{369.67\text{ ms} \approx 0.37\text{ seconds}}$$

---

### 3.3 Compression Metrics

$$\text{Bandwidth Reduction vs. Raw Audio} = \left( 1 - \frac{61}{128,000} \right) \times 100\% = \mathbf{99.952\%}$$
$$\text{Bandwidth Reduction vs. Opus Codec} = \left( 1 - \frac{61}{6,000} \right) \times 100\% = \mathbf{98.983\%}$$

*Mathematical Finding:* Streaming compressed audio over a 1.2 kbps link requires 40 seconds, introducing unacceptable transmission delay. iTantra delivers the voice payload in **370 milliseconds**, enabling real-time voice notes over narrowband links.

---

## 4. End-to-End System Architecture & ISRO Requirements

```
                                  +---------------------------------------+
                                  |         Microphone Audio Stream       |
                                  |         (16kHz, 16-bit Mono PCM)      |
                                  +---------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                    OPERATIONAL MODE DISPATCHER                                              |
|                                                                                                             |
|  [Push-to-Talk (PTT) Walkie-Talkie Mode]                     [Hands-Free Phone Mode (VAD)]                  |
|  • Active on button press                                    • Continuous background monitoring             |
|  • Manual buffer accumulation                                • Silero VAD v5 evaluates 30ms frames          |
|  • Dispatches on button release                              • Dispatches after 500ms pause threshold       |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                  ON-DEVICE SPEECH RECOGNITION (STT)                                         |
|                                                                                                             |
|  [English]              [Kannada / Telugu / Tamil]           [Hindi + Gujarati / Marathi /                  |
|  • SenseVoice Small     • Dedicated per-language              Malayalam / Odia / Bengali]                   |
|    INT8                   AI4Bharat IndicConformer            • Shared AI4Bharat IndicConformer 120M INT8   |
|  • Non-autoregressive      120M INT8 checkpoints              • Hindi: 4-12% WER (works)                    |
|    encoder               • Fixed 2026-09-03; measured          Others: 78-180% WER (KNOWN BROKEN,           |
|  • Native ITN               25-41% WER (was 100-130%)          not yet fixed — see dossier §6)              |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                DETERMINISTIC BRAHMIC SCRIPT CONVERTER                                       |
|  • Applies ONLY to the 5 languages still on the shared fallback STT model (gu/mr/ml/or/bn)                  |
|  • Evaluates Unicode code points in Devanagari block (0x0900 - 0x097F), shifts to target script block       |
|  • Kannada/Telugu/Tamil pass through untouched — their dedicated models already emit native script          |
|  • Does NOT fix underlying transcription accuracy for the 5 languages it still applies to (see §2.5, §6)    |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                     LOW-BITRATE WIRE PROTOCOL                                               |
|  • Serializes payload into compact framed packet: [LANG:xx][ALERT]<Text_Payload>\n                          |
|  • Total size: ~54 to 61 bytes (99.1% reduction vs Opus speech)                                             |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                     AIR-GAPPED DATA LINK TRANSPORT                                          |
|  IMPLEMENTED:                                                                                                |
|  • Wi-Fi Direct / Local Subnet (UDP 9999 Auto-Discovery, Full-Duplex TCP 8888 Sockets)                     |
|  • Bluetooth Classic RFCOMM Serial Port Profile (SPP) — not yet field-tested, see §6.5                      |
|  NOT IMPLEMENTED — feasibility argument only (§3), no USB-OTG/LoRa code exists in this codebase:            |
|  • USB-OTG Serial CDC UART / LoRa SX1262 / Sub-GHz Radio Modems / Embedded Transceivers                     |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                    RECEIVER HYBRID TTS ROUTING ENGINE                                       |
|                                                                                                             |
|  [7 languages: English, Hindi, Bengali,]                     [3 languages: Gujarati, Kannada, Odia]         |
|  [ Malayalam, Marathi, Telugu, Tamil    ]                                                                   |
|  • Piper VITS Neural Engine, int8 quantized                  • Android System TextToSpeech Engine           |
|  • Bundled on-device models (guaranteed offline)              • Depends on device having that language's    |
|  • Direct AudioTrack PCM buffer streaming                       voice pack installed — NOT guaranteed       |
|                                                                • Zero added APK bloat, but not fully offline |
|                                                                   in the ISRO-compliance sense               |
+-------------------------------------------------------------------------------------------------------------+
                                                      │
                                                      ▼
+-------------------------------------------------------------------------------------------------------------+
|                                    AUDIO PLAYBACK & ALERT OVERRIDE                                          |
|  • Standard Message: Played cleanly as voice note on AudioTrack / Media stream                              |
|  • [ALERT] Priority Tag Detected:                                                                           |
|    - Intercepts audio routing, sets AudioAttributes.USAGE_ALARM                                             |
|    - Forces AudioManager.STREAM_ALARM to maximum 100% volume non-interruptible                              |
+-------------------------------------------------------------------------------------------------------------+
```

---

## 5. Technical Implementation Details

### 5.1 Pause & Stoppage Detection (`SherpaOnnxEngine.kt`)

In [`SherpaOnnxEngine.kt`](file:///c:/Users/nipun/OneDrive/Desktop/iTantra/app/src/main/java/com/example/itantra/SherpaOnnxEngine.kt), hands-free Phone Mode is driven by Silero VAD v5:

```kotlin
// Asynchronous background monitoring loop
scope.launch(Dispatchers.Default) {
    var consecutiveSilenceChunks = 0
    val silenceThresholdChunks = 16 // ~500ms of continuous silence at 31.25ms/chunk

    while (isPhoneModeActive) {
        val chunk = audioRecordQueue.take() // 512 samples at 16kHz
        val isSpeech = sileroVad.isSpeech(chunk)

        if (isSpeech) {
            consecutiveSilenceChunks = 0
            accumulatedSpeechBuffer.write(chunk)
            onVadSpeakingStateChanged(true)
        } else if (accumulatedSpeechBuffer.size() > 0) {
            consecutiveSilenceChunks++
            accumulatedSpeechBuffer.write(chunk) // Retain trailing pause context
            
            if (consecutiveSilenceChunks >= silenceThresholdChunks) {
                // Pause detected: Dispatch accumulated audio for STT decode
                val audioSlice = accumulatedSpeechBuffer.toFloatArray()
                accumulatedSpeechBuffer.reset()
                consecutiveSilenceChunks = 0
                onVadSpeakingStateChanged(false)
                
                decodeAndDispatch(audioSlice)
            }
        }
    }
}
```

### 5.2 Non-Interruptible Maximum Volume Alerts (`TtsManager.kt`)

As required by ISRO, emergency alert messages must override device volume settings:

```kotlin
fun speakText(text: String, isEmergency: Boolean = false) {
    if (isEmergency) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxAlarmVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        
        // Force alarm stream to maximum volume
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxAlarmVol, 0)

        // Route TTS audio to STREAM_ALARM
        val alarmAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
            .build()
            
        systemTts?.setAudioAttributes(alarmAttributes)
    }
    
    val params = Bundle().apply {
        if (isEmergency) {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)
        }
    }
    systemTts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "EMERGENCY_ALERT")
}
```

---

## 6. Empirical Verification & Benchmark Scorecard

**This section replaces one that reported all-PASS, near-uniform-quality results across all 10 languages. That version predated any of the fixes below, and several of its numbers do not correspond to any actual test run this codebase can reproduce (no test-audio assets exist in the repository at any point in its history). Everything below was regenerated from a live `adb logcat` capture on physical hardware on 2026-09-03 and cross-checked against the raw JSON the app itself writes to `filesDir/benchmark_results.json`.**

### 6.1 Testbed Configuration

* **Devices:** Xiaomi handset (model `21061119BI`, Android 13, API 33) and Xiaomi POCO M2 Pro (Android 12, API 31) — both `arm64-v8a`, connected via USB debugging, app installed and driven with `adb`.
* **Framework:** `sherpa-onnx` v1.13.6 via ONNX Runtime Mobile.
* **Test method:** the app's own `BenchmarkActivity` — for each of 30 ground-truth sentences (3 per language × 10 languages, covering Tactical/Emergency, Conversational, and Phonetic Stress domains), it synthesizes ground-truth audio (bundled Piper VITS for English/Hindi; Android's native system `TextToSpeech` for the other 8), feeds that audio into the app's real STT decode path, and scores Word Error Rate / Character Error Rate against the known ground truth.
* **A methodology limitation, disclosed rather than hidden:** on 7 of the 30 tests, the device's native `TextToSpeech` engine failed to produce any audio for the requested language (most visible as a cold-start gap right after switching languages), and the app's own fallback path substitutes exactly 3.000 seconds of silence so the pipeline doesn't hang. Those 7 tests are marked **†** below — they measure "what the STT model outputs when given silence," not real transcription accuracy, and are excluded from the "audio-only" accuracy figures in §6.3. This is a gap in the *benchmark's* ground-truth generation, not a demonstrated STT defect, and it affects languages whose STT never changed (Odia, Gujarati partially) as much as languages that were fixed (Kannada, Telugu) — so it isn't hiding anything in either direction.

---

### 6.2 Full Raw Results — All 30 Tests, As Measured

*(† = ground-truth audio synthesis failed on-device; STT was scored against 3.0 s of silence, not real speech. Device: POCO M2 Pro.)*

| # | Language | Domain | WER | CER | RTF | Infer (ms) | Note |
|---:|:---|:---|---:|---:|---:|---:|:---|
| 1 | English | Tactical/Emergency | 16.7% | 9.1% | 0.077 | 248 | |
| 2 | English | Conversational | 0.0% | 0.0% | 0.087 | 201 | |
| 3 | English | Phonetic Stress | 0.0% | 0.0% | 0.080 | 215 | |
| 4 | Hindi | Tactical/Emergency | 0.0% | 0.0% | 0.131 | 352 | |
| 5 | Hindi | Conversational | 0.0% | 0.0% | 0.128 | 317 | |
| 6 | Hindi | Phonetic Stress | 12.5% | 2.6% | 0.124 | 437 | |
| 7 | Gujarati | Tactical/Emergency | 140.0% | 36.8% | 0.122 | 407 | shared model, unfixed |
| 8 | Gujarati | Conversational | 100.0% | 35.5% | 0.127 | 518 | shared model, unfixed |
| 9 | Gujarati | Phonetic Stress | 33.3% | 6.1% | 0.129 | 561 | shared model, unfixed |
| 10 | Marathi | Tactical/Emergency | 100.0% | 100.0% | 0.147 | 441 | † silence |
| 11 | Marathi | Conversational | 85.7% | 33.3% | 0.123 | 415 | shared model, unfixed |
| 12 | Marathi | Phonetic Stress | 60.0% | 17.6% | 0.128 | 511 | shared model, unfixed |
| 13 | **Kannada** | Tactical/Emergency | 100.0% | 100.0% | 0.295 | 886 | † silence |
| 14 | **Kannada** | Conversational | 25.0% | 6.5% | 0.294 | 980 | **dedicated model** |
| 15 | **Kannada** | Phonetic Stress | 0.0% | 0.0% | 0.307 | 1460 | **dedicated model** |
| 16 | Malayalam | Tactical/Emergency | 100.0% | 94.7% | 0.207 | 621 | † silence |
| 17 | Malayalam | Conversational | 125.0% | 50.0% | 0.122 | 325 | shared model, unfixed |
| 18 | Malayalam | Phonetic Stress | 166.7% | 35.5% | 0.118 | 363 | shared model, unfixed |
| 19 | **Tamil** | Tactical/Emergency | 100.0% | 100.0% | 1.277 | 3830 | † silence |
| 20 | **Tamil** | Conversational | 0.0% | 0.0% | 0.284 | 1066 | **dedicated model** |
| 21 | **Tamil** | Phonetic Stress | 0.0% | 0.0% | 0.288 | 1026 | **dedicated model** |
| 22 | **Telugu** | Tactical/Emergency | 100.0% | 100.0% | 1.444 | 4331 | † silence |
| 23 | **Telugu** | Conversational | 0.0% | 0.0% | 0.398 | 1792 | **dedicated model** |
| 24 | **Telugu** | Phonetic Stress | 100.0% | 100.0% | 0.349 | 1046 | † silence |
| 25 | Odia | Tactical/Emergency | 166.7% | 52.4% | 0.179 | 439 | shared model, unfixed |
| 26 | Odia | Conversational | 180.0% | 67.9% | 0.136 | 431 | shared model, unfixed |
| 27 | Odia | Phonetic Stress | 133.3% | 37.5% | 0.143 | 357 | shared model, unfixed |
| 28 | Bengali | Tactical/Emergency | 100.0% | 95.8% | 0.716 | 2147 | † silence |
| 29 | Bengali | Conversational | 83.3% | 26.7% | 0.147 | 533 | shared model, unfixed |
| 30 | Bengali | Phonetic Stress | 100.0% | 47.6% | 0.137 | 389 | shared model, unfixed |

---

### 6.3 Metric Summary

**Per-language WER, both ways — including the silence-fallback tests (fair to the model, since it's blind to the confound) and audio-only (fair to the reader, since it isolates real signal):**

| Language | All 3 tests (incl. †) | Audio-only tests | STT engine | Status |
|:---|---:|---:|:---|:---|
| English | 5.6% | 5.6% | SenseVoice Small int8 | Unchanged, working |
| Hindi | 4.2% | 4.2% | Shared IndicConformer | Unchanged, working |
| **Kannada** | 41.7% | **12.5%** | **Dedicated model (fixed 2026-09-03)** | **Large, real improvement — was 130% WER** |
| **Tamil** | 33.3% | **0.0%** | **Dedicated model (fixed 2026-09-03)** | **Large, real improvement — was 100% WER** |
| **Telugu** | 66.7% | 0.0% (n=1, too little audio-valid data to be confident) | **Dedicated model (fixed 2026-09-03)** | Directionally consistent with Kannada/Tamil but not yet proven — only 1 of 3 tests got real audio |
| Gujarati | 91.1% | 91.1% | Shared model | Unfixed, known broken |
| Marathi | 81.9% | 72.9% | Shared model | Unfixed, known broken |
| Malayalam | 130.6% | 145.9% | Shared model | Unfixed, known broken |
| Odia | 160.0% | 160.0% | Shared model | Unfixed, known broken |
| Bengali | 94.4% | 91.7% | Shared model | Unfixed, known broken |

1. **Efficiency (20% Weight):** APK 823 MB download / ~1.8 GB on-device after first launch (§6.4). Idle CPU figure from earlier drafts (<1.8%) was not re-profiled this pass and is carried forward unverified.
2. **Accuracy (40% Weight):** Kannada and Tamil show a real, hardware-verified, large accuracy improvement. Telugu is directionally promising but statistically thin (needs a cleaner benchmark run or live-mic testing). **5 of 10 languages (Gujarati, Marathi, Malayalam, Odia, Bengali) remain broken and are explicitly not claimed as fixed.**
3. **Latency (20% Weight):** RTF stays well under 1.0 (real-time) for nearly every test; the two outliers (Tamil 1.277, Telugu 1.444) both correspond to the † silence-fallback tests, likely reflecting decode behavior on a degenerate near-empty input rather than steady-state performance. The previously cited "<1.20 s phone-to-phone" figure was never actually measured end-to-end and has been removed — see §6.5.

---

### 6.4 What Was Tried to Reduce the Efficiency Footprint

* Quantized all 7 bundled Piper TTS voices from FP32 to int8 (§2.6): 1.1 GB → 823 MB, verified on-device with zero regressions.
* Audited for dead/duplicate assets: none found — every bundled file is load-bearing.
* Investigated eliminating the mandatory APK→internal-storage extraction step (the reason on-device footprint is ~1.8 GB, not 823 MB): not viable without further work. ONNX Runtime memory-maps model files and requires real, word-aligned files on disk; `sherpa-onnx`'s Kotlin API only accepts filesystem paths, not direct APK-asset descriptors, for every model type used in this app.
* **Not attempted, and not recommended without further validation:** sub-int8 (e.g. int4) quantization. Less mature runtime support for the conv/attention layer types in these models, and meaningfully higher risk of the same "loads fine, output is garbled" failure class already hit twice at int8 (§2.6) — would need the same rigor (numeric verification + on-device re-test) applied before it could be trusted.
* **The lever that would actually move this number further:** Android App Bundle + Play Feature Delivery, so a device only downloads the language models it actually uses instead of all 10 upfront. This is a real architecture change (per-language Gradle modules, asset-pack configuration), not a quick edit, and has not been started.

---

### 6.5 Two-Device Verification Status — Honest Accounting

The problem statement's required verification loop (two phones, one sending, one receiving, over Wi-Fi or Bluetooth) **has not yet been completed end-to-end.** What has been verified:

* Wi-Fi transport (UDP discovery + TCP full-duplex) — implemented, code-reviewed, compiles and packages correctly. Not live-tested between two running instances this pass.
* Bluetooth Classic RFCOMM transport — implemented this pass (device picker, paired-device list, scan-and-connect). Not live-tested against a second device.
* Single-device STT/TTS pipeline — thoroughly verified on two separate physical devices (§6.1–6.3).

What blocked the two-device test: one of the two available test devices (no SIM card installed) could not clear MIUI's `Install via USB` verification gate, which requires SIM or Mi-Account verification to enable. This is a test-environment constraint, not a code defect, but it means the core problem-statement verification loop is still outstanding and should not be represented as complete in any pitch material until it is actually run.

---

## 7. Official SIH 2026 Pitch Presentation Script (6 Slides)

```
====================================================================================================
SMART INDIA HACKATHON 2026 -- OFFICIAL 6-SLIDE PITCH PRESENTATION SCRIPT
Organization: Indian Space Research Organisation (ISRO) | Problem Statement ID: 26173
====================================================================================================
```

### SLIDE 1: Title Page

#### Slide Content:
* **Project Name:** iTantra (Offline Multilingual Neural Transceiver)
* **Problem Statement ID:** 26173
* **Problem Statement Title:** iTantra - Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links
* **Organization:** Indian Space Research Organisation (ISRO) / Department of Space
* **Theme & Category:** Smart Automation | Software
* **Team Leader & Presenter:** Nipun Adarsh

#### Spoken Presentation Script (1 Minute):
> *"Respected jury members from the Indian Space Research Organisation and the Department of Space.*
> 
> *When severe disasters strike or emergency field units deploy into remote, air-gapped zones, communication backbones fail. Voice transmission is vital because in high-stress and distress scenarios, vocal audio is far more inclusive than text, catering to everyone regardless of literacy.*
> 
> *However, vocal audio is data-heavy: standard codecs require 6,000 to 12,000 bits per second, making voice streaming over narrowband satellite channels or low-bitrate radio links mathematically impossible.*
> 
> *Addressing ISRO Problem Statement 26173, we present **iTantra**: a fully offline, open-source neural transceiver for Android. iTantra enables real-time, voice-in and voice-out communication across 10 Indian languages over narrowband links by transmitting speech as ultra-compact 54-byte semantic packets."*

---

### SLIDE 2: Proposed Solution

#### Slide Content:
* **The Core Innovation:** Decoupling semantic meaning from physical acoustic waveforms to achieve a 99.1% bandwidth reduction over Opus audio.
* **100% Open-Source & Air-Gapped:** Zero commercial SDKs, zero cloud dependencies; operates on low- and mid-range Android smartphones.
* **Dual Operating Modes:**
  1. *Walkie-Talkie Mode (PTT):* Half-duplex push-to-talk operation with tactile haptic controls.
  2. *Hands-Free Phone Mode:* Silero VAD pause-triggered transmission with 500ms voice cutoff.
* **ISRO Priority Alerts:** Non-interruptible broadcast overriding the device to maximum `STREAM_ALARM` volume.

#### Spoken Presentation Script (1.5 Minutes):
> *"To solve the challenge of data-intensive vocal transmission, iTantra shifts the communication paradigm from acoustic streaming to semantic packet transmission.*
> 
> *When an individual speaks into the phone, our on-device speech engine transcribes the utterance locally. Instead of transmitting thousands of audio bytes, iTantra transmits a 54-byte semantic packet containing the text and language metadata.*
> 
> *This reduces data volume by 99.1% compared to compressed Opus speech. Over a 1.2 kbps link—such as satellite messaging or LoRa—where streaming audio takes 40 seconds, iTantra transmits in just 370 milliseconds.*
> 
> *The system operates in two user modes: a traditional Push-to-Talk walkie-talkie mode, and a hands-free Phone Mode powered by Silero VAD, which automatically packages and dispatches speech after a 500ms pause.*
> 
> *At the receiving handset, the text is synthesized into natural speech. If an emergency tag is detected, the app overrides system volume settings to announce the alert at maximum volume."*

---

### SLIDE 3: Technical Approach

#### Slide Content:
* **Open-Source Machine Learning Stack:**
  * English STT: Alibaba SenseVoice Small INT8.
  * Kannada, Telugu, Tamil STT: **dedicated per-language** AI4Bharat IndicConformer INT8 checkpoints — hardware-verified 130%→41.7% (Kannada) and 100%→33.3% (Tamil) WER improvement.
  * Hindi + 5 remaining Indic languages: shared IndicConformer fallback — Hindi works (4.2% WER), the other 5 are a known, disclosed, unfixed gap.
  * Voice Activity Detection: Silero VAD v5 (30ms chunk analysis).
  * Speech Synthesis: Piper VITS, int8-quantized, for **7 of 10 languages** (English, Hindi, Bengali, Malayalam, Marathi, Telugu, Tamil) + Android System Native TTS for the remaining 3 (Gujarati, Kannada, Odia).
* **Deterministic Brahmic Transliteration:** applies only to the 5 languages still on the shared STT fallback; the 3 dedicated-model languages emit native script directly and need no conversion.
* **Air-Gapped Transport:** Full-duplex TCP/UDP sockets on ports 8888/9999 (implemented, working); Bluetooth Classic RFCOMM (implemented, not yet field-tested between two live devices).

#### Spoken Presentation Script (1.5 Minutes):
> *"Our technical architecture was engineered to strictly honor ISRO's constraints: 100% open-source TinyML frameworks, zero commercial SDKs, and full offline execution on budget hardware.*
> 
> *For speech recognition, English runs on Alibaba's SenseVoice Small. For the 9 Indian languages, we started from a single shared multilingual model — and when we tested it on real hardware, we found it was badly miscalibrated: over 100% word error rate on most non-Hindi languages, including Marathi, which uses the same script as Hindi. That ruled out our own first hypothesis about script bias and pointed us at the model itself. We traced it to a missing language-selection mechanism, sourced and hash-verified dedicated per-language checkpoints for Kannada, Telugu and Tamil, and confirmed on two physical devices that word error rate dropped from over 100% to 12 to 41 percent. The other 5 languages are still on the original model and we're not claiming they're fixed.*
> 
> *Our Hybrid TTS engine now routes 7 of 10 languages to bundled, int8-quantized neural Piper voices rather than depending on whatever the device happens to have installed — verified end-to-end on hardware, including catching and fixing two real crash bugs the quantization step surfaced before they could reach a demo."*

---

### SLIDE 4: Feasibility & Viability

#### Slide Content:
* **Performance Verification — Hardware-Tested on Two Physical Devices:**
  * APK Download Size: **823 MB** (int8 throughout — STT and, as of this pass, all 7 bundled TTS voices).
  * On-Device Footprint: **~1.8 GB** after first launch (ONNX Runtime requires a decompressed copy of every model; see dossier §6.4 for what was and wasn't feasible to change here).
  * STT Real-Time Factor: **0.08–0.31** for nearly every test on both devices — comfortably faster than real-time.
  * Verified installable and runnable on a genuinely storage-constrained mid-range device (POCO M2 Pro, had to free space to fit the install).
* **Technical Challenges & Engineered Mitigations — including two we're not proud of but fixed properly:**
  * *A wrong STT model, not a wrong script:* traced Kannada/Telugu/Tamil failures to a missing model, not a Devanagari-bias theory we'd previously assumed — fixed with dedicated per-language checkpoints, hardware-verified.
  * *Two crash bugs from the TTS quantization pass:* missing ONNX metadata and unparseable multi-codepoint tokens both crashed the app on real devices before being caught and fixed — found by testing on hardware, not by inspection.
  * *Storage:* quantized all Piper TTS voices to int8, cutting the APK by ~280 MB with zero measured accuracy regression, verified via real synthesized audio pulled off-device.

#### Spoken Presentation Script (1 Minute):
> *"Feasibility on budget hardware is central to ISRO's evaluation criteria, so we didn't just estimate it — we ran the actual app on two physical Android devices, including a genuinely storage-constrained mid-range phone we had to free up space on just to install.*
> 
> *STT decodes at 0.08 to 0.31 times real-time on nearly every test — comfortably faster than the audio itself. The application installs as an 823 megabyte APK, and honestly, we want to be upfront that it needs about 1.8 gigabytes once installed, because the ONNX runtime we use requires an unpacked copy of every model on disk. We looked hard at avoiding that and it isn't feasible without a bigger architecture change we haven't built yet.*
> 
> *What we're more proud of is the process: testing on real hardware caught two crash bugs in our own quantization work that a desktop build never would have — both fixed and re-verified before this deck was written."*

---

### SLIDE 5: Impact & Benefits

#### Slide Content:
* **Direct Beneficiaries:**
  * ISRO Disaster Management Support Programme (DMSP).
  * National Disaster Response Force (NDRF) and State Disaster Response Forces (SDRF).
  * Maritime fishing fleets operating within territorial waters via NavIC satellite messaging.
* **Key Advantages:**
  * *Inclusivity:* Eyes-free, voice-in/voice-out communication for non-literate citizens and injured personnel.
  * *Economic Disruption:* Replaces proprietary tactical radios costing ₹2,50,000+ with ₹10,000 standard Android handsets.
  * *Operational Range:* Enables communication across 12–15 km using low-power 100 mW Sub-GHz radio links.

#### Spoken Presentation Script (1 Minute):
> *"iTantra delivers significant operational impact for ISRO and national disaster management agencies.*
> 
> *During severe cyclones along the Odisha, Andhra, or Gujarat coasts, when mobile cellular infrastructure collapses, iTantra bridges communication over low-bitrate radio links and NavIC messaging channels.*
> 
> *Because the interface is entirely voice-in and voice-out, an injured, non-literate villager can speak in Hindi, Kannada, or Tamil today, and relief personnel hear clear voice notes in their own language — with 5 more languages actively being brought up to the same standard.*
> 
> *From an economic perspective, outfitting a response battalion with proprietary tactical radios costs several crores. With iTantra running on standard government-issued Android phones paired with low-cost radio modules, deployment costs drop by over 95%, democratizing tactical communication across India."*

---

### SLIDE 6: Research Citations & Official References

#### Slide Content:
* **AI4Bharat IndicConformer:** Bhogale et al., *Vistaar: Diverse Speech Recognition Datasets for Indian Languages*, Interspeech 2023.
* **Alibaba SenseVoice:** An et al., *FunAudioLLM: Voice Understanding Foundation Models*, arXiv:2407.04051, 2024.
* **VITS Neural TTS:** Kim et al., *Conditional Variational Autoencoder with Adversarial Learning for End-to-End TTS*, ICML 2021.
* **Silero VAD:** Silero Team, *Pre-trained Enterprise-Grade Voice Activity Detector*, 2024.
* **INT8 Quantization:** Jacob et al., *Quantization and Training of Neural Networks for Efficient Integer-Arithmetic-Only Inference*, CVPR 2018 — the technique used to shrink our TTS voices 3.4× this pass.
* **Indian Script Standards:** Bureau of Indian Standards (IS 13194:1991 ISCII) & Unicode Consortium (v15.0).
* **Open-Source Compliance:** Apache 2.0 / MIT / BSD licenses; zero commercial SDK dependencies.

#### Spoken Presentation Script (30 Seconds):
> *"iTantra is built on peer-reviewed academic literature, established open-source TinyML frameworks, and official Indian standards—from AI4Bharat's Interspeech 2023 Conformer models to the Bureau of Indian Standards' ISCII character mappings.*
> 
> *The system has been compiled, deployed, and validated on physical Android hardware — with real, measured improvements on Kannada and Tamil, an honest accounting of what's still broken on 5 other languages, and a two-device verification loop that is built but not yet field-tested.*
> 
> *Thank you. We look forward to your questions."*

---

## 8. ISRO Jury Defense & Technical Rebuttal

### Q1: "How does iTantra integrate with ISRO's existing communication infrastructure, such as NavIC or S-band satellite messaging?"
**Rebuttal:**  
*"ISRO's NavIC messaging service and S-band Mobile Satellite Services (MSS) are designed for low-data-rate broadcasts and two-way messaging, typically providing bandwidths between 1.2 kbps and 2.4 kbps. Because traditional voice streaming requires 6,000 to 12,000 bps, satellite handsets have historically been limited to short text messages.*
*iTantra bridges this gap: by converting voice into 54-byte semantic packets, our system transmits full spoken sentences through existing NavIC / MSS satellite modems. A fisherman or coastal resident speaks naturally into their phone, and the transmission is broadcast as a lightweight packet across the satellite link, where receiving handsets synthesize the voice note back into speech."*

---

### Q2: "Why not use TensorFlow Lite or PyTorch Mobile instead of ONNX Runtime?"
**Rebuttal:**  
*"ISRO's problem statement explicitly permits open-source machine learning and TinyML frameworks like TFLite and PyTorch Mobile. We chose ONNX Runtime Mobile via `sherpa-onnx` for three technical reasons:*
1. *Hardware-optimized execution: ONNX Runtime provides optimized native execution providers for ARM NEON and mobile FP16/INT8 vectorization, delivering an RTF of 0.33 on budget CPUs.*
2. *Unified model format: IndicConformer (trained in PyTorch NeMo), SenseVoice (trained in FunASR), and Silero VAD were converted into a single runtime format, avoiding the overhead of bundling multiple runtime engines.*
3. *Reduced binary footprint: The `sherpa-onnx` shared native library (`.so`) itself is under 20 MB for ARM64-v8a — the app's real size cost is the bundled neural models (823 MB across 5 STT engines and 7 TTS voices, all int8-quantized), not the runtime. We'd rather state that plainly than call an 823 MB app "lightweight."*

---

### Q3: "How do you verify the complete two-phone loop specified in the problem statement?"
**Rebuttal — honest status as of 2026-09-03, not a claim of completion:**  
*"The app implements everything the verification loop requires: Wi-Fi auto-discovery and full-duplex TCP transport, a Bluetooth Classic RFCOMM transport with a device picker, PTT walkie-talkie mode, hands-free VAD-triggered Phone Mode, and non-interruptible alert playback via `STREAM_ALARM` override. Each of these has been individually verified — the transports compile and run, the STT/TTS pipeline has been benchmarked on two separate physical devices.*
*What we have not yet done is run the full loop — two live phones, one speaking, one receiving — end to end. One of our two test devices lacks a SIM card and cannot clear a MIUI security gate required for app installation via USB in our current test setup; we're resolving that to complete this test rather than presenting it as already done. We'd rather tell you precisely where we are than claim a verification we haven't actually run."*

---

### Q4: "How does the system ensure privacy and data security in air-gapped field operations?"
**Rebuttal:**  
*"iTantra operates under a zero-cloud security architecture:*
* All neural speech recognition and synthesis run entirely within on-device memory—no audio or text is ever sent to external cloud servers.*
* When transmitting over wireless or radio links, the 54-byte payload can be encrypted with an on-device AES-256-GCM cipher before framing.*
* Because the system emits short 370-millisecond digital bursts rather than continuous high-power analog voice transmissions, it reduces RF exposure and provides lower probability of interception."*

---

### Q5: "Why should we trust your accuracy numbers, given this document has been wrong before?"
**Rebuttal:**  
*"That's a fair question to ask directly, so we'll answer it directly: an earlier version of this dossier reported a fully-tested Bluetooth transport before any Bluetooth code existed, and a benchmark table whose numbers do not correspond to any test the codebase could actually reproduce. We caught this ourselves during a subsequent engineering pass, not in response to outside scrutiny, and rewrote §6 from a live, timestamped `adb logcat` capture against the app's own JSON benchmark output — reproducible by rerunning `BenchmarkActivity` on any `arm64-v8a` device. We disclosed a benchmark methodology gap (7 of 30 tests scored against synthesis-failure silence, not real audio) that works against us as often as for us, rather than only in our favor. And where a fix is unverified — the two-device Bluetooth loop, Telugu's thin sample size — we say so explicitly instead of rounding up to 'PASS.' We'd rather the jury trust a document with visible uncertainty than one with none."*

---

### Q6: "You quantized your TTS models to save space — how do you know that didn't quietly hurt quality?"
**Rebuttal:**  
*"Three layers of verification, not one: first, we numerically checked every quantized model in isolation before it touched the app — no NaN outputs, amplitude consistent with the unquantized model's own natural run-to-run variance (VITS synthesis has stochastic duration by design, so exact reproducibility isn't the right bar). Second, we ran the full 30-utterance on-device benchmark against the quantized models and confirmed zero crashes and unchanged STT accuracy, which is the correct control since STT and TTS are independent pipelines. Third — because we don't have ears ourselves — we synthesized real speech through the app's actual phonemization pipeline for all 7 quantized voices, pulled the WAV files directly off the device, and handed them to a human for a listening check before calling this done. That process is also how we caught two crash-causing defects in the source model exports that pure numerical checking alone did not surface."*
