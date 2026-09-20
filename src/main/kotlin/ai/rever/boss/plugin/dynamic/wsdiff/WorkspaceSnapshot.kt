package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.PanelConfig
import ai.rever.boss.plugin.workspace.SplitConfig
import ai.rever.boss.plugin.workspace.extractPanels

/**
 * One panel inside a workspace, normalized for diffing.
 *
 * Two snapshots can be compared without sharing id strings: the panel id
 * is preserved as [panelId], the structural position (which side of which
 * split) as [position], and the tab count as [tabCount]. [tabTypes] holds
 * the distinct tab types in declaration order so a "browser tab became a
 * terminal tab" change shows up in the diff even though the count stayed
 * the same.
 */
data class PanelSnapshot(
    val panelId: String,
    val position: String,
    val tabCount: Int,
    val tabTypes: List<String>,
    val priority: Int,
)

/**
 * A workspace reduced to a diff-friendly shape.
 *
 * The diff engine compares two of these: it does not need panel ids to
 * mean anything beyond "matched between snapshots", so [panels] is keyed
 * by panel id and the diff treats that key as the identity.
 *
 * [origin] names where the snapshot came from ("current", "last-session",
 * or a saved workspace name). [projectPath] is the absolute project path
 * the workspace targets, or null when the workspace is projectless.
 */
data class WorkspaceSnapshot(
    val id: String,
    val name: String,
    val origin: String,
    val projectPath: String?,
    val tabCount: Int,
    val panelCount: Int,
    val orientation: String,
    val panels: List<PanelSnapshot>,
)

/**
 * Stable kinds of split orientation the diff engine reports on.
 *
 * [NONE] means a single-panel workspace, [HORIZONTAL] means the top
 * panel is above the bottom panel (a `HorizontalSplit` at the root), and
 * [VERTICAL] means the left panel sits next to the right panel
 * (a `VerticalSplit` at the root). Mixed trees with deeper splits fall
 * back to one of these based on the root node alone.
 */
enum class SplitOrientation {
    NONE,
    HORIZONTAL,
    VERTICAL,
    ;

    companion object {
        /** Empty / null input collapses to [NONE]. Unknown enums also go to [NONE]. */
        fun fromName(name: String?): SplitOrientation = when (name) {
            "HORIZONTAL" -> HORIZONTAL
            "VERTICAL" -> VERTICAL
            else -> NONE
        }
    }
}

/**
 * Build a [WorkspaceSnapshot] from a live [LayoutWorkspace].
 *
 * Panel positions are read via the api's own [extractPanels] helper so
 * the labels match what the host's vertical bar prints, capped at
 * [MAX_PANELS_PER_WORKSPACE] to keep the diff bounded. Tab counts are
 * taken from each panel's tab list (capped at [MAX_TABS_PER_PANEL]).
 *
 * @param origin What this snapshot represents ("current", "last-session", or a saved name).
 * @param priorityFromIndex Each panel's 0-based position in [LayoutWorkspace]'s
 *   recursive walk becomes its diff priority - panels earlier in the walk have
 *   lower priority and are listed first in the rendered diff.
 */
internal fun LayoutWorkspace.toSnapshot(
    origin: String,
    priorityFromIndex: (Int) -> Int = { it },
): WorkspaceSnapshot {
    val panels = mutableListOf<PanelSnapshot>()
    var totalTabs = 0

    val allPanels: List<Pair<String, PanelConfig>> = layout.extractPanels()
        .mapNotNull { (label, _) ->
            findPanelByLabel(layout, label) ?: return@mapNotNull null
        }
        .take(MAX_PANELS_PER_WORKSPACE)

    allPanels.forEachIndexed { index, (label, panel) ->
        val tabCount = panel.tabs.size.coerceAtMost(MAX_TABS_PER_PANEL)
        totalTabs += tabCount
        val types = panel.tabs
            .asSequence()
            .take(MAX_TABS_PER_PANEL)
            .map { it.type }
            .distinct()
            .toList()
        panels.add(
            PanelSnapshot(
                panelId = panel.id.take(MAX_LABEL_LENGTH),
                position = label.take(MAX_LABEL_LENGTH),
                tabCount = tabCount,
                tabTypes = types,
                priority = priorityFromIndex(index),
            )
        )
    }

    val orientation = when (layout) {
        is SplitConfig.HorizontalSplit -> SplitOrientation.HORIZONTAL
        is SplitConfig.VerticalSplit -> SplitOrientation.VERTICAL
        is SplitConfig.SinglePanel -> SplitOrientation.NONE
    }

    return WorkspaceSnapshot(
        id = id.take(MAX_LABEL_LENGTH),
        name = name.take(MAX_LABEL_LENGTH),
        origin = origin.take(MAX_LABEL_LENGTH),
        projectPath = projectPath?.take(MAX_LABEL_LENGTH),
        tabCount = totalTabs,
        panelCount = panels.size,
        orientation = orientation.name,
        panels = panels,
    )
}

/**
 * Walk a [SplitConfig] tree and find the [PanelConfig] for the
 * human-readable label [extractPanels] produced.
 *
 * The host's extractor concatenates "Left " / "Right " / "Top " /
 * "Bottom " prefixes as it recurses, and the same labels appear here in
 * the same order. The first match wins; deeper panels are reached by
 * matching against progressively longer prefixes.
 */
private fun findPanelByLabel(root: SplitConfig, label: String): Pair<String, PanelConfig>? {
    val panels = mutableListOf<Pair<String, PanelConfig>>()
    walkPanels(root, label, "", panels)
    return panels.firstOrNull()
}

private fun walkPanels(
    node: SplitConfig,
    target: String,
    prefix: String,
    sink: MutableList<Pair<String, PanelConfig>>,
) {
    when (node) {
        is SplitConfig.SinglePanel -> {
            val full = if (prefix.isEmpty()) "Main Panel" else prefix.trim() + " Panel"
            if (full == target) sink.add(full to node.panel)
        }
        is SplitConfig.VerticalSplit -> {
            walkPanels(node.left, target, "$prefix Left ", sink)
            walkPanels(node.right, target, "$prefix Right ", sink)
        }
        is SplitConfig.HorizontalSplit -> {
            walkPanels(node.top, target, "$prefix Top ", sink)
            walkPanels(node.bottom, target, "$prefix Bottom ", sink)
        }
    }
}

/**
 * Hard upper bounds for the snapshotter and the diff engine. Centralized
 * here so the panel UI, the MCP tools and the snapshotter can never drift
 * on what "a workspace" can hold.
 */
internal const val MAX_PANELS_PER_WORKSPACE = 200
internal const val MAX_TABS_PER_PANEL = 100
internal const val MAX_DIFF_ENTRIES = 500
internal const val MAX_SNAPSHOTS = 1000
internal const val MAX_LABEL_LENGTH = 256
