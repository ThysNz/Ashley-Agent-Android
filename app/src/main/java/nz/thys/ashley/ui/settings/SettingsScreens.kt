package nz.thys.ashley.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.thys.ashley.Routes
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.ui.ChatViewModel
import nz.thys.ashley.ui.chat.engineLabel
import nz.thys.ashley.ui.components.Dot
import nz.thys.ashley.ui.components.EditRow
import nz.thys.ashley.ui.components.Gap
import nz.thys.ashley.ui.components.Group
import nz.thys.ashley.ui.components.NavRow
import nz.thys.ashley.ui.components.Pill
import nz.thys.ashley.ui.components.RowDivider
import nz.thys.ashley.ui.components.ScreenHeader
import nz.thys.ashley.ui.components.SectionLabel
import nz.thys.ashley.ui.components.Segmented
import nz.thys.ashley.ui.components.SliderRow
import nz.thys.ashley.ui.components.SmallButton
import nz.thys.ashley.ui.components.SwitchRow
import nz.thys.ashley.ui.components.TextDialog
import nz.thys.ashley.ui.components.ValueRow
import nz.thys.ashley.ui.theme.Ashley

/** Shared page frame: header + scrolling column of groups. */
@Composable
private fun Page(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Ashley.colors
    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(title, back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private fun memoryLine(context: Context): String {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return String.format("%.1f GB free of %.1f GB", mi.availMem / 1e9, mi.totalMem / 1e9)
}

// ---- Settings home ----

@Composable
fun SettingsScreen(vm: ChatViewModel, back: () -> Unit, go: (String) -> Unit) {
    val c = Ashley.colors
    val context = LocalContext.current
    Page("Settings", back) {
        Group {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                    Text(vm.agentConfig.name.take(1), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(vm.agentConfig.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text("${vm.modelName} · ${memoryLine(context)}", fontSize = 12.5.sp, color = c.muted)
                }
            }
        }
        Group {
            NavRow(Icons.Outlined.Person, "Agent", "Name, persona, tool rounds, history") { go(Routes.AGENT) }
            RowDivider()
            NavRow(Icons.Outlined.Memory, "Models", "Choose, add, context, temperature") { go(Routes.MODELS) }
            RowDivider()
            NavRow(Icons.Outlined.Mic, "Voice", "Speech-to-text, voices, hold to talk") { go(Routes.VOICE) }
            RowDivider()
            NavRow(Icons.Outlined.Code, "Tools", "Files, web, memory, Termux shell") { go(Routes.AGENT) }
            RowDivider()
            NavRow(Icons.AutoMirrored.Outlined.MenuBook, "Skills", "SKILL.md skills - phase 3") { go(Routes.info("skills")) }
        }
        Group {
            NavRow(Icons.Outlined.Folder, "Memory and data", "Notes, todo, folders") { go(Routes.info("memory")) }
            RowDivider()
            NavRow(Icons.Outlined.Palette, "Appearance", "Follows the phone's theme") { go(Routes.info("appearance")) }
            RowDivider()
            NavRow(Icons.Outlined.Public, "Remote PC", "Use the PC's model over Wi-Fi or Tailscale") { go(Routes.MODELS) }
            RowDivider()
            NavRow(Icons.Outlined.Visibility, "Inspect", "What is sent to the model") { go(Routes.INSPECT) }
            RowDivider()
            NavRow(Icons.Outlined.Info, "About", "Versions, folders, build plan") { go(Routes.info("about")) }
        }
    }
}

// ---- Models ----

@Composable
fun SettingsModelsScreen(vm: ChatViewModel, back: () -> Unit) {
    val c = Ashley.colors
    val context = LocalContext.current
    var adding by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.checkAllModels() }
    val current = vm.models.firstOrNull { it.name == vm.modelName }

    Page("Models", back) {
        Group {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Dot(when (vm.modelReady) { true -> c.ok; false -> c.warn; null -> c.faint })
                    Text(vm.modelName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.text)
                    Text(if (vm.modelReady == true) "in use" else "not ready", fontSize = 12.sp, color = c.muted)
                }
                if (vm.modelReady == false) Text(vm.modelReason, fontSize = 12.5.sp, color = c.warn)
                Text("Phone memory: ${memoryLine(context)}", fontSize = 12.sp, color = c.muted)
            }
        }

        Column {
            SectionLabel("Your models")
            Group {
                vm.models.forEachIndexed { i, m ->
                    if (i > 0) RowDivider()
                    val isCurrent = m.name == vm.modelName
                    ValueRow(
                        title = m.name,
                        value = "",
                        sub = listOf(engineLabel(m.provider), "${m.contextSize / 1024}k ctx").joinToString(" · ") +
                            if (vm.modelStatus[m.name] == false) " · not ready" else "",
                        trailing = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (isCurrent) Pill("in use", c.accentBg, c.accentText)
                                else {
                                    SmallButton("Use") { vm.selectModel(m.name) }
                                    if (m.provider != "fake") SmallButton("Remove") { vm.removeModel(m.name) }
                                }
                            }
                        },
                    )
                }
            }
        }

        if (current != null) ModelSettings(vm, current)

        Column {
            SectionLabel("Add a model")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { SmallButton("PC or Ollama server", primary = true) { adding = "remote" } }
                Box(Modifier.weight(1f)) { SmallButton("On-device file") { adding = "file" } }
            }
            Gap(8)
            Text("On-device model files go in:\n${vm.modelsPath}", fontSize = 12.sp, color = c.muted)
        }
    }

    when (adding) {
        "remote" -> AddRemoteDialog(onDismiss = { adding = null }) { if (vm.addModel(it)) adding = null }
        "file" -> AddFileDialog(onDismiss = { adding = null }) { if (vm.addModel(it)) adding = null }
    }
}

