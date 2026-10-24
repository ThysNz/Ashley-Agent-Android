package nz.thys.ashley.llm

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.OpenApiTool
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.config.Paths
import nz.thys.ashley.core.Msg
import nz.thys.ashley.core.ToolCall
import nz.thys.ashley.tools.Tool
import java.io.File
import java.util.UUID

/** Finds the model file: the path in models.json, or (if that's missing) the only file of that type in the models folder. */
internal fun resolveModelFile(profile: ModelProfile, paths: Paths): File? {
    val named = profile.file?.let { File(paths.root, it) }
    if (named != null && named.isFile) return named
    val ext = named?.extension ?: return null
    val candidates = paths.models.listFiles { f -> f.isFile && f.extension.equals(ext, ignoreCase = true) }.orEmpty()
    return candidates.singleOrNull()
}

/**
 * On-device GGUF models through llama.cpp (JNI) - for Qwen3.5-4B, the "think harder" model. NEXT STEP.
 *
 * Plan:
 *  - llama.cpp as a git submodule under app/src/main/cpp, built with CMake for arm64-v8a, CPU backend
 *    (Qwen3.5's Gated-DeltaNet layers aren't GPU-accelerated on Adreno yet)
 *  - a thin JNI bridge: load(path, nCtx, nThreads), applyChatTemplate(messagesJson, toolsJson, enableThinking),
 *    generate(prompt, onToken) with a cancel flag, free()
 *  - tool calls parsed with ToolCallParser (Qwen writes <tool_call>{...}</tool_call>)
 */
class LlamaCppEngine(override val profile: ModelProfile, private val paths: Paths) : LlmEngine {
    override val isLocal: Boolean get() = true
    override val isLoaded: Boolean get() = false
    override fun modelBytes(): Long = resolveModelFile(profile, paths)?.length() ?: 0

    override suspend fun isReachable(): Boolean = false

    override fun notReadyReason(): String =
        if (resolveModelFile(profile, paths) != null) "The model file is in place; the llama.cpp engine for Qwen is the next build step."
        else "Model file missing: ${profile.file}. Download it in Settings, Models. The llama.cpp engine for Qwen is the next build step."

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult =
        throw IllegalStateException("On-device Qwen (llama.cpp) isn't built in yet. Use Gemma 4 or the PC model for now.")
}

/**
 * Gemma 4 (E4B / E2B) on the phone through Google's LiteRT-LM, GPU by default.
 *
 * LiteRT-LM keeps the conversation inside the engine, while AgentLoop sends the whole history every round.
 * So the engine remembers what it has already been given and only sends what is new (the user message,
 * or the tool results). If the history no longer matches (new chat, trimmed history, persona change) it
 * starts a fresh conversation and passes the earlier turns in as plain text.
 * Tool calls are handled by AgentLoop (automaticToolCalling = false), exactly like the other engines.
 */
class LiteRtEngine(override val profile: ModelProfile, private val paths: Paths) : LlmEngine {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var conversationKey: String? = null
    private var sent: List<Msg> = emptyList()

    /** True when the loaded model accepts photos. */
    private var canSee = false
    private var visionError: String? = null

    override val canSeeImages: Boolean get() = canSee
    override val imageProblem: String
        get() = when {
            engine == null -> "the model isn't loaded yet"
            visionError != null -> "the model loaded without image support ($visionError)"
            else -> "the model loaded without image support"
        }

    override val isLocal: Boolean get() = true
    override val isLoaded: Boolean get() = engine != null
    override fun modelBytes(): Long = resolveModelFile(profile, paths)?.length() ?: 0

    override suspend fun isReachable(): Boolean = resolveModelFile(profile, paths) != null

    override fun notReadyReason(): String =
        "Model file missing: ${profile.file}. Download it in Settings, Models, or copy it into the app's models folder."

    private suspend fun ensureEngine(): Engine {
        engine?.let { return it }
        val file = resolveModelFile(profile, paths) ?: throw IllegalStateException(notReadyReason())
        val cache = File(paths.root, "cache").apply { mkdirs() }
        fun config(vision: Boolean) = EngineConfig(
            modelPath = file.absolutePath,
            // NPU needs the app's native library folder; GPU is the tested path on the S26.
            backend = if (profile.backend == "cpu") Backend.CPU() else Backend.GPU(),
            // Photos: Gemma 4 E4B has a vision encoder. A text-only model file fails here, so we retry without it.
            visionBackend = if (vision) Backend.GPU() else null,
            cacheDir = cache.path,
            // Context window (prompt + reply). Gemma 4 E4B supports up to 32k; contextSize in models.json.
            maxNumTokens = profile.contextSize,
        )
        val e = try {
            Engine(config(vision = true)).also { it.initialize(); canSee = true; visionError = null } // up to ~10 s the first time
        } catch (ex: Throwable) {
            android.util.Log.w("Ashley", "LiteRT-LM: image support failed, loading text-only", ex)
            canSee = false
            visionError = ex.message ?: ex.javaClass.simpleName
            Engine(config(vision = false)).also { it.initialize() }
        }
        engine = e
        return e
    }

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val e = ensureEngine()
        val key = system.hashCode().toString() + "|" + tools.joinToString(",") { it.name }

