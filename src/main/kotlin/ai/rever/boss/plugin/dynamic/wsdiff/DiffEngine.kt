package ai.rever.boss.plugin.dynamic.wsdiff

/**
 * One panel that appears in B but is absent from A. The full
 * [PanelSnapshot] is carried so the panel UI can render its position
 * and tab count without re-reading the source.
 */
data class AddedPanel(val panel: PanelSnapshot)

/**
 * One panel that was in A but is absent from B.
 */
data class RemovedPanel(val panel: PanelSnapshot)

/**
 * One panel that exists in both A and B but moved to a different
 * position label.
 */
data class MovedPanel(val panelId: String, val from: String, val to: String, val tabCountDelta: Int)

/**
 * One panel that exists in both A and B at the same position but its
 * [WorkspaceSnapshot.panels] priority changed.
 */
data class PriorityChangedPanel(val panelId: String, val position: String, val fromPriority: Int, val toPriority: Int)

/**
 * The full structural diff between two snapshots.
 *
 * Every list is bounded at [MAX_DIFF_ENTRIES] so a deeply nested split
 * tree cannot produce a UI that scrolls forever. The counters
 * ([tabCountDelta], [orientationChanged], [projectChanged]) are
 * independent of the lists and always reflect the full comparison.
 */
data class DiffResult(
    val aId: String,
    val aName: String,
    val bId: String,
    val bName: String,
    val aPanelCount: Int,
    val bPanelCount: Int,
    val aTabCount: Int,
    val bTabCount: Int,
    val tabCountDelta: Int,
    val addedPanels: List<AddedPanel>,
    val removedPanels: List<RemovedPanel>,
    val movedPanels: List<MovedPanel>,
    val priorityChangedPanels: List<PriorityChangedPanel>,
    val orientationChanged: Boolean,
    val orientationFrom: String,
    val orientationTo: String,
    val projectChanged: Boolean,
    val projectFrom: String?,
    val projectTo: String?,
    val truncated: Boolean,
) {
    /** One-line verdict for the summary header. */
    fun summary(): String {
        val parts = mutableListOf<String>()
        if (addedPanels.isNotEmpty()) parts += "${addedPanels.size} added"
        if (removedPanels.isNotEmpty()) parts += "${removedPanels.size} removed"
        if (movedPanels.isNotEmpty()) parts += "${movedPanels.size} moved"
        if (priorityChangedPanels.isNotEmpty()) parts += "${priorityChangedPanels.size} priority changed"
        if (orientationChanged) parts += "split changed (${orientationFrom.lowercase()} -> ${orientationTo.lowercase()})"
        if (projectChanged) parts += "project changed"
        if (tabCountDelta != 0) {
            val sign = if (tabCountDelta > 0) "+" else ""
            parts += "${sign}${tabCountDelta} tabs"
        }
        if (parts.isEmpty()) return "No structural changes between $aName and $bName"
        return "B ($bName) vs A ($aName): ${parts.joinToString(", ")}"
    }
}

/**
 * Pure-function diff between two snapshots.
 *
 * Matching is by panel id. A panel that exists in both is "moved" if
 * its position label changed and "priority changed" if the position is
 * the same but the priority differs. A panel that exists in only one
 * side is "added" or "removed" depending on which side carries it.
 *
 * Returns an empty [DiffResult] when the two snapshots are structurally
 * identical. The result is bounded at [MAX_DIFF_ENTRIES] across all four
 * per-panel lists; [DiffResult.truncated] is set when that cap is hit.
 */
object DiffEngine {
    fun diff(a: WorkspaceSnapshot, b: WorkspaceSnapshot): DiffResult {
        val aById: Map<String, PanelSnapshot> = a.panels.associateBy { it.panelId }
        val bById: Map<String, PanelSnapshot> = b.panels.associateBy { it.panelId }

        val added = mutableListOf<AddedPanel>()
        val removed = mutableListOf<RemovedPanel>()
        val moved = mutableListOf<MovedPanel>()
        val priorityChanged = mutableListOf<PriorityChangedPanel>()

        var truncated = false

        for ((id, bp) in bById) {
            val ap = aById[id]
            if (ap == null) {
                if (added.size < MAX_DIFF_ENTRIES) added.add(AddedPanel(bp))
                else truncated = true
            } else if (ap.position != bp.position) {
                if (moved.size < MAX_DIFF_ENTRIES) {
                    moved.add(
                        MovedPanel(
                            panelId = id,
                            from = ap.position,
                            to = bp.position,
                            tabCountDelta = bp.tabCount - ap.tabCount,
                        )
                    )
                } else truncated = true
            } else if (ap.priority != bp.priority) {
                if (priorityChanged.size < MAX_DIFF_ENTRIES) {
                    priorityChanged.add(
                        PriorityChangedPanel(
                            panelId = id,
                            position = bp.position,
                            fromPriority = ap.priority,
                            toPriority = bp.priority,
                        )
                    )
                } else truncated = true
            }
        }

        for ((id, ap) in aById) {
            if (bById.containsKey(id)) continue
            if (removed.size < MAX_DIFF_ENTRIES) removed.add(RemovedPanel(ap))
            else truncated = true
        }

        val orientationChanged = a.orientation != b.orientation
        val projectChanged = a.projectPath != b.projectPath

        return DiffResult(
            aId = a.id,
            aName = a.name,
            bId = b.id,
            bName = b.name,
            aPanelCount = a.panelCount,
            bPanelCount = b.panelCount,
            aTabCount = a.tabCount,
            bTabCount = b.tabCount,
            tabCountDelta = b.tabCount - a.tabCount,
            addedPanels = added,
            removedPanels = removed,
            movedPanels = moved,
            priorityChangedPanels = priorityChanged,
            orientationChanged = orientationChanged,
            orientationFrom = a.orientation.lowercase(),
            orientationTo = b.orientation.lowercase(),
            projectChanged = projectChanged,
            projectFrom = a.projectPath,
            projectTo = b.projectPath,
            truncated = truncated,
        )
    }
}