@Composable
private fun ModelSettings(vm: ChatViewModel, m: ModelProfile) {
    Column {
        SectionLabel("${m.name} settings")
        Group {
            ValueRow("Engine", engineLabel(m.provider))
            if (m.provider in setOf("llamacpp", "litert")) {
                RowDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Backend", fontSize = 14.sp, color = Ashley.colors.text)
                    Gap(6)
                    Segmented(listOf("CPU", "GPU", "NPU"), m.backend.uppercase(), { vm.updateModel(m.copy(backend = it.lowercase())) }, Modifier.fillMaxWidth())
                }
                RowDivider()
                EditRow("Model file", m.file ?: "", sub = "Relative to the app folder, e.g. models/x.gguf") { vm.updateModel(m.copy(file = it)) }
                RowDivider()
                SliderRow("Threads", m.threads.toFloat(), 1f..8f, steps = 6, label = m.threads.toString()) { vm.updateModel(m.copy(threads = it.toInt())) }
            }
            if (m.provider in setOf("openai", "ollama")) {
                RowDivider()
                EditRow("Server address", m.baseUrl ?: "", sub = "e.g. http://my-pc:8080/v1 (Tailscale name or LAN IP)") { vm.updateModel(m.copy(baseUrl = it)) }
                RowDivider()
                EditRow("Model id", m.model ?: m.name) { vm.updateModel(m.copy(model = it)) }
            }
            RowDivider()
            SliderRow("Context size", m.contextSize.toFloat(), 2048f..32768f, steps = 14, label = "%,d".format(m.contextSize)) {
                vm.updateModel(m.copy(contextSize = (it / 2048f).toInt().coerceAtLeast(1) * 2048))
            }
            RowDivider()
            SliderRow("Temperature", m.temperature.toFloat(), 0f..1.5f, steps = 14, label = String.format("%.1f", m.temperature)) {
                vm.updateModel(m.copy(temperature = Math.round(it * 10) / 10.0))
            }
            RowDivider()
            EditRow("Max reply tokens", m.maxTokens.toString(), number = true) { s -> s.toIntOrNull()?.let { vm.updateModel(m.copy(maxTokens = it.coerceIn(64, 32768))) } }
            RowDivider()
            SwitchRow("Supports tools", "Off = chat only, like 'no tools' on the desktop", m.supportsTools) { vm.updateModel(m.copy(supportsTools = it)) }
            RowDivider()
            SwitchRow("Thinking", "Hidden reasoning; slower", m.think == true) { vm.updateModel(m.copy(think = it)) }
        }
    }
}

