package nz.thys.ashley.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.thys.ashley.ui.ChatViewModel
import nz.thys.ashley.ui.components.EditRow
import nz.thys.ashley.ui.components.Group
import nz.thys.ashley.ui.components.RowDivider
import nz.thys.ashley.ui.components.ScreenHeader
import nz.thys.ashley.ui.components.SectionLabel
import nz.thys.ashley.ui.components.SwitchRow
import nz.thys.ashley.ui.components.TextDialog
import nz.thys.ashley.ui.components.ValueRow
import nz.thys.ashley.ui.theme.Ashley

@Composable
private fun Frame(title: String, back: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(Ashley.colors.bg).statusBarsPadding().navigationBarsPadding()) {
        ScreenHeader(title, back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

// ---- Agent and tools (agent.json) ----

@Composable
fun SettingsAgentScreen(vm: ChatViewModel, back: () -> Unit) {
    val a = vm.agentConfig
    var pickPersona by remember { mutableStateOf(false) }
    var editPersona by remember { mutableStateOf(false) }
    Frame("Agent and tools", back) {
        Column {
            SectionLabel("Agent")
            Group {
                EditRow("Name", a.name) { if (it.isNotBlank()) vm.updateAgent(a.copy(name = it)) }
                RowDivider()
                EditRow("Your name", a.userName, sub = "Used in the system prompt") { if (it.isNotBlank()) vm.updateAgent(a.copy(userName = it)) }
                RowDivider()
                ValueRow("Persona", vm.personaName, onClick = { pickPersona = true })
                RowDivider()
                ValueRow("Edit persona text", "", sub = "personas/${vm.personaName}.md", onClick = { editPersona = true })
                RowDivider()
                EditRow("Max tool rounds", a.maxToolRounds.toString(), number = true) { s -> s.toIntOrNull()?.let { vm.updateAgent(a.copy(maxToolRounds = it.coerceIn(1, 500))) } }
                RowDivider()
                EditRow("History turns", a.historyTurns.toString(), number = true) { s -> s.toIntOrNull()?.let { vm.updateAgent(a.copy(historyTurns = it.coerceIn(1, 200))) } }
                RowDivider()
                EditRow("Notes limit (characters)", a.maxNotesChars.toString(), number = true) { s -> s.toIntOrNull()?.let { vm.updateAgent(a.copy(maxNotesChars = it.coerceIn(500, 100_000))) } }
            }
        }
        Column {
            SectionLabel("Tools")
            Group {
                SwitchRow("File tools", "Workspace folder only · backups on", a.tools.files) { vm.updateAgent(a.copy(tools = a.tools.copy(files = it))) }
                RowDivider()
                SwitchRow("Web fetch and search", "DuckDuckGo, 12 000 characters per page", a.tools.web) { vm.updateAgent(a.copy(tools = a.tools.copy(web = it))) }
                RowDivider()
                SwitchRow("Memory and todo", "remember, forget, todo", a.tools.memory) { vm.updateAgent(a.copy(tools = a.tools.copy(memory = it))) }
                RowDivider()
                SwitchRow("Skills", "Phase 3", a.tools.skills, enabled = false) { }
                RowDivider()
                SwitchRow("Shell via Termux", "Phase 5 · will ask before each command", a.tools.termux, enabled = false) { }
                RowDivider()
                SwitchRow("Git commits", "Later · commit only, never push", a.tools.git, enabled = false) { }
            }
        }
    }
    if (pickPersona) {
        AlertDialog(
            onDismissRequest = { pickPersona = false },
            title = { Text("Persona") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    vm.personas.forEach { p ->
                        Text(
                            p, fontWeight = if (p == vm.personaName) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth().clickable { vm.selectPersona(p); vm.updateAgent(vm.agentConfig.copy(persona = p)); pickPersona = false }.padding(vertical = 12.dp),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPersona = false }) { Text("Close") } },
        )
    }
    if (editPersona) {
        TextDialog("personas/${vm.personaName}.md", vm.personaText(vm.personaName), singleLine = false, onDismiss = { editPersona = false }) {
            vm.savePersona(vm.personaName, it)
            editPersona = false
        }
    }
}

// ---- Inspect: exactly what goes to the model ----

@Composable
fun InspectScreen(vm: ChatViewModel, back: () -> Unit) {
    val c = Ashley.colors
    val info = remember { vm.inspect() }
    val ctx = info.context
    Frame("What is sent to the model", back) {
        Text("${ctx.used} of ${ctx.window} tokens - system ${ctx.system}, tools ${ctx.tools}, notes ${ctx.notes}, chat ${ctx.history}.", fontSize = 13.sp, color = c.text)
        SectionLabel("System prompt")
        Mono(info.systemPrompt)
        SectionLabel("Tools offered")
        Text(info.tools.joinToString(", ").ifEmpty { "(none - this model has tools switched off)" }, fontSize = 13.sp, color = c.muted)
        SectionLabel("Conversation (${info.messages.size} messages)")
        info.messages.forEach { m ->
            val calls = if (m.toolCalls.isNotEmpty()) "[calls " + m.toolCalls.joinToString("; ") { "${it.name} ${it.argsJson}" } + "] " else ""
            Text("${m.role}${m.toolName?.let { " · $it" } ?: ""}: $calls${m.content.take(400)}", fontSize = 12.sp, color = c.muted)
        }
    }
}

@Composable
private fun Mono(text: String) {
    val c = Ashley.colors
    Text(
        text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = c.text,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.codeBg).horizontalScroll(rememberScrollState()).padding(12.dp),
    )
}

// ---- small information pages for the parts that arrive in later phases ----

@Composable
fun InfoScreen(vm: ChatViewModel, topic: String, back: () -> Unit) {
    val c = Ashley.colors
    val title = when (topic) { "skills" -> "Skills"; "memory" -> "Memory and data"; "appearance" -> "Appearance"; else -> "About" }
    Frame(title, back) {
        when (topic) {
            "skills" -> Text(
                "Skills use Claude's SKILL.md format, exactly as in F:\\AI-Agents\\skills. Phase 3 adds the loader, " +
                    "the /skill-name suggestions above the message box and import from a folder or .zip.", fontSize = 14.sp, color = c.text,
            )
            "memory" -> {
                SectionLabel("Notes (notes.md)")
                Mono(vm.notes.joinToString("\n").ifEmpty { "(empty)" })
                SectionLabel("Todo (todo.md)")
                Mono(vm.todo.joinToString("\n") { (if (it.done) "[x] " else "[ ] ") + it.text }.ifEmpty { "(empty)" })
                SectionLabel("Folders")
                Mono("workspace: ${vm.workspacePath}\nmodels:    ${vm.modelsPath}")
                Text("Copy files in over USB: the phone shows these under Android/data/nz.thys.ashley/files.", fontSize = 12.5.sp, color = c.muted)
            }
            "appearance" -> Text("The app follows the phone's light or dark setting, with the same colours as the AI-Agents web page.", fontSize = 14.sp, color = c.text)
            else -> {
                Text("Ashley Mobile 0.1.0 - phase 1 (UI shell, agent loop, tools, Android speech).", fontSize = 14.sp, color = c.text)
                SectionLabel("Build plan")
                Text(
                    "1 UI shell  ·  2 on-device model (llama.cpp)  ·  3 skills and parity  ·  4 Whisper + Piper voice  ·  5 LiteRT, Termux, extras",
                    fontSize = 13.sp, color = c.muted,
                )
            }
        }
    }
}
