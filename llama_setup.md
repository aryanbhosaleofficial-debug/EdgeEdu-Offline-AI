# Enabling on-device AI (`-PenableLlama`)

This turns on the part that makes the app **actually think** — running the downloaded
Qwen GGUF through llama.cpp on the phone. Without this, the model download (Settings →
AI tutor) just stores a file that never runs, and the app stays on the verified
maths solver + textbook.

Everything in code is already done: the JNI bridge
([android/app/src/main/cpp/llama_jni.cpp](android/app/src/main/cpp/llama_jni.cpp)),
the CMake build ([android/app/src/main/cpp/CMakeLists.txt](android/app/src/main/cpp/CMakeLists.txt)),
and the gradle wiring. **Three things are missing, and they're all on your build machine.**

---

## Prerequisites

1. **Android NDK + CMake** — in Android Studio: **Tools → SDK Manager → SDK Tools**,
   tick **NDK (Side by side)** and **CMake**, Apply. (The build pins CMake 3.22.1.)
2. **An arm64-v8a phone** with headroom — the 3B Q4 model needs **~2.5–3 GB free RAM**
   at runtime. Only `arm64-v8a` is built (see `abiFilters` in build.gradle.kts), so an
   x86 emulator won't run it — use a real phone.

---

## Step 1 — Clone llama.cpp into the project

The build expects the source at `android/app/src/main/cpp/llama.cpp/` (already
git-ignored, so it won't bloat your repo). Run from the project root:

```bash
git clone --depth 1 https://github.com/ggml-org/llama.cpp android/app/src/main/cpp/llama.cpp
```

> **If the build later fails with unknown-symbol/renamed-function errors**, the
> llama.cpp API drifted from what the JNI expects (it uses `llama_model_load_from_file`,
> `llama_init_from_model`, `common_sampler_*`, `llama_batch_get_one(tokens, n)` —
> the early-2025 API). Pin a compatible release instead of `master`:
> ```bash
> cd android/app/src/main/cpp/llama.cpp
> git fetch --depth 1 origin b4700 && git checkout b4700   # try a tag from ~Feb 2025
> ```
> Adjust the JNI bridge if a symbol was renamed again — it's a thin file.

---

## Step 2 — Build with the flag

Command line:
```bash
cd android
./gradlew assembleDebug -PenableLlama
```

Or in Android Studio, add to `android/gradle.properties`:
```
enableLlama=true
```
then **Build → Build APK(s)**. First native build is slow (it compiles ggml + llama +
common). The APK gets ~10–20 MB bigger (the native lib); the **model is still NOT
bundled** — it downloads separately.

---

## Step 3 — Run it

1. Install the `-PenableLlama` APK on an **arm64 phone**.
2. Open **Settings → AI tutor → Download AI model (2 GB)** (on WiFi).
3. Once it shows **✓ AI model installed**, open a subject and ask anything —
   the AI now reasons with its own brain, step by step, with the maths engine still
   verifying every calculation.

If the build does **not** include `-PenableLlama`, Settings will show
*"this build can't run the model yet"* even after the download — that's expected.

---

## How the pieces fit

| Piece | Done by | Needed for AI to think |
| --- | --- | --- |
| JNI bridge + CMake + gradle wiring | ✅ in repo | yes |
| Model download (Settings, ~2 GB) | ✅ in app | yes |
| `git clone llama.cpp` (Step 1) | **you** | yes |
| NDK + CMake installed | **you** | yes |
| Build with `-PenableLlama` (Step 2) | **you** | yes |

All five must be true. Miss any one and the app falls back to the mock engine — the
verified step-by-step maths solver still works, but open-ended reasoning won't.

---

## Caveats (real-device reality)

- **RAM:** 3B Q4 uses ~2.5–3 GB at runtime. Budget phones (≤4 GB total) may be killed
  by the OS mid-inference. A smaller model (e.g. Qwen2.5-1.5B) is gentler — override
  with `-PmodelFile=qwen2.5-1.5b-instruct-q4_k_m.gguf -PmodelUrl=…`.
- **Speed:** CPU-only inference. Expect a few tokens/sec on a mid-range phone; the
  first response is the slowest (prompt processing).
- **Storage:** the GGUF lives in app-private storage (`filesDir/models/`); logging out
  does **not** delete it (only content is cleared).
