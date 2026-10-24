package nz.thys.ashley.memory

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nz.thys.ashley.config.Paths
import nz.thys.ashley.core.Msg
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Long-term memory and the session log - a port of Memory.cs. Plain text in the memory folder:
 *   notes.md          what the agent knows (read into every prompt)
 *   todo.md           the agent's task list
 *   sessions/*.jsonl  every message, written as it happens
 *   chats/            current.json + saved chats
 *   backups/          copies of files before they were overwritten or deleted
 */
class MemoryStore(private val paths: Paths, var maxNotesChars: Int) {

    private val notesFile get() = File(paths.memory, "notes.md")
    private val todoFile get() = File(paths.memory, "todo.md")
    private val currentChat get() = File(paths.chats, "current.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    // ---- notes ----

    fun readNotes(): String {
        val text = read(notesFile)
        if (text.length <= maxNotesChars) return text
        // Newest notes are at the bottom, so keep the tail.
        return "[older notes cut off - tidy notes.md]\n" + text.takeLast(maxNotesChars)
    }

    fun noteLines(): List<String> = read(notesFile).lines().filter { it.isNotBlank() }

    fun remember(note: String): String {
        val n = note.trim().replace("\r", "").replace("\n", " ")
        if (n.isEmpty()) return "Error: nothing to remember."
        if (read(notesFile).lines().any { it.contains(n, ignoreCase = true) }) return "Already in my notes."
        paths.memory.mkdirs()
        notesFile.appendText("- $n (${LocalDate.now()})\n")
        return "Saved to notes."
    }

    fun forget(match: String): String {
        val m = match.trim()
        if (m.length < 3) return "Error: give at least 3 characters to match."
        val lines = read(notesFile).lines()
        val kept = lines.filterNot { it.contains(m, ignoreCase = true) }
        val removed = lines.size - kept.size
        if (removed == 0) return "No note contains '$m'."
        notesFile.writeText(kept.joinToString("\n"))
        return "Removed $removed note line${if (removed == 1) "" else "s"}."
    }

    // ---- todo ----

    data class TodoItem(val text: String, val done: Boolean)

    fun todoItems(): List<TodoItem> = read(todoFile).lines().filter { it.isNotBlank() }.map {
        TodoItem(it.drop(3).trim(), it.startsWith("[x]", ignoreCase = true))
    }

    fun todo(action: String, item: String?): String {
        val items = read(todoFile).lines().filter { it.isNotBlank() }.toMutableList()
        fun format(list: List<String>) = list.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
        val result: String
        when (action.lowercase()) {
            "list" -> return if (items.isEmpty()) "The todo list is empty." else format(items)
            "add" -> {
                if (item.isNullOrBlank()) return "Error: 'add' needs an item."
                items.add("[ ] " + item.trim())
                result = "Added."
            }
            "done", "undone", "remove" -> {
                val n = item?.trim()?.toIntOrNull()
                if (n == null || n < 1 || n > items.size) return "Error: give the item number (1-${items.size})."
                if (action.lowercase() == "remove") items.removeAt(n - 1)
                else items[n - 1] = (if (action.lowercase() == "done") "[x] " else "[ ] ") + items[n - 1].drop(3).trimStart()
                result = "Updated."
            }
            "clear" -> { items.clear(); result = "Cleared." }
            else -> return "Error: action must be list, add, done, undone, remove or clear."
        }
        paths.memory.mkdirs()
        todoFile.writeText(items.joinToString("\n") + if (items.isNotEmpty()) "\n" else "")
        return if (items.isEmpty()) "$result The list is now empty." else "$result\n${format(items)}"
    }

    // ---- session log ----

    fun log(m: Msg) {
        try {
            val entry = buildJsonObject {
                put("time", LocalDateTime.now().withNano(0).toString())
                put("role", m.role)
                put("content", m.content)
                m.toolName?.let { put("tool", it) }
                if (m.toolCalls.isNotEmpty()) put("tool_calls", buildJsonArray {
                    m.toolCalls.forEach { c -> add(buildJsonObject { put("Name", c.name); put("ArgsJson", c.argsJson) }) }
                })
            }
            paths.sessions.mkdirs()
            File(paths.sessions, "${LocalDate.now()}.jsonl").appendText(entry.toString() + "\n")
        } catch (_: Exception) {
            // logging must never break a turn
        }
    }

    // ---- backups (before a file tool overwrites or deletes) ----

    fun backup(file: File, workspace: File) {
        if (!file.isFile) return
        val rel = file.relativeTo(workspace).path
        val dest = File(File(paths.backups, LocalDateTime.now().format(stamp)), rel)
        dest.parentFile?.mkdirs()
        file.copyTo(dest, overwrite = true)
    }

    // ---- saved chats ----

    @Serializable
    data class ChatSummary(val id: String, val title: String, val time: Long, val messages: Int)

    fun saveCurrent(history: List<Msg>) {
        paths.chats.mkdirs()
        currentChat.writeText(json.encodeToString(ListSerializer(Msg.serializer()), history))
    }

    fun loadCurrent(): List<Msg> = loadChat(currentChat)

    /** Saves the current chat under a timestamp (if it has anything in it) and starts an empty one. */
    fun newChat() {
        val current = loadCurrent()
        if (current.any { it.role == "user" }) {
            currentChat.copyTo(File(paths.chats, LocalDateTime.now().format(stamp) + ".json"), overwrite = true)
        }
        currentChat.delete()
    }

    fun listChats(): List<ChatSummary> =
        paths.chats.listFiles { f -> f.extension == "json" && f.name != "current.json" }
            ?.mapNotNull { f ->
                val msgs = loadChat(f)
                val first = msgs.firstOrNull { it.role == "user" }?.content ?: return@mapNotNull null
                ChatSummary(f.nameWithoutExtension, first.take(60), f.lastModified(), msgs.count { it.role == "user" || (it.role == "assistant" && it.toolCalls.isEmpty()) })
            }
            ?.sortedByDescending { it.time } ?: emptyList()

    /** Opens a saved chat: the current one is saved first, then the chosen one becomes current. */
    fun openChat(id: String): List<Msg> {
        val file = File(paths.chats, "$id.json")
        val msgs = loadChat(file)
        newChat()
        saveCurrent(msgs)
        file.delete()
        return msgs
    }

    private fun loadChat(file: File): List<Msg> = try {
        if (file.exists()) json.decodeFromString(ListSerializer(Msg.serializer()), file.readText()) else emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    private fun read(file: File): String = try {
        if (file.exists()) file.readText().trim() else ""
    } catch (_: Exception) {
        ""
    }
}
