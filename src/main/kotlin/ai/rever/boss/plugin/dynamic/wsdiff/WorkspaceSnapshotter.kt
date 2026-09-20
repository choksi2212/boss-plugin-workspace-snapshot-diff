package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.workspace.LayoutWorkspace

/**
 * The well-known origin labels the snapshotter stamps onto a
 * [WorkspaceSnapshot]. Anything read from the live [WorkspaceDataProvider]
 * carries one of these.
 *
 * They are stable strings so the panel UI and the MCP tools can match
 * without coordinating beyond the constants below.
 */
internal object SnapshotOrigins {
    const val CURRENT = "current"
    const val LAST_SESSION = "last-session"

    /**
     * True when [origin] is one of the live (non-saved) origins. The
     * diff UI uses this to prefix a "live" pill so a user does not
     * confuse the live workspace with a saved one of the same name.
     */
    fun isLive(origin: String): Boolean = origin == CURRENT || origin == LAST_SESSION
}

/**
 * Read the live [WorkspaceDataProvider] and turn every workspace plus the
 * "current" and "last-session" entries into a [WorkspaceSnapshot].
 *
 * The result is bounded at [MAX_SNAPSHOTS]: when the provider exposes
 * more than that, the oldest saved entries are dropped. Live entries
 * (`current`, `last-session`) are always kept regardless of the cap.
 *
 * @throws IllegalStateException when [provider] is null - the panel UI
 *   and the MCP tools both gate on this.
 */
internal class WorkspaceSnapshotter(
    private val provider: WorkspaceDataProvider,
) {
    /** Snapshot every saved workspace plus the live "current" entry. */
    fun snapshotAll(): List<WorkspaceSnapshot> {
        val live = provider.currentWorkspace.value
        val saved = provider.workspaces.value

        val savedSnapshots = saved
            .asSequence()
            .map { it.toSnapshot(origin = it.name) }
            .toList()

        val liveSnapshot: WorkspaceSnapshot? = live?.let { ws ->
            val origin = if (isLastSessionName(ws.name)) SnapshotOrigins.LAST_SESSION
            else SnapshotOrigins.CURRENT
            ws.toSnapshot(origin = origin)
        }

        val all = mutableListOf<WorkspaceSnapshot>()
        if (liveSnapshot != null) all.add(liveSnapshot)
        all.addAll(savedSnapshots)

        return if (all.size <= MAX_SNAPSHOTS) all
        else enforceCap(all)
    }

    /** Snapshot a single named workspace, or return null if it is gone. */
    fun snapshotByName(name: String): WorkspaceSnapshot? {
        val live = provider.currentWorkspace.value
        if (live != null && live.name == name) {
            val origin = if (isLastSessionName(name)) SnapshotOrigins.LAST_SESSION
            else SnapshotOrigins.CURRENT
            return live.toSnapshot(origin = origin)
        }
        val saved = provider.workspaces.value.firstOrNull { it.name == name }
            ?: return null
        return saved.toSnapshot(origin = saved.name)
    }

    /**
     * Save the current workspace under a fresh name and return its
     * snapshot. Returns null when there is no current workspace, the
     * name is blank, or the provider refuses the save.
     *
     * The current implementation delegates to
     * [WorkspaceDataProvider.saveCurrentWorkspace] which the host
     * already implements; the spec asked for an explicit "save as
     * snapshot" verb, so the panel UI calls this with a name the user
     * types and the live state is recorded for later diffing.
     */
    fun saveCurrentAsSnapshot(name: String): WorkspaceSnapshot? {
        val trimmed = name.trim().take(MAX_LABEL_LENGTH)
        if (trimmed.isEmpty()) return null
        val live = provider.currentWorkspace.value ?: return null
        val saved: LayoutWorkspace? = provider.saveCurrentWorkspace(trimmed)
        if (saved == null) return null
        return saved.toSnapshot(origin = saved.name)
    }

    /** Apply the cap by dropping the oldest saved (non-live) entries. */
    private fun enforceCap(snapshots: List<WorkspaceSnapshot>): List<WorkspaceSnapshot> {
        if (snapshots.size <= MAX_SNAPSHOTS) return snapshots
        val live = snapshots.filter { SnapshotOrigins.isLive(it.origin) }
        val saved = snapshots.filterNot { SnapshotOrigins.isLive(it.origin) }
        val keepSaved = (MAX_SNAPSHOTS - live.size).coerceAtLeast(0)
        return live + saved.take(keepSaved)
    }

    private fun isLastSessionName(name: String): Boolean =
        name.equals("Last Session", ignoreCase = true) ||
            name.equals("last-session", ignoreCase = true)
}
