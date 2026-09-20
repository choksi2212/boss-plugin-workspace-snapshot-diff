package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.ui.BossAlertDialog
import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun WorkspaceDiffContent(component: WorkspaceDiffComponent) {
    BossTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                WorkspaceDiffToolbar(
                    onRefresh = { component.refresh() },
                    onSwap = { component.swap() },
                )
                Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.1f))

                val status by component.viewModel.status.collectAsState()
                val error by component.viewModel.error.collectAsState()
                AnimatedVisibility(
                    visible = status != null || error != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                ) {
                    ToastBanner(status, error, onDismiss = { component.clearMessages() })
                }

                if (component.viewModel.providerUnavailable) {
                    ProviderUnavailableMessage()
                } else {
                    DiffPanelBody(component)
                }
            }
        }
    }
}

@Composable
private fun WorkspaceDiffToolbar(onRefresh: () -> Unit, onSwap: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Snapshot Diff",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onSwap, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.SwapHoriz,
                contentDescription = "Swap A and B",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
        IconButton(onClick = onRefresh, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ToastBanner(status: String?, error: String?, onDismiss: () -> Unit) {
    LaunchedEffect(status, error) {
        delay(4000)
        onDismiss()
    }
    val isError = error != null
    val message = error ?: status ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) BossThemeColors.ErrorColor else BossThemeColors.SuccessColor)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Default.Close else Icons.Default.Check,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = BossThemeColors.TextPrimary,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            fontSize = 11.sp,
            color = BossThemeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(12.dp),
                tint = BossThemeColors.TextPrimary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ProviderUnavailableMessage() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Workspace manager unavailable",
                fontSize = 13.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun DiffPanelBody(component: WorkspaceDiffComponent) {
    val listState = rememberLazyListState()
    val diffState = component.viewModel.diff.collectAsState()
    val diff: DiffResult? = diffState.value
    Column(modifier = Modifier.fillMaxSize()) {
        PickerRow(
            label = "Workspace A",
            origin = component.viewModel.aOrigin.collectAsState().value,
            choices = component.viewModel.choices(),
            labelFor = { component.viewModel.labelFor(it) },
            onSelected = { component.setA(it) },
        )
        PickerRow(
            label = "Workspace B",
            origin = component.viewModel.bOrigin.collectAsState().value,
            choices = component.viewModel.choices(),
            labelFor = { component.viewModel.labelFor(it) },
            onSelected = { component.setB(it) },
        )
        ActionRow(
            onRun = { component.runDiff() },
            onCopyJson = { component.copyDiffJson() },
        )
        SaveRow(component = component)
        Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.08f))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .lazyListScrollbar(
                    listState = listState,
                    direction = Orientation.Vertical,
                    config = getPanelScrollbarConfig(),
                ),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (diff == null) {
                    item { EmptyState() }
                } else {
                    val d: DiffResult = diff
                    item { SummaryHeader(d.summary(), d.truncated) }
                    if (d.addedPanels.isNotEmpty()) {
                        item { SectionHeader("Added in B", d.addedPanels.size, ADDED_COLOR) }
                        items(d.addedPanels, key = { "added-${it.panel.panelId}" }) { row ->
                            PanelRow(position = row.panel.position, panelId = row.panel.panelId, tabCount = row.panel.tabCount, kind = DiffKind.ADDED)
                        }
                    }
                    if (d.removedPanels.isNotEmpty()) {
                        item { SectionHeader("Removed from B", d.removedPanels.size, REMOVED_COLOR) }
                        items(d.removedPanels, key = { "removed-${it.panel.panelId}" }) { row ->
                            PanelRow(position = row.panel.position, panelId = row.panel.panelId, tabCount = row.panel.tabCount, kind = DiffKind.REMOVED)
                        }
                    }
                    if (d.movedPanels.isNotEmpty()) {
                        item { SectionHeader("Moved", d.movedPanels.size, MOVED_COLOR) }
                        items(d.movedPanels, key = { "moved-${it.panelId}" }) { row ->
                            MovedRow(panelId = row.panelId, from = row.from, to = row.to, tabCountDelta = row.tabCountDelta)
                        }
                    }
                    if (d.priorityChangedPanels.isNotEmpty()) {
                        item { SectionHeader("Priority changed", d.priorityChangedPanels.size, MOVED_COLOR) }
                        items(d.priorityChangedPanels, key = { "prio-${it.panelId}" }) { row ->
                            PriorityRow(panelId = row.panelId, position = row.position, from = row.fromPriority, to = row.toPriority)
                        }
                    }
                    if (d.orientationChanged || d.projectChanged || d.tabCountDelta != 0) {
                        item { SectionHeader("Counters", 0, COUNTERS_COLOR) }
                        if (d.orientationChanged) {
                            item {
                                CounterRow("Split orientation", "${d.orientationFrom} -> ${d.orientationTo}")
                            }
                        }
                        if (d.projectChanged) {
                            item {
                                CounterRow("Project path", "${d.projectFrom ?: "(none)"} -> ${d.projectTo ?: "(none)"}")
                            }
                        }
                        item {
                            val sign = if (d.tabCountDelta > 0) "+" else ""
                            CounterRow("Tab count delta", "${sign}${d.tabCountDelta}  (A: ${d.aTabCount}, B: ${d.bTabCount})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    label: String,
    origin: String?,
    choices: List<SnapshotChoice>,
    labelFor: (String?) -> String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.width(64.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colors.surface, RoundedCornerShape(4.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = labelFor(origin),
                    fontSize = 12.sp,
                    color = MaterialTheme.colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                if (choices.isEmpty()) {
                    DropdownMenuItem(
                        onClick = { expanded = false },
                        enabled = false,
                    ) {
                        Text("No saved workspaces yet", fontSize = 11.sp)
                    }
                } else {
                    choices.forEach { c ->
                        DropdownMenuItem(
                            onClick = {
                                onSelected(c.origin)
                                expanded = false
                            },
                        ) {
                            Text(c.label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(onRun: () -> Unit, onCopyJson: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Button(
            onClick = onRun,
            modifier = Modifier.height(28.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary),
        ) {
            Text("Compare", fontSize = 12.sp, color = MaterialTheme.colors.onPrimary)
        }
        TextButton(
            onClick = onCopyJson,
            modifier = Modifier.height(28.dp),
        ) {
            Icon(
                imageVector = Icons.Default.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Export JSON", fontSize = 11.sp)
        }
    }
}

@Composable
private fun SaveRow(component: WorkspaceDiffComponent) {
    val name by component.saveName.collectAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { component.setSaveName(it) },
            placeholder = { Text("Snapshot name", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Button(
            onClick = { component.submitSave() },
            enabled = name.isNotBlank(),
            modifier = Modifier.height(32.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colors.onPrimary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save", fontSize = 11.sp, color = MaterialTheme.colors.onPrimary)
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Pick two workspaces and press Compare",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun SummaryHeader(summary: String, truncated: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (truncated) {
            Text(
                text = "truncated",
                fontSize = 10.sp,
                color = BossThemeColors.WarningColor,
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.8f),
        )
        if (count > 0) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($count)",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

private enum class DiffKind { ADDED, REMOVED }

@Composable
private fun PanelRow(position: String, panelId: String, tabCount: Int, kind: DiffKind) {
    val bg = when (kind) {
        DiffKind.ADDED -> ADDED_COLOR.copy(alpha = 0.12f)
        DiffKind.REMOVED -> REMOVED_COLOR.copy(alpha = 0.12f)
    }
    val accent = when (kind) {
        DiffKind.ADDED -> ADDED_COLOR
        DiffKind.REMOVED -> REMOVED_COLOR
    }
    val sign = when (kind) {
        DiffKind.ADDED -> "+"
        DiffKind.REMOVED -> "-"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$sign $position",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = panelId,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "$tabCount tabs",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun MovedRow(panelId: String, from: String, to: String, tabCountDelta: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MOVED_COLOR.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MOVED_COLOR),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$from -> $to",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = panelId,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val sign = if (tabCountDelta > 0) "+" else ""
        Text(
            text = "${sign}${tabCountDelta} tabs",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun PriorityRow(panelId: String, position: String, from: Int, to: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MOVED_COLOR.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MOVED_COLOR),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$position",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = panelId,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "prio $from -> $to",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CounterRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ADDED_COLOR = Color(0xFF4CAF50)
private val REMOVED_COLOR = Color(0xFFEF5350)
private val MOVED_COLOR = Color(0xFFFFB300)
private val COUNTERS_COLOR = Color(0xFF42A5F5)