@Composable
private fun AddRemoteDialog(onDismiss: () -> Unit, onAdd: (ModelProfile) -> Unit) {
    var name by remember { mutableStateOf("pc-model") }
    var kind by remember { mutableStateOf("llama-server") }
    var url by remember { mutableStateOf("http://my-pc:8080/v1") }
    var model by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a PC model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Segmented(listOf("llama-server", "Ollama"), kind, {
                    kind = it
                    url = if (it == "Ollama") "http://my-pc:11434" else "http://my-pc:8080/v1"
                }, Modifier.fillMaxWidth())
                Field("Name", name) { name = it }
                Field("Server address", url) { url = it }
                Field("Model id (e.g. qwen3-coder-30b or qwen2.5:7b)", model) { model = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onAdd(
                    ModelProfile(
                        name = name.trim(), description = "Added on the phone", provider = if (kind == "Ollama") "ollama" else "openai",
                        baseUrl = url.trim(), model = model.trim().ifBlank { name.trim() }, contextSize = 16384, maxTokens = 4096,
                    ),
                )
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddFileDialog(onDismiss: () -> Unit, onAdd: (ModelProfile) -> Unit) {
    var name by remember { mutableStateOf("") }
    var file by remember { mutableStateOf("models/") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add an on-device model") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", name) { name = it }
                Field("File (models/name.gguf or .litertlm)", file) { file = it }
                Text("The llama.cpp engine arrives in phase 2; the entry is saved now.", fontSize = 12.sp, color = Ashley.colors.muted)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = file.trim()
                onAdd(ModelProfile(name = name.trim(), provider = if (f.endsWith(".litertlm")) "litert" else "llamacpp", file = f, contextSize = 8192))
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

// ---- Voice ----

@Composable
fun SettingsVoiceScreen(vm: ChatViewModel, back: () -> Unit) {
    val v = vm.voiceConfig
    var pickVoice by remember { mutableStateOf(false) }
    Page("Voice", back) {
        Column {
            SectionLabel("Voice input")
            Group {
                ValueRow("Speech-to-text", "Android (on-device)", sub = "Whisper via sherpa-onnx arrives in phase 4")
                RowDivider()
                EditRow("Language", v.stt.language, sub = "e.g. en-NZ, en-GB, en-US") { vm.updateVoice(v.copy(stt = v.stt.copy(language = it))) }
                RowDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text("Talk mode", fontSize = 14.sp, color = Ashley.colors.text)
                    Gap(6)
                    Segmented(listOf("Hold", "Tap"), v.talkMode, { vm.updateVoice(v.copy(talkMode = it.lowercase())) }, Modifier.fillMaxWidth())
                }
                RowDivider()
                SwitchRow("Send after talking", null, v.autoSend) { vm.updateVoice(v.copy(autoSend = it)) }
                RowDivider()
                SliderRow("Max recording", v.stt.maxSeconds.toFloat(), 5f..60f, steps = 10, label = "${v.stt.maxSeconds} s") {
                    vm.updateVoice(v.copy(stt = v.stt.copy(maxSeconds = it.toInt())))
                }
                RowDivider()
                EditRow(
                    "Name fixes", v.stt.corrections.entries.joinToString(", ") { "${it.key}=${it.value}" },
                    sub = "heard=meant, comma separated",
                ) { s ->
                    val map = s.split(',').mapNotNull { pair ->
                        val parts = pair.split('=')
                        if (parts.size == 2 && parts[0].isNotBlank()) parts[0].trim() to parts[1].trim() else null
                    }.toMap()
                    vm.updateVoice(v.copy(stt = v.stt.copy(corrections = map)))
                }
            }
        }
        Column {
            SectionLabel("Voice output")
            Group {
                SwitchRow("Speak replies", null, v.speakReplies) { vm.updateVoice(v.copy(speakReplies = it)) }
                RowDivider()
                ValueRow("Voice", vm.voiceName.ifBlank { "System default" }, onClick = { pickVoice = true })
                RowDivider()
                SliderRow("Speed", v.tts.speed, 0.5f..2f, steps = 14, label = String.format("%.1fx", v.tts.speed)) {
                    vm.updateVoice(v.copy(tts = v.tts.copy(speed = Math.round(it * 10) / 10f)))
                }
                RowDivider()
                SliderRow("Pitch", v.tts.pitch, 0.5f..2f, steps = 14, label = String.format("%.1f", v.tts.pitch)) {
                    vm.updateVoice(v.copy(tts = v.tts.copy(pitch = Math.round(it * 10) / 10f)))
                }
                RowDivider()
                ValueRow("Play a sample", "", trailing = { SmallButton("Play") { vm.speak(null, "Hello, this is how I sound.") } })
            }
            Gap(6)
            Text("Piper voices (en_US-hfc_female-medium and the rest of the desktop set) arrive with sherpa-onnx in phase 4.", fontSize = 12.sp, color = Ashley.colors.muted)
        }
    }
    if (pickVoice) {
        AlertDialog(
            onDismissRequest = { pickVoice = false },
            title = { Text("Voice") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    (listOf("") + vm.voices).forEach { name ->
                        Text(
                            name.ifBlank { "System default" },
                            fontWeight = if (name == vm.voiceName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                                .clickable { vm.selectVoice(name); vm.speak(null, "Hello, this is how I sound.") }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickVoice = false }) { Text("Done") } },
        )
    }
}
