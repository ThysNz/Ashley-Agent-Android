package nz.thys.ashley.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nz.thys.ashley.AshleyApp
import nz.thys.ashley.agent.AgentLoop
import nz.thys.ashley.agent.Speakable
import nz.thys.ashley.config.AgentConfig
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.config.ModelsFile
import nz.thys.ashley.config.VoiceConfig
import nz.thys.ashley.core.AgentEvent
import nz.thys.ashley.core.ContextInfo
import nz.thys.ashley.core.InspectInfo
import nz.thys.ashley.core.Msg
import nz.thys.ashley.llm.Engines
import nz.thys.ashley.memory.MemoryStore
import nz.thys.ashley.tools.FileTools
import nz.thys.ashley.tools.MemoryTools
import nz.thys.ashley.tools.Sandbox
import nz.thys.ashley.tools.Tool
import nz.thys.ashley.tools.WebTools
import nz.thys.ashley.voice.AndroidStt
import nz.thys.ashley.voice.AndroidTts
import nz.thys.ashley.voice.SttEngine
import nz.thys.ashley.voice.TtsEngine
import nz.thys.ashley.voice.applyCorrections
import java.io.File

// ---- UI models ----

data class ToolUi(
    val id: String,
    val name: String,
    val what: String,
    val args: String,
    val result: String? = null,
    val failed: Boolean = false,
    val ms: Long? = null,
)

sealed interface ChatItem {
    val key: Long

    data class User(override val key: Long, val text: String, val spoken: Boolean) : ChatItem

    data class Assistant(
        override val key: Long,
        val status: String? = null,
        val notes: List<String> = emptyList(),
        val tools: List<ToolUi> = emptyList(),
        val text: String? = null,
        val meta: String? = null,
        val error: String? = null,
        val spoken: String = "",
    ) : ChatItem
}

data class FileEntry(val name: String, val rel: String, val isDir: Boolean, val size: Long)

