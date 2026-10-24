package nz.thys.ashley.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.core.Msg
import nz.thys.ashley.core.ToolCall
import nz.thys.ashley.tools.Tool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Any OpenAI-compatible server: llama-server on the PC (host\start-qwen30b.bat), or Ollama's /v1.
 * Reach the PC from the phone over Tailscale or the home Wi-Fi; llama-server must listen on 0.0.0.0.
 */
class OpenAiEngine(override val profile: ModelProfile, http: OkHttpClient) : LlmEngine {

    private val client = http.newBuilder().readTimeout(10, TimeUnit.MINUTES).build()
    private val quick = http.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()

    private val base: String = (profile.baseUrl ?: "http://127.0.0.1:8080/v1").trimEnd('/').let {
        if (profile.provider == "ollama" && !it.endsWith("/v1")) "$it/v1" else it
    }

    /** Up if /v1/models answers, or (older/newer servers) /health on the server root. Any HTTP answer below 500 means it is listening. */
    override suspend fun isReachable(): Boolean = withContext(Dispatchers.IO) {
        val root = base.removeSuffix("/v1")
        listOf("$base/models", "$root/health", "$root/").any { url ->
            runCatching { quick.newCall(Request.Builder().url(url).build()).execute().use { it.code in 200..499 } }.getOrDefault(false)
        }
    }

    override fun notReadyReason(): String = "Can't reach $base. Start the server on the PC and check the address."

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult {
        val body = buildJsonObject {
            put("model", profile.model ?: profile.name)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                messages.forEach { add(toWire(it)) }
            })
            if (tools.isNotEmpty() && profile.supportsTools) put("tools", buildJsonArray {
                tools.forEach { t ->
                    add(buildJsonObject {
                        put("type", "function")
                        put("function", buildJsonObject {
                            put("name", t.name)
                            put("description", t.description)
                            put("parameters", t.schema)
                        })
                    })
                }
            })
            put("temperature", profile.temperature)
            put("max_tokens", profile.maxTokens)
            put("stream", false)
        }
        val started = System.currentTimeMillis()
        val req = Request.Builder().url("$base/chat/completions").post(body.toString().toRequestBody(jsonType)).build()
        val text = client.newCall(req).await().use { resp ->
            val s = withContext(Dispatchers.IO) { resp.body?.string() ?: "" }
            if (!resp.isSuccessful) throw IllegalStateException("Model server error ${resp.code}: ${s.take(300)}")
            s
        }
        val ms = System.currentTimeMillis() - started
        val root = Json.parseToJsonElement(text).jsonObject
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
            ?: throw IllegalStateException("Model server sent no reply.")
        var content = (message["content"] as? JsonPrimitive)?.contentOrNull ?: ""
        // Some servers put hidden reasoning in <think> tags inside the content.
        content = content.replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "").trim()
        var calls = (message["tool_calls"] as? JsonArray)?.mapNotNull { el ->
            val o = el.jsonObject
            val fn = o["function"]?.jsonObject ?: return@mapNotNull null
            val args = fn["arguments"]
            ToolCall(
                id = o["id"]?.jsonPrimitive?.contentOrNull ?: ("call_" + UUID.randomUUID().toString().take(8)),
                name = fn["name"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                argsJson = when (args) {
                    null, is JsonNull -> "{}"
                    is JsonPrimitive -> args.content
                    else -> args.toString()
                },
            )
        } ?: emptyList()
        if (calls.isEmpty() && tools.isNotEmpty()) {
            val (rest, parsed) = ToolCallParser.extract(content, tools.map { it.name }.toSet())
            if (parsed.isNotEmpty()) { content = rest; calls = parsed }
        }
        val usage = root["usage"] as? JsonObject
        return LlmResult(
            text = content,
            toolCalls = calls,
            promptTokens = usage?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
            completionTokens = usage?.get("completion_tokens")?.jsonPrimitive?.intOrNull,
            ms = ms,
        )
    }

    private fun toWire(m: Msg): JsonObject = buildJsonObject {
        put("role", m.role)
        when {
            m.role == "tool" -> {
                put("tool_call_id", m.toolCallId ?: "")
                put("content", m.content)
            }
            m.role == "assistant" && m.toolCalls.isNotEmpty() -> {
                if (m.content.isBlank()) put("content", JsonNull) else put("content", m.content)
                put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { c ->
                        add(buildJsonObject {
                            put("id", c.id)
                            put("type", "function")
                            put("function", buildJsonObject { put("name", c.name); put("arguments", c.argsJson) })
                        })
                    }
                })
            }
            else -> put("content", m.content)
        }
    }
}
