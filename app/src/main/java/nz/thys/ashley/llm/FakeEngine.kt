package nz.thys.ashley.llm

import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.thys.ashley.config.ModelProfile
import nz.thys.ashley.core.Msg
import nz.thys.ashley.core.ToolCall
import nz.thys.ashley.tools.Tool
import java.util.UUID

/**
 * Built-in demo engine (model "demo"). It runs real tools so the whole loop can be tested on the
 * phone before a model is installed:
 *   "...todo <text>"          -> todo add
 *   "...workspace" / "files"  -> list_files
 *   "remember <fact>"         -> remember
 *   anything else             -> a short canned reply
 */
class FakeEngine(override val profile: ModelProfile) : LlmEngine {

    override suspend fun chat(system: String, messages: List<Msg>, tools: List<Tool>): LlmResult {
        delay(450)
        val names = tools.map { it.name }.toSet()
        val last = messages.lastOrNull()
        val prompt = system.length + messages.sumOf { it.content.length }

        if (last?.role == "tool") {
            val first = last.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "done"
            val count = last.content.lines().count { it.isNotBlank() }
            val reply = when (last.toolName) {
                "list_files" -> if (last.content.startsWith("(empty")) "Your workspace is empty for now. Copy files into the app's workspace folder and I'll see them."
                    else "There ${if (count == 1) "is one entry" else "are $count entries"} in the workspace, starting with $first."
                "todo" -> "Done, it's on your todo list."
                "remember" -> "Got it, I've saved that to my notes."
                else -> "Done: $first"
            }
            return result(reply, prompt)
        }

        if (last?.role == "user" && last.images.isNotEmpty()) {
            return result("I've saved your photo as ${last.images.first()}. The demo engine can't see it; switch to Gemma 4 to talk about it.", prompt)
        }
        val text = last?.content?.trim() ?: ""
        val lower = text.lowercase()
        fun call(name: String, args: String) =
            LlmResult("", listOf(ToolCall("call_" + UUID.randomUUID().toString().take(8), name, args)), prompt / 3, 12, 300)

        return when {
            "todo" in lower && "todo" in names -> {
                val item = text.substringAfter("todo", "").trim(' ', ':', '.', ',').removePrefix("to ").ifBlank { "Follow up" }
                call("todo", buildJsonObject { put("action", "add"); put("item", item.replaceFirstChar { it.uppercase() }) }.toString())
            }
            lower.startsWith("remember") && "remember" in names ->
                call("remember", buildJsonObject { put("note", text.substringAfter(" ", text).trim()) }.toString())
            ("workspace" in lower || "files" in lower) && "list_files" in names ->
                call("list_files", buildJsonObject { put("path", ".") }.toString())
            else -> result(
                "This is the demo engine, so I can't really think yet. Load a model in Settings, Models - " +
                    "or try \"what's in my workspace\" or \"add a todo to test the app\" to see the tools work.",
                prompt,
            )
        }
    }

    private fun result(text: String, promptChars: Int) =
        LlmResult(text, emptyList(), promptTokens = promptChars / 3, completionTokens = text.length / 4, ms = 400)
}
