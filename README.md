# Ashley Mobile (Android-Ashley)

The AI-Agents Ashley agent as a native Android app for the Galaxy S26: same agent loop, tools, memory files,
personas and screen layout as `F:\AI-Agents`, with models and voice running on the phone.

Design doc: *Ashley Mobile — On-Device AI Agent for Android* · UI mock-up: *Ashley Mobile UI* (both in Claude).

## Status: phase 1 (UI shell + agent core)

| Works now | Arrives later |
| --- | --- |
| Chat screen like the web page: status dot, Model / Persona / Voice chips, context meter, tool cards, Copy, Speak | Phase 2: on-device llama.cpp (GGUF, Adreno GPU) |
| Agent loop ported from `AgentLoop.cs` (tool rounds, trimming, voice mode, stop) | Phase 3: skills (`SKILL.md`, `/skill-name`) |
| Tools: list/read/write/append/edit/delete/move/find/search files, web_fetch, web_search, remember, forget, todo | Phase 4: Whisper + Piper via sherpa-onnx, foreground service |
| Memory files: notes.md, todo.md, sessions/*.jsonl, chats/, backups/, state.json | Phase 5: LiteRT-LM (Gemma 4), Termux shell, zip import/export, wake word |
| Hold-to-talk with Android's on-device recogniser; spoken replies with Android TTS | |
| Models: built-in **demo** engine, and your PC's llama-server or Ollama over Wi-Fi / Tailscale | |
| Settings: Agent, Models, Voice, Tools, Inspect, saved chats, Todo / Notes / Workspace drawer | |

## Build and run

1. Install **Android Studio** (Ladybug 2024.2 or newer) on the PC.
2. *File › Open* → `F:\ai-projects\Android-Ashley`. Let Gradle sync (it downloads Gradle 8.11.1, AGP 8.7.3 and the libraries the first time).
   If Studio offers to upgrade AGP or Kotlin, accepting is fine.
3. On the phone: *Settings › About phone › Software information* → tap *Build number* 7 times, then
   *Developer options › Wireless debugging* (or plug in USB and allow USB debugging).
4. Pick the phone in Studio's device list and press **Run**.

Command line instead: `gradlew.bat assembleDebug` → `app\build\outputs\apk\debug\app-debug.apk`.

## First test

- Model chip shows **demo**. Type *what's in my workspace* or *add a todo to test the app* — you'll see the tool card,
  the todo in the drawer (list icon) and the reply spoken if *Speak replies* is ticked.
- Hold the mic, say *add a todo to buy milk*, release.

## Use the PC's model now (before phase 2)

1. On the PC, start llama-server listening on the network: in `F:\AI-Agents\host\start-qwen30b.bat` use `--host 0.0.0.0`
   and allow port 8080 in Windows Firewall (or just run Ollama, port 11434).
2. Phone and PC on the same Wi-Fi, or both on Tailscale.
3. App: *Settings › Models* → pick **pc-qwen3-coder-30b** and set *Server address* to `http://<pc-name-or-ip>:8080/v1`,
   or *Add a model › PC or Ollama server*. The dot turns green when the server answers.

## Where the files are on the phone

`Android/data/nz.thys.ashley/files/` (visible over USB):

```
agents/ashley/config/     agent.json  models.json  voice.json  personas/*.md
agents/ashley/workspace/  the only folder her file tools can touch
agents/ashley/memory/     notes.md  todo.md  state.json  chats/  sessions/  backups/
models/                   GGUF / .litertlm files (phase 2+)
voices/                   Piper voices (phase 4)
skills/                   shared skills (phase 3)
```

Same layout and keys as `F:\AI-Agents\ashley`, so config and memory files can be copied between PC and phone.
Note: files saved from the Settings page are rewritten without the `//` comments.

## Code map

```
app/src/main/java/nz/thys/ashley/
  agent/AgentLoop.kt        port of AgentLoop.cs (+ Speakable: markdown-free spoken text)
  config/Config.kt          agent.json, models.json, voice.json, state.json, folder layout
  memory/MemoryStore.kt     port of Memory.cs + saved chats + backups
  tools/                    FileTools, WebTools, MemoryTools (ports of Tools/*.cs)
  llm/                      LlmEngine, OpenAiEngine (PC), FakeEngine (demo), LlamaCpp/LiteRt stubs
  voice/Voice.kt            SttEngine/TtsEngine + Android implementations
  ui/                       ChatViewModel, chat/ (screen, drawer, sheets, markdown), settings/, theme/
app/src/main/assets/defaults/   default config + personas copied on first run
```
