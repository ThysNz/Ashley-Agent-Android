package nz.thys.ashley.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.thys.ashley.ui.ChatViewModel
import nz.thys.ashley.ui.ToolUi
import nz.thys.ashley.ui.components.Dot
import nz.thys.ashley.ui.components.Pill
import nz.thys.ashley.ui.components.Segmented
import nz.thys.ashley.ui.components.SmallButton
import nz.thys.ashley.ui.theme.Ashley
import java.text.DateFormat
import java.util.Date

// ---- tool card (collapsible, like <details class="tool"> on the desktop) ----

@Composable
fun ToolCard(t: ToolUi) {
    val c = Ashley.colors
    var open by remember { mutableStateOf(false) }
    val icon = when {
        t.name.startsWith("web") -> Icons.Outlined.Public
        t.name.endsWith("_file") || t.name.endsWith("_files") -> Icons.Outlined.Folder
        t.name in setOf("todo", "remember", "forget") -> Icons.Outlined.CheckCircle
        else -> Icons.Outlined.Build
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(8.dp))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClickLabel = if (open) "Hide details" else "Show details") { open = !open }.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, null, tint = c.muted, modifier = Modifier.size(15.dp))
            Text(t.name, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            Text(t.what, fontSize = 12.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            val stat = when {
                t.result == null -> "running"
                t.failed -> "failed"
                t.ms == null -> "done"
                t.ms < 1000 -> "${t.ms} ms"
                else -> String.format("%.1f s", t.ms / 1000.0)
            }
            Text(stat, fontSize = 12.sp, color = when { t.result == null -> c.faint; t.failed -> c.danger; else -> c.ok })
        }
        if (open) {
            Box(Modifier.fillMaxWidth().background(c.border).padding(top = 1.dp))
            Column(Modifier.padding(10.dp)) {
                Text("Arguments", fontSize = 11.sp, color = c.faint)
                Text(t.args, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.muted)
                Text("Result", fontSize = 11.sp, color = c.faint, modifier = Modifier.padding(top = 6.dp))
                Box(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
                    Text(t.result ?: "(running)", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = c.muted)
                }
            }
        }
    }
}

// ---- panel drawer: Todo, Notes, Workspace (the desktop's side panel) ----

