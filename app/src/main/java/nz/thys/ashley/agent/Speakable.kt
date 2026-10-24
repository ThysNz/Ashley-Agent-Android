package nz.thys.ashley.agent

/** Turns a reply into something safe to speak: no markdown, no code, no URLs, capped length (the desktop's `spoken`). */
object Speakable {
    private const val MAX_CHARS = 420

    fun make(text: String): String {
        var s = text
        s = s.replace(Regex("```.*?```", RegexOption.DOT_MATCHES_ALL), " The code is in the chat. ")
        s = s.replace(Regex("`([^`]*)`"), "$1")
        s = s.replace(Regex("https?://\\S+"), "a link")
        s = s.replace(Regex("(?m)^\\s*#{1,6}\\s*"), "")
        s = s.replace(Regex("(?m)^\\s*([-*+]|\\d+[.)])\\s+"), "")
        s = s.replace(Regex("[*_~>|]"), "")
        s = s.replace(Regex("\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        s = s.replace(Regex("\\s+"), " ").trim()
        if (s.length <= MAX_CHARS) return s
        // Cut at a sentence end before the cap.
        val cut = s.take(MAX_CHARS)
        val end = cut.lastIndexOfAny(charArrayOf('.', '!', '?'))
        return if (end > 80) cut.take(end + 1) else "$cut..."
    }
}
