package nz.thys.ashley.core

import kotlinx.serialization.Serializable

/** One tool call requested by the model. [argsJson] is the raw JSON object string. */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val argsJson: String,
)

/**
 * One message in the conversation, in OpenAI chat format.
 * role = "user" | "assistant" | "tool"  (the system prompt is built fresh for every call).
 */
@Serializable
data class Msg(
    val role: String,
    val content: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** True when the user message came from the microphone (the reply is spoken). */
    val spoken: Boolean = false,
)

/** Live progress of one turn - the same events the desktop web page gets from /chat/stream. */
sealed interface AgentEvent {
    data class Thinking(val round: Int) : AgentEvent
    data class Usage(val tokens: Int, val window: Int) : AgentEvent
    data class Text(val text: String) : AgentEvent
    data class ToolStart(val id: String, val name: String, val what: String, val args: String) : AgentEvent
    data class ToolEnd(val id: String, val result: String, val failed: Boolean, val ms: Long) : AgentEvent
    data class Final(val text: String, val spoken: String, val ms: Long, val tokensPerSecond: Double?) : AgentEvent
    data class Error(val error: String) : AgentEvent
    data object Stopped : AgentEvent
}

/** Token split shown by the context meter (system / tools / notes / chat). */
data class ContextInfo(
    val system: Int,
    val tools: Int,
    val notes: Int,
    val history: Int,
    val window: Int,
) {
    val used: Int get() = system + tools + notes + history
}

/** What "Inspect" shows: exactly what goes to the model on the next call. */
data class InspectInfo(
    val systemPrompt: String,
    val tools: List<String>,
    val messages: List<Msg>,
    val context: ContextInfo,
)