        val canContinue = conversation != null && conversationKey == key &&
            messages.size > sent.size && messages.subList(0, sent.size) == sent
        val reply: Message = try {
        if (canContinue) {
            val conv = conversation!!
            val fresh = messages.drop(sent.size)
            if (fresh.all { it.role == "tool" }) {
                conv.sendMessage(Message.tool(Contents.of(fresh.map { Content.ToolResponse(it.toolName ?: "tool", wrapResult(it.content)) })))
            } else {
                sendTurn(conv, fresh)
            }
        } else {
            runCatching { conversation?.close() }
            // Earlier turns go in as history; the current turn (last user message onward) is sent now.
            val lastUser = messages.indexOfLast { it.role == "user" }.coerceAtLeast(0)
            val conv = e.createConversation(
                ConversationConfig(
                    systemInstruction = Contents.of(system),
                    initialMessages = history(messages.take(lastUser)),
                    samplerConfig = SamplerConfig(topK = 64, topP = 0.95, temperature = profile.temperature),
                    tools = tools.map { tool(ToolAdapter(it)) },
                    automaticToolCalling = false,
                ),
            )
            conversation = conv
            conversationKey = key
            sendTurn(conv, messages.drop(lastUser))
        }
        } catch (ex: Exception) {
            // Gemma sometimes writes a slightly malformed tool call (e.g. a value without its name) and LiteRT-LM's
            // strict parser throws. Recover the calls from the raw text it includes in the error, and start a fresh
            // conversation next round (the native one may not have kept this turn).
            val recovered = recoverToolCalls(ex, tools) ?: throw ex
            runCatching { conversation?.close() }
            conversation = null
            sent = emptyList()
            android.util.Log.w("Ashley", "LiteRT-LM: recovered ${recovered.toolCalls.size} tool call(s) from a parse error")
            return@withContext recovered.copy(ms = System.currentTimeMillis() - started)
        }

