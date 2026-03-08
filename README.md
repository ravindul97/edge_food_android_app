# Edge Food Local Android App

This Android Studio project runs your local GGUF model on-device with a simple two-screen flow:

1. **Profile screen** – save user profile inside the app  
2. **Analyze screen** – enter ingredients, tap **Analyse**, and view the result

## What is included

- Jetpack Compose UI
- Navigation Compose
- Preferences DataStore for local profile saving
- Local RAG JSON assets
- Rule-based validation layer
- JNI bridge for llama.cpp GGUF inference
- Asset copy helper for the local model

## What you need to add before build

### 1. Copy your GGUF model into assets
Place your final model here and rename it exactly:

```text
app/src/main/assets/model/smollm2_food_q4.gguf
```

### 2. Copy llama.cpp source into third_party
This project expects the source tree here:

```text
third_party/llama.cpp
```

You can clone it yourself:

```bash
git clone https://github.com/ggml-org/llama.cpp third_party/llama.cpp
```

## Android Studio setup

Install these from **SDK Manager → SDK Tools**:
- Android NDK
- CMake
- LLDB

Then open this project in Android Studio and let Gradle sync.

## Build notes

- The app builds only for **arm64-v8a**
- The model is loaded from assets to app-private storage on first launch
- The profile is stored locally using Preferences DataStore
- The analysis result is produced fully on-device

## Architecture

- `ProfileRepository` saves the user profile
- `Analyzer` builds the prompt, retrieves local RAG evidence, validates output, and applies safety overrides
- `LlamaBridge` calls the JNI layer
- `native-lib.cpp` loads the GGUF model and performs greedy generation using llama.cpp

## Important note

This project is designed to be simple and local-first. It is a practical starting point for your research project, but you should still test and refine:
- inference speed
- memory usage
- generation quality
- native API compatibility with the exact llama.cpp revision you use

