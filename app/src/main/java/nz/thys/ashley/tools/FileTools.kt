package nz.thys.ashley.tools

import nz.thys.ashley.memory.MemoryStore
import java.io.File
import java.nio.file.FileSystems

/** Port of Tools/FileTools.cs - every path is relative to, and confined to, the workspace. */
object FileTools {

    private val ignored = setOf(".git", "node_modules", "__pycache__", ".venv", "venv", "bin", "obj")
    private const val MAX_LIST = 500
    private const val MAX_MATCHES = 200

    fun create(sb: Sandbox, memory: MemoryStore): List<Tool> = listOf(
        Tool(
            "list_files",
            "List files and folders in the workspace (or a subfolder). Paths are relative to the workspace.",
            Schema.obj(
                Prop("path", "string", "Subfolder to list. Default '.'"),
                Prop("recursive", "boolean", "List subfolders too (up to 4 levels deep). Default false."),
            ),
        ) { a -> listFiles(sb, a.str("path") ?: ".", a.bool("recursive")) },

        Tool(
            "read_file",
            "Read a text file from the workspace. Output has line numbers (not part of the file). Use start_line/max_lines for big files.",
            Schema.obj(
                Prop("path", "string", "File path relative to the workspace.", true),
                Prop("start_line", "integer", "First line to show (1-based). Default 1."),
                Prop("max_lines", "integer", "How many lines to show. Default 2000."),
            ),
        ) { a -> readFile(sb, a.req("path"), a.int("start_line") ?: 1, a.int("max_lines") ?: 2000) },

        Tool(
            "write_file",
            "Create a file, or replace its entire contents. Creates parent folders. The old version is backed up.",
            Schema.obj(
                Prop("path", "string", "File path relative to the workspace.", true),
                Prop("content", "string", "The complete new contents.", true),
            ),
        ) { a -> writeFile(sb, memory, a.req("path"), a.req("content"), append = false) },

        Tool(
            "append_file",
            "Add text to the end of a file (creates it if missing).",
            Schema.obj(
                Prop("path", "string", "File path relative to the workspace.", true),
                Prop("content", "string", "Text to add.", true),
            ),
        ) { a -> writeFile(sb, memory, a.req("path"), a.req("content"), append = true) },

        Tool(
            "edit_file",
            "Replace exact text in a file. old_text must match exactly once (include enough surrounding lines), unless replace_all is true. Prefer this over write_file for small changes.",
            Schema.obj(
                Prop("path", "string", "File path relative to the workspace.", true),
                Prop("old_text", "string", "Exact text to find.", true),
                Prop("new_text", "string", "Replacement text.", true),
                Prop("replace_all", "boolean", "Replace every occurrence. Default false."),
            ),
        ) { a -> editFile(sb, memory, a.req("path"), a.req("old_text"), a.str("new_text") ?: "", a.bool("replace_all")) },

        Tool(
            "delete_file",
            "Delete a file, or a folder with recursive=true. A file is backed up first; a folder is not.",
            Schema.obj(
                Prop("path", "string", "Path relative to the workspace.", true),
                Prop("recursive", "boolean", "Required to delete a folder."),
            ),
        ) { a -> delete(sb, memory, a.req("path"), a.bool("recursive")) },

        Tool(
            "move_file",
            "Move or rename a file or folder inside the workspace.",
            Schema.obj(Prop("from", "string", "Existing path.", true), Prop("to", "string", "New path.", true)),
        ) { a -> move(sb, a.req("from"), a.req("to")) },

        Tool(
            "find_files",
            "Find files by name pattern, e.g. '*.cs' or 'src/**/*.json'.",
            Schema.obj(
                Prop("pattern", "string", "Glob pattern. No '/' = match file names anywhere.", true),
                Prop("path", "string", "Folder to search. Default '.'"),
            ),
        ) { a -> findFiles(sb, a.req("pattern"), a.str("path") ?: ".") },

        Tool(
            "search_files",
            "Search file contents with a regular expression (like grep). Returns file:line: text.",
            Schema.obj(
                Prop("pattern", "string", "Regular expression.", true),
                Prop("path", "string", "Folder or file to search. Default '.'"),
                Prop("glob", "string", "Only search files matching this pattern, e.g. '*.cs'."),
                Prop("ignore_case", "boolean", "Case-insensitive. Default false."),
            ),
        ) { a -> search(sb, a.req("pattern"), a.str("path") ?: ".", a.str("glob"), a.bool("ignore_case")) },
    )