        val calls = reply.toolCalls.map { tc ->
            ToolCall("call_" + UUID.randomUUID().toString().take(8), tc.name, argsToJson(tc.arguments))
        }
        val text = cleanReply(textOf(reply).replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), ""))
        // Remember exactly what AgentLoop will add, so the next round only sends what is new.
        sent = messages + if (calls.isEmpty()) Msg("assistant", text.trim().ifEmpty { "(no reply)" }) else Msg("assistant", text, toolCalls = calls)

        val ms = System.currentTimeMillis() - started
        LlmResult(text, calls, promptTokens = null, completionTokens = (text.length / 4).coerceAtLeast(1), ms = ms)
    }

    /** Sends the current turn: a user message with a photo goes as image + text, everything else as text. */
    private suspend fun sendTurn(conv: Conversation, msgs: List<Msg>): Message {
        val single = msgs.singleOrNull()
        if (single != null && single.role == "user" && single.images.isNotEmpty()) {
            val files = single.images.map { File(paths.workspace, it) }.filter { it.isFile }
            if (canSee && files.isNotEmpty()) {
                android.util.Log.i("Ashley", "LiteRT-LM: sending ${files.size} photo(s): ${files.joinToString { it.name + " " + it.length() + " B" }}")
                val parts = files.map<File, Content> { Content.ImageFile(it.absolutePath) } + Content.Text(single.content)
                return conv.sendMessage(Contents.of(parts))
            }
            // Tell the model plainly, so it doesn't go looking for the file with its tools.
            val why = if (files.isEmpty()) "the photo file is missing" else imageProblem
            return conv.sendMessage(single.content + "\n\n[The user attached a photo, but it could not be shown to you: $why. Say so in one sentence; don't search for the file.]")
        }
        return conv.sendMessage(asText(msgs))
    }

    // ---- recovering from LiteRT-LM tool-call parse errors ----

    private val gemmaCall = Regex("<\\|tool_call>\\s*call:([A-Za-z0-9_]+)\\{(.*?)\\}\\s*<tool_call\\|>", RegexOption.DOT_MATCHES_ALL)

    /** Pulls Gemma's raw output out of the error and parses the tool calls leniently. Null if there is nothing to recover. */
    private fun recoverToolCalls(err: Throwable, tools: List<Tool>): LlmResult? {
        val msg = generateSequence(err) { it.cause }.mapNotNull { it.message }.firstOrNull { "full response:" in it } ?: return null
        val raw = msg.substringAfter("full response:").substringBefore("error: Failed to parse").trim()
        val calls = gemmaCall.findAll(raw).mapNotNull { m ->
            val name = m.groupValues[1]
            val tool = tools.firstOrNull { it.name == name } ?: return@mapNotNull null
            ToolCall("call_" + UUID.randomUUID().toString().take(8), name, parseGemmaArgs(m.groupValues[2], tool).toString())
        }.toList()
        val text = cleanReply(gemmaCall.replace(raw, "").replace("<|\"|>", "\""))
        if (calls.isEmpty() && text.isEmpty()) return null
        return LlmResult(text, calls, completionTokens = (raw.length / 4).coerceAtLeast(1))
    }

    /**
     * Gemma's call syntax: key:<|"|>text<|"|>,key:42,key:true. A value written without its key is given to the
     * next parameter of the tool that hasn't been filled yet (in schema order), which fixes the common slip
     * todo{action:<|"|>add<|"|><|"|>Buy milk<|"|>}.
     */
    private fun parseGemmaArgs(body: String, tool: Tool): JsonObject {
        val order = (tool.schema["properties"] as? JsonObject)?.keys?.toList() ?: emptyList()
        val out = linkedMapOf<String, JsonElement>()
        val token = Regex("(?:([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*)?(?:<\\|\"\\|>(.*?)<\\|\"\\|>|([^,<]+))", RegexOption.DOT_MATCHES_ALL)
        for (m in token.findAll(body)) {
            val quoted = m.groups[2]?.value
            val bare = m.groups[3]?.value?.trim()
            if (quoted == null && bare.isNullOrEmpty()) continue
            val value: JsonElement = when {
                quoted != null -> JsonPrimitive(quoted)
                bare == "true" || bare == "false" -> JsonPrimitive(bare.toBoolean())
                bare!!.toLongOrNull() != null -> JsonPrimitive(bare.toLong())
                bare.toDoubleOrNull() != null -> JsonPrimitive(bare.toDouble())
                else -> JsonPrimitive(bare)
            }
            val key = m.groups[1]?.value?.takeIf { it in order || order.isEmpty() } ?: order.firstOrNull { it !in out } ?: continue
            out[key] = value
        }
        return JsonObject(out)
    }

    /** The text parts of a reply (a Message holds a list of Content items: text, images, tool calls...). */
    private fun textOf(m: Message): String =
        m.contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

    /**
     * Earlier turns as plain user / model messages: only what was said, not the tool traffic. (Writing tool calls
     * into the model's own turns as text taught Gemma to echo "[I used ...]" lines back in its replies.)
     */
    private fun history(msgs: List<Msg>): List<Message> {
        val out = mutableListOf<Message>()
        for (m in msgs) when {
            m.role == "user" -> out += Message.user(m.content)
            m.role == "assistant" && m.toolCalls.isEmpty() && m.content.isNotBlank() -> out += Message.model(m.content)
        }
        return out
    }

    /**
     * The current turn as one user message. If tools already ran for it (after a fresh start mid-task), their
     * results are listed as information from the app, so the model carries on instead of repeating them.
     */
    private fun asText(msgs: List<Msg>): String {
        val ask = msgs.filter { it.role == "user" }.joinToString("\n") { it.content }
        val done = msgs.filter { it.role == "tool" }
        if (done.isEmpty()) return ask
        val results = done.joinToString("\n") { "- ${it.toolName}: ${it.content.lineSequence().firstOrNull { l -> l.isNotBlank() }?.take(200) ?: "done"}" }
        return "$ask\n\n(Note from the app, not from the user: these steps are already done for this request - don't repeat them.\n$results\n" +
            "Continue if something is still missing, otherwise reply to the user in one or two sentences.)"
    }

    /** Removes tool traces a model sometimes copies into its reply ("[I used write_file {...}]", "[todo result: ...]"). */
    private fun cleanReply(text: String): String = text
        .replace(Regex("\\[I used [a-z_]+.*?\\}\\]", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\\[(Earlier in this task you called|[a-z_]+ result):.*?\\]", RegexOption.DOT_MATCHES_ALL), "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun wrapResult(output: String): String = buildJsonObject { put("result", output) }.toString()

    private fun argsToJson(args: Any?): String = when (args) {
        null -> "{}"
        is String -> args.ifBlank { "{}" }
        is Map<*, *> -> toJson(args).toString()
        else -> args.toString()
    }

    private fun toJson(v: Any?): JsonElement = when (v) {
        null -> JsonNull
        is String -> JsonPrimitive(v)
        is Number -> JsonPrimitive(v)
        is Boolean -> JsonPrimitive(v)
        is Map<*, *> -> JsonObject(v.entries.associate { (k, value) -> k.toString() to toJson(value) })
        is Iterable<*> -> JsonArray(v.map { toJson(it) })
        is Array<*> -> JsonArray(v.map { toJson(it) })
        else -> JsonPrimitive(v.toString())
    }

    override fun close() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
        conversation = null
        engine = null
        sent = emptyList()
    }

    /** Describes one of our tools to LiteRT-LM in OpenAPI form. It is never executed by LiteRT: AgentLoop runs it. */
    private class ToolAdapter(private val t: Tool) : OpenApiTool {
        override fun getToolDescriptionJsonString(): String = buildJsonObject {
            put("name", t.name)
            put("description", t.description)
            put("parameters", t.schema)
        }.toString()

        override fun execute(paramsJsonString: String): String = """{"error":"handled by the app"}"""
    }
}