/** Everything the chat, panel and settings screens show. One per app, like the desktop's single page. */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val c = (app as AshleyApp).container
    private val store = c.store
    private val paths = c.paths

    // ---- configuration (Settings writes these back to the JSON files) ----
    var agentConfig by mutableStateOf(store.loadAgent())
        private set
    var voiceConfig by mutableStateOf(store.loadVoice())
        private set
    var models by mutableStateOf(store.loadModels().models)
        private set
    private var appState = store.loadState()

    val memory = MemoryStore(paths, agentConfig.maxNotesChars)
    private val sandbox = Sandbox(paths.workspace)
    private val stt: SttEngine = AndroidStt(app)
    private val tts: TtsEngine = AndroidTts(app)

    // ---- chat state ----
    val items = mutableStateListOf<ChatItem>()
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var context by mutableStateOf<ContextInfo?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
        private set

    // ---- pickers ----
    var modelName by mutableStateOf("")
        private set
    var modelReady by mutableStateOf<Boolean?>(null)
        private set
    var modelReason by mutableStateOf("")
        private set
    val modelStatus = mutableStateMapOf<String, Boolean>()
    var personaName by mutableStateOf("")
        private set
    var personas by mutableStateOf(listOf<String>())
        private set
    var voiceName by mutableStateOf("")
        private set
    var voices by mutableStateOf(listOf<String>())
        private set

    // ---- panel ----
    var todo by mutableStateOf(listOf<MemoryStore.TodoItem>())
        private set
    var notes by mutableStateOf(listOf<String>())
        private set
    var dir by mutableStateOf(".")
        private set
    var files by mutableStateOf(listOf<FileEntry>())
        private set

    // ---- voice ----
    var recording by mutableStateOf(false)
        private set
    var level by mutableStateOf(0f)
        private set
    var partial by mutableStateOf("")
        private set
    var recordStart by mutableStateOf(0L)
        private set
    var speakingKey by mutableStateOf<Long?>(null)
        private set

    private val loop: AgentLoop
    private var job: Job? = null
    private var nextKey = 1L
    private fun key() = nextKey++

    val workspacePath: String get() = paths.workspace.absolutePath
    val modelsPath: String get() = paths.models.absolutePath

    init {
        val profile = profileFor(appState.model ?: agentConfig.defaultModel)
        personas = store.personaNames()
        personaName = appState.persona?.takeIf { it in personas } ?: agentConfig.persona
        voiceName = appState.voice ?: voiceConfig.tts.voice
        loop = AgentLoop(
            agentConfig, memory, buildTools(), Engines.create(profile, paths, c.http),
            personaName, store.personaText(personaName),
        )
        modelName = profile.name
        loop.load(memory.loadCurrent())
        rebuildItems(loop.historyView)
        refreshPanels()
        refreshStatus()
        context = loop.contextInfo()
        viewModelScope.launch {
            // The TTS engine binds asynchronously; fetch its voices once it is up.
            var tries = 0
            while (!tts.ready && tries++ < 20) delay(250)
            voices = tts.voices()
        }
    }

    private fun profileFor(name: String): ModelProfile =
        models.firstOrNull { it.name == name } ?: models.firstOrNull() ?: ModelProfile("demo", provider = "fake")

    private fun buildTools(): List<Tool> = buildList {
        val t = agentConfig.tools
        if (t.files) addAll(FileTools.create(sandbox, memory))
        if (t.web) addAll(WebTools.create(c.http))
        if (t.memory) addAll(MemoryTools.create(memory, agentConfig.userName))
    }

    // ---- sending ----

    fun send(textIn: String = input, spoken: Boolean = false) {
        val text = textIn.trim()
        if (text.isEmpty()) return
        if (busy) { showToast("Stop the running task first."); return }
        input = ""
        stopSpeaking()
        items += ChatItem.User(key(), text, spoken)
        val aKey = key()
        items += ChatItem.Assistant(aKey, status = "Thinking...")
        busy = true
        job = viewModelScope.launch {
            try {
                loop.run(text, spoken) { e -> onEvent(aKey, e) }
            } catch (_: CancellationException) {
                // AgentLoop already emitted Stopped
            } finally {
                busy = false
                job = null
                refreshPanels()
                context = loop.contextInfo()
            }
        }
    }

    fun stop() {
        job?.cancel()
        stopSpeaking()
    }

    private fun update(key: Long, f: (ChatItem.Assistant) -> ChatItem.Assistant) {
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0) (items[i] as? ChatItem.Assistant)?.let { items[i] = f(it) }
    }

    private fun onEvent(key: Long, e: AgentEvent) {
        when (e) {
            is AgentEvent.Thinking -> update(key) { it.copy(status = if (e.round > 1) "Working..." else "Thinking...") }
            is AgentEvent.Usage -> context = loop.contextInfo()
            is AgentEvent.Text -> update(key) { it.copy(notes = it.notes + e.text.take(300)) }
            is AgentEvent.ToolStart -> update(key) {
                it.copy(status = "Running ${e.name}...", tools = it.tools + ToolUi(e.id, e.name, e.what, e.args))
            }
            is AgentEvent.ToolEnd -> {
                update(key) { a -> a.copy(tools = a.tools.map { t -> if (t.id == e.id) t.copy(result = e.result, failed = e.failed, ms = e.ms) else t }) }
                refreshPanels()
            }
            is AgentEvent.Final -> {
                val meta = buildList {
                    add(String.format("%.1f s", e.ms / 1000.0))
                    val n = (items.firstOrNull { it.key == key } as? ChatItem.Assistant)?.tools?.size ?: 0
                    if (n > 0) add("$n tool call${if (n == 1) "" else "s"}")
                    e.tokensPerSecond?.let { add(String.format("%.0f tk/s", it)) }
                }.joinToString(" · ")
                val spoken = e.spoken.ifEmpty { Speakable.make(e.text) }
                update(key) { it.copy(status = null, text = e.text, meta = meta, spoken = spoken) }
                if (voiceConfig.speakReplies) speak(key, spoken)
            }
            is AgentEvent.Error -> update(key) { it.copy(status = null, error = e.error) }
            AgentEvent.Stopped -> update(key) { it.copy(status = null, meta = "Stopped") }
        }
    }

    /** Turns a saved conversation back into chat items (tool cards included). */
    private fun rebuildItems(history: List<Msg>) {
        items.clear()
        var current: ChatItem.Assistant? = null
        fun flush() { current?.let { items += it }; current = null }
        for (m in history) when (m.role) {
            "user" -> { flush(); items += ChatItem.User(key(), m.content, m.spoken) }
            "assistant" -> {
                val a = current ?: ChatItem.Assistant(key())
                current = if (m.toolCalls.isNotEmpty()) {
                    a.copy(tools = a.tools + m.toolCalls.map { ToolUi(it.id, it.name, "", it.argsJson) },
                        notes = if (m.content.isNotBlank()) a.notes + m.content.take(300) else a.notes)
                } else a.copy(text = m.content, spoken = Speakable.make(m.content))
                if (m.toolCalls.isEmpty()) flush()
            }
            "tool" -> current = current?.let { a ->
                a.copy(tools = a.tools.map { t -> if (t.id == m.toolCallId) t.copy(result = m.content, failed = m.content.startsWith("Error")) else t })
            }
        }
        flush()
    }

    // ---- voice ----

    fun micDown() {
        stopSpeaking()
        if (recording) return
        recording = true
        partial = ""
        level = 0f
        recordStart = System.currentTimeMillis()
        stt.start(voiceConfig.stt.language, object : SttEngine.Listener {
            override fun onLevel(level: Float) { this@ChatViewModel.level = level }
            override fun onPartial(text: String) { partial = text }
            override fun onResult(text: String) {
                recording = false
                val fixed = applyCorrections(text, voiceConfig.stt.corrections).trim()
                when {
                    fixed.isEmpty() -> showToast("Didn't catch that.")
                    voiceConfig.autoSend && !busy -> send(fixed, spoken = true)
                    else -> input = if (input.isBlank()) fixed else "$input $fixed"
                }
            }
            override fun onError(message: String) {
                recording = false
                showToast(message)
            }
        })
        // Safety net: max recording length from voice.json.
        viewModelScope.launch {
            val started = recordStart
            delay(voiceConfig.stt.maxSeconds * 1000L)
            if (recording && recordStart == started) micUp()
        }
    }

    fun micUp() {
        if (recording) stt.stop()
    }

    fun micCancel() {
        stt.cancel()
        recording = false
    }

    fun speak(key: Long?, text: String) {
        if (text.isBlank()) return
        speakingKey = key ?: -1
        val v = voiceConfig.tts
        tts.speak(text, voiceName, v.speed, v.pitch) { speakingKey = null }
    }

    fun stopSpeaking() {
        if (speakingKey != null) tts.stop()
        speakingKey = null
    }

    fun micPermissionDenied() = showToast("Ashley needs the microphone for hold-to-talk.")

    // ---- pickers ----

    fun selectModel(name: String) {
        if (busy) { showToast("Stop the running task first."); return }
        val p = profileFor(name)
        loop.engine.close()
        loop.engine = Engines.create(p, paths, c.http)
        modelName = p.name
        appState = appState.copy(model = p.name)
        store.saveState(appState)
        context = loop.contextInfo()
        refreshStatus()
    }

    fun selectPersona(name: String) {
        personaName = name
        loop.personaName = name
        loop.personaText = store.personaText(name)
        appState = appState.copy(persona = name)
        store.saveState(appState)
        context = loop.contextInfo()
    }

    fun selectVoice(name: String) {
        voiceName = name
        appState = appState.copy(voice = name)
        store.saveState(appState)
    }

    fun refreshStatus() {
        viewModelScope.launch {
            modelReady = null
            val engine = loop.engine
            val ok = runCatching { engine.isReachable() }.getOrDefault(false)
            modelReady = ok
            modelReason = if (ok) "" else engine.notReadyReason()
        }
    }

    fun checkAllModels() {
        models.forEach { p ->
            viewModelScope.launch {
                modelStatus[p.name] = runCatching { Engines.create(p, paths, c.http).isReachable() }.getOrDefault(false)
            }
        }
    }

    fun reasonFor(p: ModelProfile): String = Engines.create(p, paths, c.http).notReadyReason()

    // ---- settings ----

    fun updateAgent(cfg: AgentConfig) {
        agentConfig = cfg
        store.saveAgent(cfg)
        memory.maxNotesChars = cfg.maxNotesChars
        loop.config = cfg
        loop.tools = buildTools()
        context = loop.contextInfo()
    }

    fun updateVoice(v: VoiceConfig) {
        voiceConfig = v
        store.saveVoice(v)
    }

    fun updateModel(p: ModelProfile) {
        models = models.map { if (it.name == p.name) p else it }
        store.saveModels(ModelsFile(models))
        if (p.name == modelName && !busy) {
            loop.engine = Engines.create(p, paths, c.http)
            context = loop.contextInfo()
            refreshStatus()
        }
    }

    fun addModel(p: ModelProfile): Boolean {
        if (p.name.isBlank() || models.any { it.name == p.name }) { showToast("A model called '${p.name}' already exists."); return false }
        models = models + p
        store.saveModels(ModelsFile(models))
        checkAllModels()
        return true
    }

    fun removeModel(name: String) {
        if (name == modelName) { showToast("Switch to another model first."); return }
        models = models.filterNot { it.name == name }
        store.saveModels(ModelsFile(models))
    }

    fun personaText(name: String) = store.personaText(name)

    fun savePersona(name: String, text: String) {
        store.savePersona(name, text)
        personas = store.personaNames()
        if (name == personaName) selectPersona(name)
    }

    // ---- chats ----

    fun newChat() {
        if (busy) { showToast("Stop the running task first."); return }
        memory.saveCurrent(loop.historyView)
        memory.newChat()
        loop.reset()
        items.clear()
        refreshPanels()
        context = loop.contextInfo()
    }

    fun listChats() = memory.listChats()

    fun openChat(id: String) {
        if (busy) { showToast("Stop the running task first."); return }
        memory.saveCurrent(loop.historyView)
        loop.load(memory.openChat(id))
        rebuildItems(loop.historyView)
        context = loop.contextInfo()
    }

    fun inspect(): InspectInfo = loop.inspect()

    // ---- panel ----

    fun refreshPanels() {
        todo = memory.todoItems()
        notes = memory.noteLines()
        openDir(dir)
    }

    fun openDir(rel: String) {
        val d = runCatching { sandbox.resolve(rel) }.getOrNull()?.takeIf { it.isDirectory } ?: paths.workspace
        dir = sandbox.rel(d)
        files = d.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
            ?.map { FileEntry(it.name, sandbox.rel(it), it.isDirectory, it.length()) } ?: emptyList()
    }

    fun readWorkspaceFile(rel: String): String = runCatching {
        val f = sandbox.resolve(rel)
        if (f.length() > 200_000) f.readText().take(200_000) + "\n... (cut)" else f.readText()
    }.getOrElse { "Can't open: ${it.message}" }

    fun showToast(message: String) {
        toast = message
        viewModelScope.launch {
            delay(3500)
            if (toast == message) toast = null
        }
    }

    override fun onCleared() {
        stt.release()
        tts.release()
        loop.engine.close()
    }
}
