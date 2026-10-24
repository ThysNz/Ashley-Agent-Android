package nz.thys.ashley.config

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

// ---- config file shapes (same keys as F:\AI-Agents\<agent>\config, plus phone additions) ----

@Serializable
data class ToolSwitches(
    val files: Boolean = true,
    val web: Boolean = true,
    val memory: Boolean = true,
    val skills: Boolean = true,
    val termux: Boolean = false,
    val git: Boolean = false,
)

@Serializable
data class AgentConfig(
    val name: String = "Ashley",
    val userName: String = "Thys",
    val persona: String = "ashley",
    val defaultModel: String = "demo",
    val maxToolRounds: Int = 80,
    val historyTurns: Int = 12,
    val commandTimeoutSeconds: Int = 120,
    val maxNotesChars: Int = 8000,
    val gitName: String = "Ashley",
    val gitEmail: String = "ashley@ai-agents.local",
    val tools: ToolSwitches = ToolSwitches(),
)

@Serializable
data class ModelProfile(
    val name: String,
    val description: String = "",
    /** fake | llamacpp | litert | openai | ollama */
    val provider: String = "fake",
    val baseUrl: String? = null,
    val model: String? = null,
    /** On-device model file, relative to the app's root folder (e.g. models/x.gguf). */
    val file: String? = null,
    /** cpu | gpu | npu */
    val backend: String = "gpu",
    val contextSize: Int = 8192,
    val temperature: Double = 0.3,
    val maxTokens: Int = 2048,
    val threads: Int = 6,
    val supportsTools: Boolean = true,
    val think: Boolean? = null,
    val launch: String? = null,
)

@Serializable
data class ModelsFile(val models: List<ModelProfile> = emptyList())

@Serializable
data class SttConfig(
    val engine: String = "android",
    val language: String = "en-NZ",
    val maxSeconds: Int = 30,
    val corrections: Map<String, String> = emptyMap(),
)

@Serializable
data class TtsConfig(
    val engine: String = "android",
    val voice: String = "",
    val speed: Float = 1.0f,
    val pitch: Float = 1.0f,
)

@Serializable
data class VoiceConfig(
    val stt: SttConfig = SttConfig(),
    val tts: TtsConfig = TtsConfig(),
    /** hold | tap */
    val talkMode: String = "hold",
    val autoSend: Boolean = true,
    val speakReplies: Boolean = true,
)

/** memory/state.json - same capitalised keys as the desktop. */
@Serializable
data class AppState(
    @SerialName("Model") val model: String? = null,
    @SerialName("Persona") val persona: String? = null,
    @SerialName("Voice") val voice: String? = null,
)

// ---- folder layout (mirrors F:\AI-Agents so an agent folder can move between PC and phone) ----

class Paths(val root: File, agent: String) {
    val agentDir = File(root, "agents/$agent")
    val config = File(agentDir, "config")
    val personas = File(config, "personas")
    val workspace = File(agentDir, "workspace")
    val memory = File(agentDir, "memory")
    val chats = File(memory, "chats")
    val sessions = File(memory, "sessions")
    val backups = File(memory, "backups")
    val agentSkills = File(agentDir, "skills")
    val sharedSkills = File(root, "skills")
    val models = File(root, "models")
    val voices = File(root, "voices")

    fun ensure() {
        listOf(config, personas, workspace, memory, chats, sessions, backups, agentSkills, sharedSkills, models, voices)
            .forEach { it.mkdirs() }
    }
}

// ---- reading and writing the JSON files ----

val ConfigJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    encodeDefaults = true
}

/** Removes // and /* */ comments outside strings, so the desktop's commented JSON files load. */
fun stripJsonComments(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    var inString = false
    while (i < text.length) {
        val c = text[i]
        if (inString) {
            out.append(c)
            if (c == '\\' && i + 1 < text.length) { out.append(text[i + 1]); i += 2; continue }
            if (c == '"') inString = false
            i++
            continue
        }
        when {
            c == '"' -> { inString = true; out.append(c); i++ }
            c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                while (i < text.length && text[i] != '\n') i++
            }
            c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                i += 2
                while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                i += 2
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}

class ConfigStore(private val context: Context, val paths: Paths) {

    /** Copies assets/defaults (agent.json, models.json, voice.json, personas) on first run. Never overwrites. */
    fun installDefaults() {
        paths.ensure()
        copyAssetDir("defaults", paths.config)
    }

    private fun copyAssetDir(assetDir: String, target: File) {
        val names = context.assets.list(assetDir) ?: return
        target.mkdirs()
        for (name in names) {
            val child = "$assetDir/$name"
            val sub = context.assets.list(child)
            if (!sub.isNullOrEmpty()) {
                copyAssetDir(child, File(target, name))
            } else {
                val dest = File(target, name)
                if (!dest.exists()) context.assets.open(child).use { input -> dest.outputStream().use { input.copyTo(it) } }
            }
        }
    }

    private fun <T> read(file: File, serializer: KSerializer<T>, fallback: T): T = try {
        if (file.exists()) ConfigJson.decodeFromString(serializer, stripJsonComments(file.readText())) else fallback
    } catch (e: Exception) {
        fallback
    }

    private fun <T> write(file: File, serializer: KSerializer<T>, value: T) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(ConfigJson.encodeToString(serializer, value))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }

    fun loadAgent() = read(File(paths.config, "agent.json"), AgentConfig.serializer(), AgentConfig())
    fun saveAgent(v: AgentConfig) = write(File(paths.config, "agent.json"), AgentConfig.serializer(), v)

    fun loadModels() = read(File(paths.config, "models.json"), ModelsFile.serializer(), ModelsFile())
    fun saveModels(v: ModelsFile) = write(File(paths.config, "models.json"), ModelsFile.serializer(), v)

    fun loadVoice() = read(File(paths.config, "voice.json"), VoiceConfig.serializer(), VoiceConfig())
    fun saveVoice(v: VoiceConfig) = write(File(paths.config, "voice.json"), VoiceConfig.serializer(), v)

    fun loadState() = read(File(paths.memory, "state.json"), AppState.serializer(), AppState())
    fun saveState(v: AppState) = write(File(paths.memory, "state.json"), AppState.serializer(), v)

    fun personaNames(): List<String> =
        paths.personas.listFiles { f -> f.isFile && f.extension == "md" }?.map { it.nameWithoutExtension }?.sorted() ?: emptyList()

    fun personaText(name: String): String =
        File(paths.personas, "$name.md").takeIf { it.exists() }?.readText()?.trim() ?: ""

    fun savePersona(name: String, text: String) = File(paths.personas, "$name.md").writeText(text.trim() + "\n")
}
