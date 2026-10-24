# Ashley Mobile (Android-Ashley)

The AI-Agents Ashley agent as a native Android app for the Galaxy S26 Ultra. It has the same agent loop, tools, memory files,
personas and screen layout as `F:\AI-Agents`. The model runs on the phone (Gemma 4) or on the PC (the 30B over Wi-Fi), and voice
works both ways.

- Repo: `github.com/ThysNz/Ashley-Agent-Android`. Edited on the F: PC (`F:\ai-projects\Android-Ashley`), built on the D: PC
  (`D:\ai-projects\Android-Ashley\Ashley-Agent-Android`). Push from one, `git pull` on the other before building.
- Design doc: *Ashley Mobile — On-Device AI Agent for Android*. UI mock-up: *Ashley Mobile UI* (both in Claude).

## Status

| Works now | Next |
| --- | --- |
| Chat screen like the web page: status dot, Model / Persona / Voice chips, context meter, tool cards, Copy, Speak | **Qwen3.5-4B on the phone** (llama.cpp engine, CPU) + a "Think" toggle |
| **Phone strip:** temperature, Android heat level, free RAM, Ashley's RAM, battery | Skills (`SKILL.md`, `/skill-name`) |
| **Phone comes first:** on-device models won't start, and stop between steps, when the phone is too hot or short of RAM | Whisper + Piper voices (sherpa-onnx), background service |
| **Gemma 4 E4B on the phone** (Google LiteRT-LM, GPU) — the default model, with tools | Termux shell, zip import/export, wake word |
| In-app model download with progress, pause and resume (Gemma 4 E4B 3.7 GB, Qwen3.5-4B 2.7 GB) | |
| The PC's 30B (llama-server) or Ollama over Wi-Fi / Tailscale | |
| Agent loop ported from `AgentLoop.cs`; tools: files, **log_note** (say "log this…" → `workspace/notes/log.md`, photo linked), web_fetch, web_search, remember, forget, todo | |
| Memory files: notes.md, todo.md, sessions/*.jsonl, chats/, backups/, state.json | |
| Hold-to-talk (Android recogniser, falls back from on-device to standard); spoken replies (Android TTS) | |
| Settings: Agent, phone protection, Models + downloads, Voice, Tools, Inspect, saved chats, Todo / Notes / Workspace drawer | |
| App icon: an "A" with a voice-wave crossbar (adaptive + themed) | |
| **Photos:** camera button in the composer; the photo goes to Gemma with your typed or spoken comment, is saved in `workspace/photos/`, and `photos/photo-log.md` keeps photo + comment + reply for later review | |

## Models

| Model | Where it runs | Use it for | Notes |
| --- | --- | --- | --- |
| **gemma4-e4b** (default) | Phone GPU (LiteRT-LM) | Everyday questions, notes, voice | 3.7 GB download; about 22 words/s on the S26; cool and light on battery |
| qwen3.5-4b | Phone CPU (llama.cpp) | Trickier, multi-step jobs, design reasoning | 2.7 GB download; **engine not built yet** (next step) |
| pc-qwen3-coder-30b | The F: PC (llama-server) | Heavy jobs at home | Phone does almost nothing; needs the PC setup below |
| demo | Built in | Testing the app with no model | Runs real tools with canned replies |

Pick the model with the **Model** chip. Switching keeps the conversation. On-device models load on the first message (a few
seconds) and are unloaded when you switch away.

## Phone protection

The strip under the context meter shows `°C · heat level · RAM free / total · Ashley's RAM · battery`, updated every 2–3 s. It turns
amber when the phone gets warm and red at the limit.

For **on-device** models only (not the PC model):
- **Won't start** when the phone is over the temperature limit, at Android's thermal level, or when loading the model would leave
  too little free RAM. The chat shows "Not started: …" with the reason.
- **Stops** a running task between steps if the phone gets too hot, and says why.
- Limits: Settings › Agent and tools › **Phone protection** (stored in `agent.json`):

| Setting | Default | Key |
| --- | --- | --- |
| Stop above | 42 °C (battery temperature) | `maxPhoneTempC` |
| Stop at thermal level | Severe (3) — 2 moderate, 4 critical | `stopThermalStatus` |
| Keep free RAM | 0.8 GB | `reserveRamGb` |

## Build and run (D: PC)

1. Android Studio (Ladybug 2024.2 or newer). **File › Open** → `D:\ai-projects\Android-Ashley\Ashley-Agent-Android`.
2. `git pull` first, then **File › Sync Project with Gradle Files**.
3. Phone: Developer options › **USB debugging** (or Wireless debugging), allow the PC.
4. Pick the S26 in the device list, press **Run ▶**.

Versions: Kotlin **2.4.0** (needed by LiteRT-LM, whose newest release is built with 2.4), AGP 8.7.3, Gradle 8.11.1,
compileSdk/targetSdk 35, minSdk 31. `litertlm = "latest.release"` in `gradle/libs.versions.toml`: pin it to the version Studio
resolves once a build works.

## First run

1. Settings (sliders icon) › **Models** › *On-device models* › **Download** next to gemma4-e4b (Wi-Fi; keep the app open).
2. Tap the **Model** chip › **gemma4-e4b**. The first message takes a few extra seconds while it loads.
3. Try *what's in my workspace*, or hold the mic and say *add a todo to test the app*.

## Photos

1. Tap the **camera** next to the mic, take the picture, accept it.
2. "Photo ready" appears above the message box: type or hold the mic and say your comment (or just send: she describes it).
3. Gemma 4 sees the photo; the PC's 30B can't see images, but still gets the file name.
4. Everything is kept for later: the photo in `workspace/photos/IMG_<date>_<time>.jpg` (shrunk to 1600 px, upright) and an entry in
   `workspace/photos/photo-log.md` with the date, your comment and Ashley's reply. Review them in the drawer (list icon › Workspace ›
   photos; tap a photo to open it), or ask *"show me what we said about yesterday's photos"*.

## Use the PC's 30B

Full steps are in `F:\AI-Agents\host\PHONE-ACCESS.md`. In short, on the **F: PC**:

1. Start llama-server with **`--host 0.0.0.0`** (the window ends with `listening on http://0.0.0.0:8080`):
   ```
   F:\Ai-Models\AI-Tools\llama-bin\llama-server.exe ^
     -m F:\Ai-Models\Qwen3-Coder-30B\Qwen3-Coder-30B-A3B-Instruct-Q4_K_M.gguf ^
     --alias qwen3-coder-30b ^
     -ngl 99 --n-cpu-moe 16 -np 1 -c 32768 -fa on -ctk q8_0 -ctv q8_0 ^
     --host 0.0.0.0 --port 8080 --jinja
   ```
2. Home network set to **Private** (Settings › Network & internet › your network › Properties).
3. **Firewall rule** (done 25 Oct 2026). Run in an *administrator* PowerShell:
   ```powershell
   New-NetFirewallRule -DisplayName "llama-server 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow -Profile Private
   ```
   Undo: `Disable-NetFirewallRule -DisplayName "llama-server 8080"` or `Remove-NetFirewallRule -DisplayName "llama-server 8080"`.
4. `ipconfig` → IPv4 address. Phone Chrome: `http://<ip>:8080/health` → `{"status":"ok"}`.
5. App: Settings › Models › **pc-qwen3-coder-30b** › **Use** → **Server address** `http://<ip>:8080/v1`.

The app allows plain `http://` for this (`usesCleartextTraffic` in the manifest). Don't add `--api-key` to llama-server; the app
doesn't send one yet. Away from home, use Tailscale names instead of the LAN IP.

## Where the files are on the phone

`Android/data/nz.thys.ashley/files/` (visible over USB, or `adb push`):

```
agents/ashley/config/     agent.json  models.json  voice.json  personas/*.md
agents/ashley/workspace/  the only folder her file tools can touch
agents/ashley/memory/     notes.md  todo.md  state.json  chats/  sessions/  backups/
models/                   gemma-4-E4B-it.litertlm, Qwen3.5-4B-Q4_K_M.gguf (downloaded in the app)
cache/                    LiteRT-LM cache (faster second load)
voices/  skills/          later phases
```

Same layout and keys as `F:\AI-Agents\ashley`, so config and memory can be copied between PC and phone. Older installs get the
Gemma and Qwen entries added to their `models.json` automatically. Files saved from the Settings page lose their `//` comments.

Copying a model over USB instead of downloading it:
```
adb push gemma-4-E4B-it.litertlm /sdcard/Android/data/nz.thys.ashley/files/models/
```

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Status dot amber, "not ready" (PC model) | Phone Chrome → `http://<ip>:8080/health`. Fails = llama-server not on `0.0.0.0`, firewall rule missing/blocked, or phone not on the same Wi-Fi. See `PHONE-ACCESS.md` |
| "Model file missing" (on-device) | Settings › Models › Download, or copy the file into `models/` |
| "Not started: the phone is …" | Phone too hot or low on RAM. Let it cool, close apps, or use the PC model. Limits in Settings › Agent and tools |
| Mic error | The app falls back from the on-device recogniser automatically. Still failing: Settings › Voice › Language `en-US`; update *Speech Recognition and Synthesis from Google* |
| `compiled with an incompatible version of Kotlin` | Raise `kotlin` in `gradle/libs.versions.toml` to the version in the message |
| Old icon on the home screen | Remove it from the home screen and add it again from the app drawer |
| Long task stops when the screen turns off | Keep the app open until the background service arrives |

## Code map

```
app/src/main/java/nz/thys/ashley/
  agent/AgentLoop.kt        port of AgentLoop.cs (+ phone guard hook) · Speakable.kt (spoken text)
  config/Config.kt          agent.json, models.json, voice.json, state.json, folder layout
  device/PhoneMonitor.kt    temperature, thermal level, RAM, battery; the too-hot / not-enough-RAM rules
  memory/MemoryStore.kt     port of Memory.cs + saved chats + backups
  tools/                    FileTools, WebTools, MemoryTools (ports of Tools/*.cs)
  llm/                      LlmEngine · OnDeviceEngines (LiteRT-LM Gemma, llama.cpp stub) · OpenAiEngine (PC)
                            FakeEngine (demo) · ModelCatalog (+ downloader)
  voice/Voice.kt            Android speech recogniser (session-safe, on-device fallback) and TTS
  device/Photos.kt          shrink / decode photos (upright via ImageDecoder)
  ui/                       ChatViewModel, chat/ (screen, phone strip, drawer, sheets), settings/, theme/
app/src/main/assets/defaults/   default config + personas, copied on first run
app/src/main/res/               app icon (drawable/ic_launcher_*.xml)
```
