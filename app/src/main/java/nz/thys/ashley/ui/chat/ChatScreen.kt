package nz.thys.ashley.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.automirrored.outlined.List as ListIcon
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import nz.thys.ashley.core.ContextInfo
import nz.thys.ashley.ui.ChatItem
import nz.thys.ashley.ui.ChatViewModel
import nz.thys.ashley.ui.components.Dot
import nz.thys.ashley.ui.components.IconBox
import nz.thys.ashley.ui.theme.Ashley
import kotlin.math.max

@Composable
fun ChatScreen(vm: ChatViewModel, openSettings: () -> Unit, openModels: () -> Unit) {
    val c = Ashley.colors
    var showPanel by rememberSaveable { mutableStateOf(false) }
    var pickerTab by rememberSaveable { mutableStateOf<String?>(null) }
    var showChats by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(vm, onChats = { showChats = true }, onPanel = { showPanel = true }, onSettings = openSettings, onPicker = { pickerTab = it })
            ContextMeter(vm.context)
            PhoneStrip(vm)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ChatLog(vm)
                if (vm.recording) ListeningCard(vm, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
            }
            vm.toast?.let { Toast(it) }
            Composer(vm)
            Options(vm)
        }
        PanelDrawer(vm, visible = showPanel, onClose = { showPanel = false })
    }
    pickerTab?.let { tab -> PickerSheet(vm, tab, onDismiss = { pickerTab = null }, openModels = { pickerTab = null; openModels() }) }
    if (showChats) ChatsSheet(vm, onDismiss = { showChats = false })
}

// ---- top bar: status dot, name, actions, then the Model / Persona / Voice chips ----

