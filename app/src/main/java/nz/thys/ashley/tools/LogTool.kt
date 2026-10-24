package nz.thys.ashley.tools

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * log_note: one simple call for "log this / note this down / save this comment for later".
 * Small on-device models pick a single obvious tool far more reliably than composing append_file
 * (and otherwise reach for todo or remember, which are for other things).
 */
object LogTool {
    fun create(sb: Sandbox, currentPhoto: () -> String?): Tool = Tool(
        "log_note",
        "Save a comment, idea, observation or instruction to a log file in the workspace for later reference. " +
            "Adds the date and time, and links the photo if the user attached one to this message. " +
            "Use this whenever the user says log, note down, write down, keep a record or save this comment. " +
            "Not for tasks (use todo) or facts about the user (use remember).",
        Schema.obj(
            Prop("text", "string", "What to save, in the user's words (clean up only obvious speech-to-text mistakes).", true),
            Prop("file", "string", "Log file in the workspace. Default 'notes/log.md'. Use a topic file such as 'notes/speaker-project.md' if the user names a topic."),
            Prop("title", "string", "Optional short heading for the entry."),
        ),
    ) { a ->
        val text = a.req("text").trim()
        if (text.isEmpty()) return@Tool "Error: nothing to log."
        var path = (a.str("file") ?: "notes/log.md").trim().ifEmpty { "notes/log.md" }
        if (!path.contains('.')) path += ".md"
        val f = sb.resolve(path)
        f.parentFile?.mkdirs()
        val now = LocalDateTime.now()
        if (!f.exists()) f.writeText("# ${f.nameWithoutExtension.replace('-', ' ').replaceFirstChar { it.uppercase() }}\n")
        val heading = a.str("title")?.trim()?.takeIf { it.isNotEmpty() }
        val photo = currentPhoto()
        val entry = buildString {
            append("\n## ").append(now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
            if (heading != null) append(" - ").append(heading)
            append("\n\n").append(text).append('\n')
            if (photo != null) {
                // Link relative to the log file so it opens from any markdown viewer.
                val rel = File(sb.root, photo).relativeTo(f.parentFile ?: sb.root).path.replace('\\', '/')
                append("\n![photo]($rel)\n")
            }
        }
        f.appendText(entry)
        "Logged to ${sb.rel(f)}" + (if (photo != null) " with the photo." else ".")
    }
}
