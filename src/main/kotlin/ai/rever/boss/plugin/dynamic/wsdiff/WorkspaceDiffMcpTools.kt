package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import ai.rever.boss.plugin.api.WorkspaceDataProvider

/**
 * MCP tools contributed by the Workspace Snapshot Diff plugin.
 *
 * Four tools are exposed while the plugin is active:
 *
 * - `workspace_list_snapshots` - every snapshot the host's workspace
 *   manager exposes right now, with id, name, origin, projectPath and
 *   counts. Read-only.
 * - `workspace_snapshot(name)` - one snapshot by name, or null when no
 *   such name exists. Read-only.
 * - `workspace_diff(a, b)` - the full [DiffResult] for two snapshots,
 *   serialised as JSON. Read-only.
 * - `workspace_diff_summary(a, b)` - the one-line summary the panel
 *   header prints. Read-only.
 *
 * Tools that take a workspace name accept the value the
 * `workspace_list_snapshots` tool returns in `origin` (so a caller
 * can string-copy from one tool's output to another's input).
 */
internal class WorkspaceDiffMcpToolProvider(
    override val providerId: String,
    private val provider: WorkspaceDataProvider?,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "workspace_list_snapshots",
            description = "List every workspace snapshot BOSS exposes right now, " +
                "including the live 'current' and 'last-session' entries. Returns one JSON " +
                "object per snapshot with id, name, origin, projectPath, panelCount, tabCount and orientation.",
            handler = McpToolHandler { listSnapshots() },
        ),
        McpToolDefinition(
            name = "workspace_snapshot",
            description = "Return one snapshot by name, or an error if it does not exist. " +
                "Accepts the 'origin' value from workspace_list_snapshots.",
            inputSchema = NAME_SCHEMA,
            handler = McpToolHandler { args -> oneSnapshot(args) },
        ),
        McpToolDefinition(
            name = "workspace_diff",
            description = "Diff two workspace snapshots by name. Returns the structural diff " +
                "(added / removed / moved / priority-changed panels, plus tab-count delta, " +
                "orientation change and project path change) as JSON. Accepts the 'origin' " +
                "values from workspace_list_snapshots.",
            inputSchema = TWO_NAMES_SCHEMA,
            handler = McpToolHandler { args -> fullDiff(args) },
        ),
        McpToolDefinition(
            name = "workspace_diff_summary",
            description = "Same comparison as workspace_diff, but returns only the one-line " +
                "summary string the panel header prints.",
            inputSchema = TWO_NAMES_SCHEMA,
            handler = McpToolHandler { args -> summaryDiff(args) },
        ),
    )

    private suspend fun listSnapshots(): McpToolResult {
        val p = provider ?: return unavailable()
        val snap = WorkspaceSnapshotter(p).snapshotAll()
        val body = snap.joinToString(",", prefix = "[", postfix = "]") { s ->
            "{\"id\":\"${esc(s.id)}\",\"name\":\"${esc(s.name)}\"," +
                "\"origin\":\"${esc(s.origin)}\"," +
                "\"projectPath\":${s.projectPath?.let { "\"${esc(it)}\"" } ?: "null"}," +
                "\"panelCount\":${s.panelCount},\"tabCount\":${s.tabCount}," +
                "\"orientation\":\"${esc(s.orientation)}\"}"
        }
        return McpToolResult(body)
    }

    private suspend fun oneSnapshot(args: McpToolArgs): McpToolResult {
        val p = provider ?: return unavailable()
        val name = args.string("name")
            ?: return McpToolResult("Missing required argument: name", isError = true)
        val snap = WorkspaceSnapshotter(p).snapshotByName(name)
            ?: return McpToolResult("Workspace not found: $name", isError = true)
        return McpToolResult(snapshotJson(snap))
    }

    private suspend fun fullDiff(args: McpToolArgs): McpToolResult {
        val p = provider ?: return unavailable()
        val aName = args.string("a")
            ?: return McpToolResult("Missing required argument: a", isError = true)
        val bName = args.string("b")
            ?: return McpToolResult("Missing required argument: b", isError = true)
        if (aName == bName) {
            return McpToolResult("Workspace A and B must differ", isError = true)
        }
        val snapper = WorkspaceSnapshotter(p)
        val a = snapper.snapshotByName(aName)
            ?: return McpToolResult("Workspace not found: $aName", isError = true)
        val b = snapper.snapshotByName(bName)
            ?: return McpToolResult("Workspace not found: $bName", isError = true)
        return McpToolResult(WorkspaceDiffViewModel(provider).also { it.refreshSnapshots() }.let { vm ->
            // The view model's runDiff path also surfaces a status - bypass it
            // by computing the diff inline so the MCP tool returns only the JSON.
            val r = DiffEngine.diff(a, b)
            diffJsonForTool(r)
        })
    }

    private suspend fun summaryDiff(args: McpToolArgs): McpToolResult {
        val p = provider ?: return unavailable()
        val aName = args.string("a")
            ?: return McpToolResult("Missing required argument: a", isError = true)
        val bName = args.string("b")
            ?: return McpToolResult("Missing required argument: b", isError = true)
        if (aName == bName) {
            return McpToolResult("Workspace A and B must differ", isError = true)
        }
        val snapper = WorkspaceSnapshotter(p)
        val a = snapper.snapshotByName(aName)
            ?: return McpToolResult("Workspace not found: $aName", isError = true)
        val b = snapper.snapshotByName(bName)
            ?: return McpToolResult("Workspace not found: $bName", isError = true)
        return McpToolResult(DiffEngine.diff(a, b).summary())
    }

    private fun snapshotJson(s: WorkspaceSnapshot): String {
        val panels = s.panels.joinToString(",", prefix = "[", postfix = "]") { p ->
            "{\"panelId\":\"${esc(p.panelId)}\",\"position\":\"${esc(p.position)}\"," +
                "\"tabCount\":${p.tabCount},\"priority\":${p.priority}}"
        }
        return "{\"id\":\"${esc(s.id)}\",\"name\":\"${esc(s.name)}\"," +
            "\"origin\":\"${esc(s.origin)}\"," +
            "\"projectPath\":${s.projectPath?.let { "\"${esc(it)}\"" } ?: "null"}," +
            "\"panelCount\":${s.panelCount},\"tabCount\":${s.tabCount}," +
            "\"orientation\":\"${esc(s.orientation)}\",\"panels\":$panels}"
    }

    private fun diffJsonForTool(d: DiffResult): String {
        val added = d.addedPanels.joinToString(",", prefix = "[", postfix = "]") { p ->
            "{\"panelId\":\"${esc(p.panel.panelId)}\",\"position\":\"${esc(p.panel.position)}\",\"tabCount\":${p.panel.tabCount}}"
        }
        val removed = d.removedPanels.joinToString(",", prefix = "[", postfix = "]") { p ->
            "{\"panelId\":\"${esc(p.panel.panelId)}\",\"position\":\"${esc(p.panel.position)}\",\"tabCount\":${p.panel.tabCount}}"
        }
        val moved = d.movedPanels.joinToString(",", prefix = "[", postfix = "]") { m ->
            "{\"panelId\":\"${esc(m.panelId)}\",\"from\":\"${esc(m.from)}\",\"to\":\"${esc(m.to)}\",\"tabCountDelta\":${m.tabCountDelta}}"
        }
        val prio = d.priorityChangedPanels.joinToString(",", prefix = "[", postfix = "]") { p ->
            "{\"panelId\":\"${esc(p.panelId)}\",\"position\":\"${esc(p.position)}\",\"from\":${p.fromPriority},\"to\":${p.toPriority}}"
        }
        return "{\"a\":\"${esc(d.aName)}\",\"b\":\"${esc(d.bName)}\"," +
            "\"aPanelCount\":${d.aPanelCount},\"bPanelCount\":${d.bPanelCount}," +
            "\"aTabCount\":${d.aTabCount},\"bTabCount\":${d.bTabCount}," +
            "\"tabCountDelta\":${d.tabCountDelta}," +
            "\"added\":$added,\"removed\":$removed,\"moved\":$moved,\"priorityChanged\":$prio," +
            "\"orientationChanged\":${d.orientationChanged}," +
            "\"orientationFrom\":\"${esc(d.orientationFrom)}\",\"orientationTo\":\"${esc(d.orientationTo)}\"," +
            "\"projectChanged\":${d.projectChanged}," +
            "\"projectFrom\":${d.projectFrom?.let { "\"${esc(it)}\"" } ?: "null"}," +
            "\"projectTo\":${d.projectTo?.let { "\"${esc(it)}\"" } ?: "null"}," +
            "\"truncated\":${d.truncated}," +
            "\"summary\":\"${esc(d.summary())}\"}"
    }

    private fun unavailable(): McpToolResult =
        McpToolResult("Workspace data provider unavailable in this context.", isError = true)

    private fun esc(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    private companion object {
        const val NAME_SCHEMA =
            """{"type":"object","properties":{"name":{"type":"string","description":"Workspace name (or the 'origin' value from workspace_list_snapshots)."}},"required":["name"]}"""
        const val TWO_NAMES_SCHEMA =
            """{"type":"object","properties":{"a":{"type":"string","description":"Workspace A name or origin."},"b":{"type":"string","description":"Workspace B name or origin."}},"required":["a","b"]}"""
    }
}
