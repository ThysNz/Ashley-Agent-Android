package nz.thys.ashley.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nz.thys.ashley.ui.theme.Ashley

/** 44 dp square icon button with the desktop's outlined look. */
@Composable
fun IconBox(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Ashley.colors
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface)
            .border(1.dp, c.borderStrong, RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = c.text, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Dot(color: Color, size: Int = 10) =
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))

@Composable
fun Pill(text: String, bg: Color = Ashley.colors.surface2, fg: Color = Ashley.colors.muted) {
    Text(
        text, fontSize = 11.sp, color = fg,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(bg).padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun ScreenHeader(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Ashley.colors.text) }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Ashley.colors.text)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(), fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = Ashley.colors.muted,
        letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 6.dp),
    )
}

/** A white rounded card holding settings rows separated by thin dividers. */
@Composable
fun Group(content: @Composable () -> Unit) {
    val c = Ashley.colors
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(14.dp)),
    ) { content() }
}

@Composable
fun RowDivider() = HorizontalDivider(color = Ashley.colors.border.copy(alpha = 0.6f), thickness = 1.dp)

@Composable
fun NavRow(icon: ImageVector?, title: String, sub: String?, onClick: () -> Unit) {
    val c = Ashley.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (icon != null) Box(
            Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(c.accentBg), contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.accentText, modifier = Modifier.size(20.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.text)
            if (sub != null) Text(sub, fontSize = 12.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.faint)
    }
}

@Composable
fun ValueRow(title: String, value: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val c = Ashley.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = c.text)
            if (sub != null) Text(sub, fontSize = 12.sp, color = c.muted)
        }
        if (value.isNotEmpty()) Text(value, fontSize = 13.5.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
        trailing?.invoke()
    }
}

@Composable
fun SwitchRow(title: String, sub: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    val c = Ashley.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, color = c.text)
            if (sub != null) Text(sub, fontSize = 12.sp, color = c.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, label: String, onChange: (Float) -> Unit) {
    val c = Ashley.colors
    var v by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
        Row {
            Text(title, fontSize = 14.sp, color = c.text, modifier = Modifier.weight(1f))
            Text(label, fontSize = 13.5.sp, color = c.muted)
        }
        Slider(value = v, onValueChange = { v = it }, onValueChangeFinished = { onChange(v) }, valueRange = range, steps = steps)
    }
}

/** Equal-width segmented choice (Hold / Tap, CPU / GPU / NPU). */
@Composable
fun Segmented(options: List<String>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Ashley.colors
    Row(
        modifier.clip(RoundedCornerShape(9.dp)).background(c.surface2).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o.equals(selected, ignoreCase = true)
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(RoundedCornerShape(7.dp))
                    .background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(o) },
                contentAlignment = Alignment.Center,
            ) { Text(o, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) c.text else c.muted) }
        }
    }
}

/** A row that opens a small dialog to type a value. */
@Composable
fun EditRow(title: String, value: String, sub: String? = null, number: Boolean = false, onSave: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ValueRow(title, value, sub, onClick = { open = true })
    if (open) TextDialog(title, value, number, onDismiss = { open = false }) { onSave(it); open = false }
}

@Composable
fun TextDialog(title: String, initial: String, number: Boolean = false, singleLine: Boolean = true, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = singleLine,
                minLines = if (singleLine) 1 else 6,
                keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SmallButton(text: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Ashley.colors
    Box(
        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
            .background(if (primary) c.accent else c.surface)
            .border(1.dp, if (primary) c.accent else c.borderStrong, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontSize = 13.sp, color = if (primary) Color.White else if (enabled) c.text else c.faint, fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Normal) }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.size(h.dp))

@Composable
fun HGap(w: Int) = Spacer(Modifier.width(w.dp))