    // ---- implementations ----

    fun listFiles(sb: Sandbox, path: String, recursive: Boolean): String {
        val dir = sb.resolve(path)
        if (!dir.exists()) return "Error: '$path' does not exist."
        if (!dir.isDirectory) return "Error: '$path' is a file. Use read_file."
        val lines = mutableListOf<String>()
        fun walk(d: File, depth: Int, indent: String) {
            val entries = d.listFiles()?.filterNot { it.name in ignored }
                ?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })) ?: return
            for (e in entries) {
                if (lines.size >= MAX_LIST) return
                lines += if (e.isDirectory) "$indent${e.name}/" else "$indent${e.name}  (${size(e.length())})"
                if (recursive && e.isDirectory && depth < 4) walk(e, depth + 1, "$indent  ")
            }
        }
        walk(dir, 1, "")
        if (lines.isEmpty()) return "(empty folder)"
        if (lines.size >= MAX_LIST) lines += "... (stopped at $MAX_LIST entries)"
        return lines.joinToString("\n")
    }

    fun readFile(sb: Sandbox, path: String, startLine: Int, maxLines: Int): String {
        val f = sb.resolve(path)
        if (!f.exists()) return "Error: '$path' does not exist."
        if (f.isDirectory) return "Error: '$path' is a folder. Use list_files."
        if (nz.thys.ashley.device.Photos.isImage(f.name)) return "Error: '$path' is a photo (${size(f.length())}). read_file can't show images. " +
            "If the user attached it to their message and your model can see images, you already have it; otherwise tell them you can't see photos with this model."
        if (f.length() > 20_000_000) return "Error: '$path' is ${size(f.length())}, too big to read."
        val text = f.readText()
        if (text.contains('\u0000')) return "Error: '$path' looks like a binary file (${size(f.length())})."
        val all = text.lines()
        val start = startLine.coerceAtLeast(1)
        val shown = all.drop(start - 1).take(maxLines.coerceIn(1, 5000))
        val sb2 = StringBuilder()
        shown.forEachIndexed { i, l -> sb2.append(start + i).append('\t').append(l).append('\n') }
        val end = start - 1 + shown.size
        if (end < all.size) sb2.append("... (${all.size - end} more lines; use start_line=${end + 1})")
        return sb2.toString().ifEmpty { "(empty file)" }
    }

    fun writeFile(sb: Sandbox, memory: MemoryStore, path: String, content: String, append: Boolean): String {
        val f = sb.resolve(path)
        if (f.isDirectory) return "Error: '$path' is a folder."
        f.parentFile?.mkdirs()
        return if (append) {
            f.appendText(content)
            "Appended ${content.length} characters to ${sb.rel(f)}."
        } else {
            val existed = f.exists()
            if (existed) memory.backup(f, sb.root)
            f.writeText(content)
            "${if (existed) "Replaced" else "Created"} ${sb.rel(f)} (${content.length} characters)."
        }
    }

    fun editFile(sb: Sandbox, memory: MemoryStore, path: String, old: String, new: String, all: Boolean): String {
        val f = sb.resolve(path)
        if (!f.isFile) return "Error: '$path' does not exist."
        if (old.isEmpty()) return "Error: old_text is empty."
        val text = f.readText()
        var count = 0
        var at = text.indexOf(old)
        while (at >= 0) { count++; at = text.indexOf(old, at + old.length) }
        if (count == 0) return "Error: old_text was not found in $path. Read the file and copy the text exactly."
        if (count > 1 && !all) return "Error: old_text matches $count times. Include more surrounding text, or set replace_all=true."
        memory.backup(f, sb.root)
        f.writeText(if (all) text.replace(old, new) else text.replaceFirst(old, new))
        return "Edited ${sb.rel(f)} ($count replacement${if (count == 1) "" else "s"})."
    }

    fun delete(sb: Sandbox, memory: MemoryStore, path: String, recursive: Boolean): String {
        val f = sb.resolve(path)
        if (f == sb.root.canonicalFile) return "Error: refusing to delete the workspace itself."
        if (!f.exists()) return "Error: '$path' does not exist."
        if (f.isDirectory) {
            if (!recursive) return "Error: '$path' is a folder; set recursive=true to delete it."
            return if (f.deleteRecursively()) "Deleted folder ${sb.rel(f)}." else "Error: could not delete $path."
        }
        memory.backup(f, sb.root)
        return if (f.delete()) "Deleted ${sb.rel(f)} (backed up)." else "Error: could not delete $path."
    }

    fun move(sb: Sandbox, from: String, to: String): String {
        val src = sb.resolve(from)
        val dst = sb.resolve(to)
        if (!src.exists()) return "Error: '$from' does not exist."
        if (dst.exists()) return "Error: '$to' already exists."
        dst.parentFile?.mkdirs()
        return if (src.renameTo(dst)) "Moved ${sb.rel(src)} to ${sb.rel(dst)}." else "Error: could not move $from."
    }

    fun findFiles(sb: Sandbox, pattern: String, path: String): String {
        val dir = sb.resolve(path)
        if (!dir.isDirectory) return "Error: '$path' is not a folder."
        val byName = !pattern.contains('/')
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        val hits = dir.walkTopDown().onEnter { it.name !in ignored }.filter { it.isFile }.filter { f ->
            if (byName) matcher.matches(java.nio.file.Paths.get(f.name))
            else matcher.matches(java.nio.file.Paths.get(f.relativeTo(dir).path))
        }.take(MAX_MATCHES + 1).map { sb.rel(it) }.toList()
        if (hits.isEmpty()) return "No files match '$pattern'."
        return hits.take(MAX_MATCHES).joinToString("\n") + if (hits.size > MAX_MATCHES) "\n... (more not shown)" else ""
    }

    fun search(sb: Sandbox, pattern: String, path: String, glob: String?, ignoreCase: Boolean): String {
        val regex = try {
            if (ignoreCase) Regex(pattern, RegexOption.IGNORE_CASE) else Regex(pattern)
        } catch (e: Exception) {
            return "Error: bad regular expression: ${e.message}"
        }
        val start = sb.resolve(path)
        if (!start.exists()) return "Error: '$path' does not exist."
        val matcher = glob?.let { FileSystems.getDefault().getPathMatcher("glob:$it") }
        val out = mutableListOf<String>()
        val files = if (start.isFile) sequenceOf(start) else start.walkTopDown().onEnter { it.name !in ignored }.filter { it.isFile }
        for (f in files) {
            if (out.size >= MAX_MATCHES) break
            if (matcher != null && !matcher.matches(java.nio.file.Paths.get(f.name))) continue
            if (f.length() > 2_000_000) continue
            val text = try { f.readText() } catch (_: Exception) { continue }
            if (text.contains('\u0000')) continue
            text.lineSequence().forEachIndexed { i, line ->
                if (out.size < MAX_MATCHES && regex.containsMatchIn(line)) out += "${sb.rel(f)}:${i + 1}: ${line.trim().take(300)}"
            }
        }
        if (out.isEmpty()) return "No matches for '$pattern'."
        return out.joinToString("\n") + if (out.size >= MAX_MATCHES) "\n... (stopped at $MAX_MATCHES matches)" else ""
    }

    private fun size(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / 1048576.0)
    }
}