@Composable
fun PanelDrawer(vm: ChatViewModel, visible: Boolean, onClose: () -> Unit) {
    val c = Ashley.colors
    var viewing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) { if (visible) vm.refreshPanels() }

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close panel", onClick = onClose),
        )
    }
    AnimatedVisibility(
        visible, enter = slideInHorizontally { it }, exit = slideOutHorizontally { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                Modifier.fillMaxHeight().width(318.dp).background(c.surface).systemBarsPadding()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            ) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${vm.agentConfig.name}'s desk", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close", tint = c.text) }
                }
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PanelSection("Todo") {
                        if (vm.todo.isEmpty()) Text("Nothing on the list.", fontSize = 13.sp, color = c.faint)
                        vm.todo.forEach { t ->
                            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                                Icon(
                                    if (t.done) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, null,
                                    tint = if (t.done) c.ok else c.faint, modifier = Modifier.size(16.dp).padding(top = 1.dp),
                                )
                                Text(
                                    t.text, fontSize = 14.sp, color = if (t.done) c.faint else c.text,
                                    textDecoration = if (t.done) TextDecoration.LineThrough else null, modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    PanelSection("Notes") {
                        if (vm.notes.isEmpty()) Text("No notes yet. Ask her to remember something.", fontSize = 13.sp, color = c.faint)
                        vm.notes.forEach { Text(it, fontSize = 13.5.sp, color = c.text, modifier = Modifier.padding(vertical = 3.dp)) }
                    }
                    PanelSection("Workspace", last = true) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text("workspace", fontSize = 12.5.sp, color = c.accent, modifier = Modifier.clickable { vm.openDir(".") }.padding(vertical = 4.dp))
                            if (vm.dir != ".") {
                                var acc = ""
                                vm.dir.split('/', '\\').forEach { part ->
                                    acc = if (acc.isEmpty()) part else "$acc/$part"
                                    val target = acc
                                    Text(" / ", fontSize = 12.5.sp, color = c.faint)
                                    Text(part, fontSize = 12.5.sp, color = c.accent, modifier = Modifier.clickable { vm.openDir(target) }.padding(vertical = 4.dp))
                                }
                            }
                        }
                        if (vm.files.isEmpty()) Text("Empty. Files go in:\n${vm.workspacePath}", fontSize = 12.sp, color = c.faint)
                        vm.files.forEach { f ->
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(8.dp))
                                    .clickable { if (f.isDir) vm.openDir(f.rel) else viewing = f.rel }.padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (f.isDir) Icons.Outlined.Folder else Icons.Outlined.Description, null, tint = if (f.isDir) c.accent else c.muted, modifier = Modifier.size(16.dp))
                                Text(f.name, fontSize = 14.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    viewing?.let { rel ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            title = { Text(rel.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                    Text(vm.readWorkspaceFile(rel), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun PanelSection(title: String, last: Boolean = false, content: @Composable () -> Unit) {
    val c = Ashley.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.muted, letterSpacing = 0.6.sp, modifier = Modifier.padding(bottom = 6.dp))
        content()
    }
    if (!last) Box(Modifier.fillMaxWidth().padding(top = 0.dp).background(c.border).heightIn(min = 1.dp, max = 1.dp))
}

// ---- Model / Persona / Voice picker (bottom sheet) ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickerSheet(vm: ChatViewModel, initialTab: String, onDismiss: () -> Unit, openModels: () -> Unit) {
    val c = Ashley.colors
    var tab by remember { mutableStateOf(initialTab) }
    LaunchedEffect(Unit) { vm.checkAllModels() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Segmented(listOf("Model", "Persona", "Voice"), tab, { tab = it }, Modifier.fillMaxWidth())
            when (tab) {
                "Model" -> {
                    vm.models.forEach { m ->
                        val current = m.name == vm.modelName
                        val ok = vm.modelStatus[m.name]
                        PickRow(
                            current = current,
                            dot = when (ok) { true -> c.ok; false -> c.faint; null -> c.border },
                            title = m.name,
                            sub = listOfNotNull(engineLabel(m.provider), m.backend.uppercase().takeIf { m.provider in setOf("llamacpp", "litert") }, "${m.contextSize / 1024}k ctx").joinToString(" · ") +
                                if (ok == false) " · " + vm.reasonFor(m) else "",
                            badge = when { !m.supportsTools -> "no tools"; m.provider in setOf("openai", "ollama") -> "PC"; else -> null },
                        ) { vm.selectModel(m.name); onDismiss() }
                    }
                    Text("Switching keeps the conversation.", fontSize = 12.5.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                    SmallButton("Manage models in Settings", onClick = openModels)
                }
                "Persona" -> vm.personas.forEach { p ->
                    PickRow(p == vm.personaName, null, p, vm.personaText(p).lineSequence().firstOrNull()?.take(90) ?: "", null) { vm.selectPersona(p); onDismiss() }
                }
                "Voice" -> {
                    if (vm.voices.isEmpty()) Text("No offline English voices found. Install one in Android Settings, Text-to-speech.", fontSize = 13.sp, color = c.muted)
                    PickRow(vm.voiceName.isBlank(), null, "System default", "The phone's default text-to-speech voice", null) { vm.selectVoice(""); onDismiss() }
                    vm.voices.forEach { v ->
                        PickRow(v == vm.voiceName, null, v, "Android TTS (Piper voices arrive in phase 4)", null) { vm.selectVoice(v); vm.speak(null, "Hello, this is how I sound.") }
                    }
                }
            }
        }
    }
}

fun engineLabel(provider: String) = when (provider) {
    "llamacpp" -> "llama.cpp"
    "litert" -> "LiteRT"
    "openai" -> "llama-server"
    "ollama" -> "Ollama"
    "fake" -> "demo"
    else -> provider
}

@Composable
private fun PickRow(current: Boolean, dot: Color?, title: String, sub: String, badge: String?, onClick: () -> Unit) {
    val c = Ashley.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(10.dp))
            .background(if (current) c.accentBg else c.surface)
            .border(1.dp, if (current) c.accent else c.border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (dot != null) Dot(dot)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                badge?.let { Pill(it) }
            }
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (current) Pill("in use", c.surface, c.accentText)
    }
}

// ---- saved chats ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsSheet(vm: ChatViewModel, onDismiss: () -> Unit) {
    val c = Ashley.colors
    val chats = remember { vm.listChats() }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.surface) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Saved chats", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            SmallButton("Start a new chat", primary = true) { vm.newChat(); onDismiss() }
            if (chats.isEmpty()) Text("No saved chats yet. When you start a new chat, the current one is saved here.", fontSize = 13.sp, color = c.muted)
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                chats.forEach { ch ->
                    PickRow(
                        false, null, ch.title,
                        "${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ch.time))} · ${ch.messages} messages", null,
                    ) { vm.openChat(ch.id); onDismiss() }
                }
            }
        }
    }
}

