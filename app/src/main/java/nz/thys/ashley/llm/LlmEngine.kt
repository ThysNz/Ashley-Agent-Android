package nz.thys.ashley.llm

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.config.Paths
import nz.thys.ashley.core.Msg
import nz.thys.ashley.core.ToolCall
import nz.thys.ashley.tools.Tool
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One model reply: text, or tool calls, plus token counts when the engine reports them. */
data class LlmResult(
    val text: String,
    val toolCalls: List<ToolCall>,
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val ms: Long = 0,
)

/** Same role as LlmClient.cs: the agent loop never knows which engine answers. */
interface LlmEngine {
    val profile: ModelProfile

    suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult

    /** Used for the status dot and the Models screen. */
    suspend fun isReachable(): Boolean = true

    /** Why it isn't reachable, in words for the user. */
    fun notReadyReason(): String = profile.launch ?: "Not available."

    fun close() {}
}

object Engines {
    fun create(profile: ModelProfile, paths: Paths, http: OkHttpClient): LlmEngine = when (profile.provider) {
        "fake" -> FakeEngine(profile)
        "openai", "ollama" -> OpenAiEngine(profile, http)
        "llamacpp" -> LlamaCppEngine(profile, paths)
        "litert" -> LiteRtEngine(profile, paths)
        else -> FakeEngine(profile.copy(description = "Unknown provider '${profile.provider}' - using the demo engine"))
    }
}

/**
 * Small local models sometimes write tool calls as text instead of structured calls, e.g.
 * <tool_call>{"name": "list_files", "arguments": {"path": "."}}</tool_call>  (Qwen / Hermes style).
 * Used by every engine as a fallback.
 */
object ToolCallParser {
    private val tagged = Regex("<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>", RegexOption.DOT_MATCHES_ALL)

    fun extract(text: String, known: Set<String>): Pair<String, List<ToolCall>> {
        val calls = mutableListOf<ToolCall>()
        for (m in tagged.findAll(text)) {
            try {
                val obj = Json.parseToJsonElement(m.groupValues[1]).jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: continue
                if (name !in known) continue
                val args = obj["arguments"] ?: obj["parameters"] ?: JsonObject(emptyMap())
                val argsJson = if (args is JsonObject) args.toString() else args.jsonPrimitive.content
                calls += ToolCall("call_" + UUID.randomUUID().toString().take(8), name, argsJson)
            } catch (_: Exception) {
                // not a valid call - leave the text as it is
            }
        }
        if (calls.isEmpty()) return text to emptyList()
        return tagged.replace(text, "").trim() to calls
    }
}

/** OkHttp call as a cancellable suspend function: stopping a task cancels the request. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response)
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
