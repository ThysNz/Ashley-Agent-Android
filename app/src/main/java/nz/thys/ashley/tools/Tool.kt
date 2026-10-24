package nz.thys.ashley.tools

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/** A tool the model can call - same shape as ToolBase.cs: name, description, JSON schema, run. */
class Tool(
    val name: String,
    val description: String,
    val schema: JsonObject,
    val run: suspend (JsonObject) -> String,
)

data class Prop(val name: String, val type: String, val description: String, val required: Boolean = false)

object Schema {
    /** Builds {"type":"object","properties":{...},"required":[...]}. */
    fun obj(vararg props: Prop): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            props.forEach { p ->
                put(p.name, buildJsonObject { put("type", p.type); put("description", p.description) })
            }
        })
        put("required", JsonArray(props.filter { it.required }.map { JsonPrimitive(it.name) }))
    }
}

// ---- argument helpers (Args in ToolBase.cs) ----

fun JsonObject.str(key: String): String? {
    val v = this[key] ?: return null
    if (v is JsonNull) return null
    return if (v is JsonPrimitive) v.content else v.toString()
}

fun JsonObject.req(key: String): String = str(key) ?: throw IllegalArgumentException("missing required argument '$key'.")

fun JsonObject.int(key: String): Int? = str(key)?.trim()?.toDoubleOrNull()?.toInt()

fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
    str(key)?.let { it.equals("true", ignoreCase = true) } ?: fallback

/** Keeps every file tool inside the workspace folder. */
class Sandbox(val root: File) {
    private val rootPath: String = root.canonicalPath

    fun resolve(path: String): File {
        val clean = path.trim().ifEmpty { "." }.replace('\\', '/').removePrefix("/workspace/").trimStart('/')
        val f = File(root, clean).canonicalFile
        if (f.path != rootPath && !f.path.startsWith(rootPath + File.separator)) {
            throw IllegalArgumentException("'$path' is outside the workspace.")
        }
        return f
    }

    fun rel(f: File): String = f.relativeTo(root).path.ifEmpty { "." }
}

object TextUtil {
    fun clipMiddle(s: String, head: Int, tail: Int): String =
        if (s.length <= head + tail + 50) s
        else s.take(head) + "\n... [${s.length - head - tail} characters cut] ...\n" + s.takeLast(tail)

    fun firstLine(s: String, max: Int = 120): String {
        val line = s.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: ""
        return if (line.length > max) line.take(max) + "..." else line
    }
}