@Composable
private fun TopBar(vm: ChatViewModel, onChats: () -> Unit, onPanel: () -> Unit, onSettings: () -> Unit, onPicker: (String) -> Unit) {
    val c = Ashley.colors
    Column(Modifier.fillMaxWidth().background(c.surface).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Dot(when (vm.modelReady) { true -> c.ok; false -> c.warn; null -> c.faint })
                Column(Modifier.padding(start = 8.dp)) {
                    Text(vm.agentConfig.name, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = c.text)
                    val status = when (vm.modelReady) {
                        true -> "${vm.modelName} ready"
                        false -> "${vm.modelName} not ready"
                        null -> "${vm.modelName} checking..."
                    }
                    Text(status, fontSize = 11.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconBox(Icons.Outlined.Schedule, "Saved chats", onChats)
            IconBox(Icons.Outlined.Add, "New chat", vm::newChat)
            IconBox(Icons.AutoMirrored.Outlined.ListIcon, "Todo, notes and workspace", onPanel)
            IconBox(Icons.Outlined.Tune, "Settings", onSettings)
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Model", vm.modelName, Modifier.weight(1f)) { onPicker("Model") }
            Chip("Persona", vm.personaName, Modifier.weight(1f)) { onPicker("Persona") }
            Chip("Voice", vm.voiceName.ifBlank { "system" }, Modifier.weight(1f)) { onPicker("Voice") }
        }
        if (vm.modelReady == false && vm.modelReason.isNotBlank()) {
            Text(
                vm.modelReason, fontSize = 12.5.sp, color = c.warn,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.warnBg).padding(8.dp),
            )
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

@Composable
private fun Chip(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Ashley.colors
    Column(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(c.surface)
            .border(1.dp, c.borderStrong, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = "Choose $label", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(label, fontSize = 10.5.sp, color = c.muted)
        Text("$value ▾", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- context meter (system / tools / notes / chat), as in the desktop header ----

@Composable
private fun ContextMeter(ctx: ContextInfo?) {
    val c = Ashley.colors
    val window = max(ctx?.window ?: 1, 1)
    val used = ctx?.used ?: 0
    val frac = used.toFloat() / window
    val edge = when { frac > 0.9f -> c.danger; frac > 0.75f -> c.warn; else -> c.border }
    fun k(n: Int) = if (n >= 1000) String.format("%.1fk", n / 1000.0) else n.toString()
    Column(Modifier.fillMaxWidth().background(c.surface2).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)) {
        Row {
            Text("Context window", fontSize = 12.sp, color = c.muted, modifier = Modifier.weight(1f))
            Text(if (ctx == null) "-" else "${k(used)} of ${k(window)} tokens", fontSize = 12.sp, color = if (frac > 0.75f) edge else c.muted)
        }
        Row(
            Modifier.padding(top = 5.dp).fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(c.surface)
                .border(1.dp, edge, RoundedCornerShape(4.dp))
                .semantics { contentDescription = "Context window ${(frac * 100).toInt()} percent used" },
        ) {
            if (ctx != null) {
                val parts = listOf(ctx.system to c.segSystem, ctx.tools to c.segTools, ctx.notes to c.segNotes, ctx.history to c.segHistory)
                var sum = 0f
                parts.forEach { (n, color) ->
                    val f = (n.toFloat() / window).coerceIn(0f, 1f - sum)
                    sum += f
                    if (f > 0.0005f) Box(Modifier.weight(f).fillMaxHeight().background(color))
                }
                if (1f - sum > 0.0005f) Spacer(Modifier.weight(1f - sum))
            }
        }
        Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("System" to c.segSystem, "Tools" to c.segTools, "Notes" to c.segNotes, "Chat" to c.segHistory).forEach { (name, color) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
                    Text(name, fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

// ---- phone strip: temperature, thermal state, RAM, battery ----

@Composable
private fun PhoneStrip(vm: ChatViewModel) {
    val c = Ashley.colors
    val h = vm.health ?: return
    val limits = vm.limits()
    val hot = nz.thys.ashley.device.PhoneMonitor.tooHot(h, limits) != null
    val warm = !hot && ((h.batteryTempC ?: 0f) >= limits.maxTempC - 4f || h.thermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE)
    val tempColor = when { hot -> c.danger; warm -> c.warn; else -> c.muted }
    val ramColor = if (h.lowMemory) c.danger else c.muted
    Row(
        Modifier.fillMaxWidth().background(c.surface2).padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .semantics { contentDescription = "Phone status" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            (h.batteryTempC?.let { String.format("%.1f °C", it) } ?: "-- °C") + " · " + h.thermalLabel,
            fontSize = 11.5.sp, color = tempColor, fontWeight = if (hot || warm) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            String.format("RAM %.1f / %.1f GB free", h.availRamBytes / 1e9, h.totalRamBytes / 1e9),
            fontSize = 11.5.sp, color = ramColor,
        )
        Text(String.format("App %.1f GB", h.appRamBytes / 1e9), fontSize = 11.5.sp, color = c.muted)
        Spacer(Modifier.weight(1f))
        h.batteryPct?.let { Text("$it%" + if (h.charging) " charging" else "", fontSize = 11.5.sp, color = c.muted) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
}

// ---- the chat log ----

@Composable
private fun ChatLog(vm: ChatViewModel) {
    val c = Ashley.colors
    val state = rememberLazyListState()
    val last = vm.items.lastOrNull()
    LaunchedEffect(vm.items.size, last) {
        if (vm.items.isNotEmpty()) state.animateScrollToItem(vm.items.size - 1, scrollOffset = 100_000)
    }
    if (vm.items.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Hi, I'm ${vm.agentConfig.name}.", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = c.muted)
            Text(
                "Type a message, or hold the mic and talk.\nTry \"what's in my workspace\" or \"add a todo to test the app\".",
                fontSize = 13.5.sp, color = c.faint, modifier = Modifier.padding(top = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items(vm.items, key = { it.key }) { item ->
            when (item) {
                is ChatItem.User -> Column { UserBubble(vm, item) }
                is ChatItem.Assistant -> AssistantBlock(vm, item)
            }
        }
    }
}

@Composable
private fun UserBubble(vm: ChatViewModel, item: ChatItem.User) {
    val c = Ashley.colors
    var viewing by remember { mutableStateOf(false) }
    item.photo?.let { rel ->
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.End) {
            PhotoInChat(vm.workspaceFile(rel)) { viewing = true }
        }
        if (viewing) PhotoViewer(vm.workspaceFile(rel), item.text) { viewing = false }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        if (item.spoken) Icon(Icons.Outlined.Mic, "Spoken", tint = c.muted, modifier = Modifier.size(15.dp).padding(end = 2.dp))
        Text(
            item.text, color = c.accentText, fontSize = 14.sp, lineHeight = 20.sp,
            modifier = Modifier.padding(start = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.accentBg).padding(horizontal = 13.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun AssistantBlock(vm: ChatViewModel, a: ChatItem.Assistant) {
    val c = Ashley.colors
    val clipboard = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(vm.agentConfig.name, fontSize = 11.sp, color = c.muted)
        a.notes.forEach { Text(it, fontSize = 12.5.sp, color = c.faint) }
        a.tools.forEach { ToolCard(it) }
        a.status?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp, color = c.accent)
                Text(it, fontSize = 12.5.sp, color = c.muted, modifier = Modifier.padding(start = 8.dp))
            }
        }
        a.text?.let { MarkdownText(it) }
        a.error?.let {
            Text(it, color = c.danger, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.dangerBg).padding(horizontal = 12.dp, vertical = 8.dp))
        }
        if (a.text != null || a.meta != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val speaking = vm.speakingKey == a.key
                if (a.text != null) {
                    SmallIcon(if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Outlined.VolumeUp, if (speaking) "Stop speaking" else "Speak this reply") {
                        if (speaking) vm.stopSpeaking() else vm.speak(a.key, a.spoken)
                    }
                    SmallIcon(Icons.Outlined.ContentCopy, "Copy reply") { clipboard.setText(AnnotatedString(a.text)) }
                }
                Text(if (speaking) "Speaking · tap to stop" else a.meta ?: "", fontSize = 11.5.sp, color = if (speaking) c.accentText else c.muted, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = Ashley.colors.muted, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun Toast(text: String) {
    val c = Ashley.colors
    Text(
        text, fontSize = 12.5.sp, color = c.muted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.surface2).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ---- listening card (shown while the mic is held) ----

@Composable
private fun ListeningCard(vm: ChatViewModel, modifier: Modifier) {
    val c = Ashley.colors
    val lvl by animateFloatAsState(vm.level, label = "level")
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(horizontal = 22.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.height(40.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.35f, 0.7f, 1f, 0.55f, 0.85f, 0.45f, 0.75f, 0.3f).forEach { f ->
                Box(Modifier.width(6.dp).height((6 + 34 * f * lvl).dp).clip(RoundedCornerShape(3.dp)).background(c.danger))
            }
        }
        Text("Listening", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.text)
        Text(
            vm.partial.ifBlank { if (vm.voiceConfig.talkMode == "hold") "Release to send" else "Tap the mic again to send" },
            fontSize = 12.5.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- composer: hold-to-talk mic, message box, send / stop ----

@Composable
private fun Composer(vm: ChatViewModel) {
    val c = Ashley.colors
    val context = LocalContext.current
    var hasMic by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> vm.onPhotoTaken(ok) }

    // A photo waiting for its comment.
    vm.pendingPhoto?.let { rel ->
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 6.dp).clip(RoundedCornerShape(12.dp))
                .background(c.surface).border(1.dp, c.border, RoundedCornerShape(12.dp)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PhotoThumb(vm.workspaceFile(rel), 56)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text("Photo ready", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                Text("Type or say your comment, then send. Saved in ${rel.substringBeforeLast('/')}/", fontSize = 12.sp, color = c.muted)
            }
            SmallIcon(Icons.Outlined.Close, "Remove photo") { vm.clearPhoto() }
        }
    }

    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasMic = ok
        if (!ok) vm.micPermissionDenied()
    }
    val micAllowed by rememberUpdatedState(hasMic)
    val talkMode = vm.voiceConfig.talkMode

    // Recording timer for the level bar over the text box.
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(vm.recording) {
        while (vm.recording) { seconds = (System.currentTimeMillis() - vm.recordStart) / 1000f; delay(100) }
    }

    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val micBg = when { vm.recording -> c.danger; vm.speakingKey != null -> c.accentBg; else -> c.surface }
        val micFg = when { vm.recording -> Color.White; vm.speakingKey != null -> c.accentText; else -> c.text }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(micBg)
                .border(1.dp, if (vm.recording) c.danger else c.borderStrong, CircleShape)
                .semantics { contentDescription = if (talkMode == "hold") "Hold to talk" else "Tap to talk" }
                .pointerInput(talkMode) {
                    if (talkMode == "hold") {
                        detectTapGestures(onPress = {
                            if (!micAllowed) {
                                askMic.launch(Manifest.permission.RECORD_AUDIO)
                                return@detectTapGestures
                            }
                            vm.micDown()
                            if (tryAwaitRelease()) vm.micUp() else vm.micCancel()
                        })
                    } else {
                        detectTapGestures(onTap = {
                            if (!micAllowed) askMic.launch(Manifest.permission.RECORD_AUDIO)
                            else if (vm.recording) vm.micUp() else vm.micDown()
                        })
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.Mic, null, tint = micFg, modifier = Modifier.size(22.dp)) }

        Box(
            Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = "Take a photo") { runCatching { takePhoto.launch(vm.newPhotoUri()) }.onFailure { vm.showToast("No camera app found.") } },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Outlined.PhotoCamera, "Take a photo", tint = c.text, modifier = Modifier.size(22.dp)) }

        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = vm.input,
                onValueChange = { vm.input = it },
                textStyle = TextStyle(fontSize = 15.sp, color = c.text),
                cursorBrush = SolidColor(c.accent),
                maxLines = 6,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.send() }),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .border(1.dp, c.borderStrong, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 13.dp)
                    .semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    if (vm.input.isEmpty()) Text("Message ${vm.agentConfig.name}", fontSize = 15.sp, color = c.faint)
                    inner()
                },
            )
            if (vm.recording) {
                Row(
                    Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).background(c.surface)
                        .border(1.dp, c.danger, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f).height(8.dp)) {
                        Box(Modifier.fillMaxWidth(vm.level.coerceIn(0.03f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c.danger))
                    }
                    Text(String.format("%.1f s", seconds), fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(start = 10.dp))
                }
            }
        }

        val canSend = vm.input.isNotBlank() || vm.pendingPhoto != null
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (vm.busy) c.danger else if (canSend) c.accent else c.accent.copy(alpha = 0.5f))
                .clickable(role = Role.Button, onClickLabel = if (vm.busy) "Stop" else "Send") { if (vm.busy) vm.stop() else vm.send() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (vm.busy) Icons.Filled.Stop else Icons.Filled.ArrowUpward, if (vm.busy) "Stop" else "Send", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun Options(vm: ChatViewModel) {
    val c = Ashley.colors
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = vm.voiceConfig.speakReplies, onCheckedChange = { vm.updateVoice(vm.voiceConfig.copy(speakReplies = it)); if (!it) vm.stopSpeaking() })
        Text("Speak replies", fontSize = 13.sp, color = c.muted)
        Spacer(Modifier.width(8.dp))
        Checkbox(checked = vm.voiceConfig.autoSend, onCheckedChange = { vm.updateVoice(vm.voiceConfig.copy(autoSend = it)) })
        Text("Send after talking", fontSize = 13.sp, color = c.muted)
    }
}
