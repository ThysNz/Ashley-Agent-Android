package nz.thys.ashley.tools

import nz.thys.ashley.memory.MemoryStore

/** remember / forget / todo - same wording as Memory.cs so the model behaves the same. */
object MemoryTools {
    fun create(memory: MemoryStore, userName: String): List<Tool> = listOf(
        Tool(
            "remember",
            "Save a lasting fact to your long-term notes: about $userName, his projects, preferences or decisions. One short, self-contained, verified sentence. " +
                "NOT for logging what you did in a task. Notes are shown to you at the start of every future session.",
            Schema.obj(Prop("note", "string", "The fact to remember.", true)),
        ) { a -> memory.remember(a.req("note")) },

        Tool(
            "forget",
            "Remove notes that contain the given text (use when a note is wrong or out of date).",
            Schema.obj(Prop("match", "string", "Text contained in the note(s) to delete.", true)),
        ) { a -> memory.forget(a.req("match")) },

        Tool(
            "todo",
            "Your task list for multi-step work. Actions: list, add (item=text), done / undone / remove (item=number), clear. Plan with it for any job of three or more steps and tick items off as you go.",
            Schema.obj(
                Prop("action", "string", "list | add | done | undone | remove | clear", true),
                Prop("item", "string", "Text for add, or item number for done/undone/remove."),
            ),
        ) { a -> memory.todo(a.req("action"), a.str("item")) },
    )
}
