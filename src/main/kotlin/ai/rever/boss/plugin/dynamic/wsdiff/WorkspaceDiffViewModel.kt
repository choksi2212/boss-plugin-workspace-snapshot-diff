package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.workspace.LayoutWorkspace
import ai.rever.boss.plugin.workspace.WorkspaceSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One selectable item in the Workspace A / B picker.
 *
 * [label] is what the user sees; [origin] matches the value
 * [WorkspaceSnapshotter] stamps onto a [WorkspaceSnapshot] so we can
 * resolve the chosen entry to its snapshot without re-implementing
 * provider access.
 */
data class SnapshotChoice(
    val label: String,
    val origin: String,
)

/**
 * State the panel UI renders.
 *
 * The view model owns: the list of [SnapshotChoice]s each picker shows,
 * the currently-selected A and B, the most recent [DiffResult] (or null
 * when nothing has been compared yet), the status / error toasts, and a
 * short message about the last save attempt.
 *
 * Re-snapshotting is cheap - the [WorkspaceDataProvider] lives in
 * memory - so [refreshSnapshots] is called on every panel open and on
 * every "Save current as snapshot" success, without watching the
 * provider's flows (the panel UI does that separately).
 */
class WorkspaceDiffViewModel(
    private val provider: WorkspaceDataProvider?,
) {

    private val _snapshots = MutableStateFlow<List<WorkspaceSnapshot>>(emptyList())
    val snapshots: StateFlow<List<WorkspaceSnapshot>> = _snapshots.asStateFlow()

    private val _aOrigin = MutableStateFlow<String?>(null)
    val aOrigin: StateFlow<String?> = _aOrigin.asStateFlow()

    private val _bOrigin = MutableStateFlow<String?>(null)
    val bOrigin: StateFlow<String?> = _bOrigin.asStateFlow()

    private val _diff = MutableStateFlow<DiffResult?>(null)
    val diff: StateFlow<DiffResult?> = _diff.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** True when the host has not provided a workspace manager. */
    val providerUnavailable: Boolean
        get() = provider == null

    init {
        refreshSnapshots()
    }

    /** Re-read the provider's workspace list and re-stamp the snapshots. */
    fun refreshSnapshots() {
        val p = provider ?: run {
            _error.value = "Workspace manager unavailable"
            return
        }
        val snap = WorkspaceSnapshotter(p).snapshotAll()
        _snapshots.value = snap
        if (_aOrigin.value == null && snap.isNotEmpty()) {
            _aOrigin.value = snap.first().origin
        }
        if (_bOrigin.value == null && snap.size > 1) {
            _bOrigin.value = snap[1].origin
        } else if (_bOrigin.value == null) {
            _bOrigin.value = _aOrigin.value
        }
    }

    /** Items to show in the A and B pickers, in stable order. */
    fun choices(): List<SnapshotChoice> = _snapshots.value.map { s ->
        val label = if (SnapshotOrigins.isLive(s.origin)) {
            when (s.origin) {
                SnapshotOrigins.CURRENT -> "Current (live)"
                SnapshotOrigins.LAST_SESSION -> "Last Session (live)"
                else -> s.origin
            }
        } else {
            s.name
        }
        SnapshotChoice(label = label, origin = s.origin)
    }

    /** Convenience for the panel UI. */
    fun labelFor(origin: String?): String =
        _snapshots.value.firstOrNull { it.origin == origin }?.let { s ->
            when (s.origin) {
                SnapshotOrigins.CURRENT -> "Current (live)"
                SnapshotOrigins.LAST_SESSION -> "Last Session (live)"
                else -> s.name
            }
        } ?: origin ?: "(none)"

    fun setA(origin: String) {
        _aOrigin.value = origin
        invalidateDiff()
    }

    fun setB(origin: String) {
        _bOrigin.value = origin
        invalidateDiff()
    }

    /** Clear the cached diff when the user changes either picker. */
    private fun invalidateDiff() {
        _diff.value = null
    }

    /** Run the diff engine on the currently-selected A and B. */
    fun runDiff() {
        val a = _aOrigin.value ?: return reportError("Pick a Workspace A first")
        val b = _bOrigin.value ?: return reportError("Pick a Workspace B first")
        if (a == b) return reportError("Workspace A and B must differ")

        val snapA = _snapshots.value.firstOrNull { it.origin == a }
            ?: return reportError("Workspace A no longer exists")
        val snapB = _snapshots.value.firstOrNull { it.origin == b }
            ?: return reportError("Workspace B no longer exists")

        val result = DiffEngine.diff(snapA, snapB)
        _diff.value = result
        _status.value = result.summary()
        _error.value = null
    }

    /** Swap A and B; recomputes the diff when one was already computed. */
    fun swap() {
        val a = _aOrigin.value ?: return
        val b = _bOrigin.value ?: return
        _aOrigin.value = b
        _bOrigin.value = a
        _diff.value?.let { _diff.value = DiffEngine.diff(_snapshots.value.first { it.origin == b }, _snapshots.value.first { it.origin == a }) }
    }

    /**
     * Render the current diff as a JSON string suitable for clipboard
     * copy or MCP response. Returns null when no diff has been computed.
     */
    fun diffAsJson(): String? {
        val d = _diff.value ?: return null
        return diffJson(d)
    }

    /**
     * Save the live workspace under [name] and add it to the picker.
     * Returns the new origin label on success, null on failure.
     */
    fun saveSnapshot(name: String): String? {
        val p = provider ?: return null.also { reportError("Workspace manager unavailable") }
        val trimmed = name.trim().take(MAX_LABEL_LENGTH)
        if (trimmed.isEmpty()) {
            reportError("Name cannot be empty")
            return null
        }
        if (_snapshots.value.any { it.name.equals(trimmed, ignoreCase = true) }) {
            reportError("A snapshot called '$trimmed' already exists")
            return null
        }
        val saved = WorkspaceSnapshotter(p).saveCurrentAsSnapshot(trimmed) ?: run {
            reportError("Could not save '$trimmed' - is there a current workspace?")
            return null
        }
        _snapshots.value = (_snapshots.value + saved).take(MAX_SNAPSHOTS)
        _status.value = "Saved snapshot '$trimmed'"
        _error.value = null
        return saved.origin
    }

    /** Resolve an origin to its [LayoutWorkspace] for export. */
    fun workspaceByOrigin(origin: String): LayoutWorkspace? {
        val p = provider ?: return null
        if (SnapshotOrigins.isLive(origin)) return p.currentWorkspace.value
        return p.workspaces.value.firstOrNull { it.name == origin }
    }

    fun exportSnapshotAsJson(origin: String): String? {
        val p = provider ?: return null
        val live = p.currentWorkspace.value
        if (SnapshotOrigins.isLive(origin) && live != null) {
            return WorkspaceSerializer.serialize(live)
        }
        val saved = p.workspaces.value.firstOrNull { it.name == origin }
            ?: return null
        return WorkspaceSerializer.serialize(saved)
    }

    fun clearMessages() {
        _status.value = null
        _error.value = null
    }

    fun reportError(text: String) {
        _error.value = text.take(MAX_LABEL_LENGTH)
    }

    fun reportStatus(text: String) {
        _status.value = text.take(MAX_LABEL_LENGTH)
        _error.value = null
    }

}

/**
 * Serialise a [DiffResult] as a JSON object. Hand-written to avoid
 * pulling in a JSON dependency inside the view model itself; the
 * plugin already ships kotlinx-serialization but the format here is
 * trivially small and stable enough to do without it.
 */
private fun diffJson(d: DiffResult): String {
    val sb = StringBuilder()
    sb.append('{')
    sb.appendJsonField("a", d.aName); sb.append(',')
    sb.appendJsonField("b", d.bName); sb.append(',')
    sb.appendRaw("\"aPanelCount\":${d.aPanelCount},")
    sb.appendRaw("\"bPanelCount\":${d.bPanelCount},")
    sb.appendRaw("\"aTabCount\":${d.aTabCount},")
    sb.appendRaw("\"bTabCount\":${d.bTabCount},")
    sb.appendRaw("\"tabCountDelta\":${d.tabCountDelta},")
    sb.appendRaw("\"added\":[")
    d.addedPanels.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.appendRaw("{\"panelId\":\"${escape(p.panel.panelId)}\",\"position\":\"${escape(p.panel.position)}\",\"tabCount\":${p.panel.tabCount}}")
    }
    sb.appendRaw("],")
    sb.appendRaw("\"removed\":[")
    d.removedPanels.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.appendRaw("{\"panelId\":\"${escape(p.panel.panelId)}\",\"position\":\"${escape(p.panel.position)}\",\"tabCount\":${p.panel.tabCount}}")
    }
    sb.appendRaw("],")
    sb.appendRaw("\"moved\":[")
    d.movedPanels.forEachIndexed { i, m ->
        if (i > 0) sb.append(',')
        sb.appendRaw("{\"panelId\":\"${escape(m.panelId)}\",\"from\":\"${escape(m.from)}\",\"to\":\"${escape(m.to)}\",\"tabCountDelta\":${m.tabCountDelta}}")
    }
    sb.appendRaw("],")
    sb.appendRaw("\"priorityChanged\":[")
    d.priorityChangedPanels.forEachIndexed { i, p ->
        if (i > 0) sb.append(',')
        sb.appendRaw("{\"panelId\":\"${escape(p.panelId)}\",\"position\":\"${escape(p.position)}\",\"from\":${p.fromPriority},\"to\":${p.toPriority}}")
    }
    sb.appendRaw("],")
    sb.appendJsonField("orientationChanged", d.orientationChanged.toString()); sb.append(',')
    sb.appendJsonField("orientationFrom", d.orientationFrom); sb.append(',')
    sb.appendJsonField("orientationTo", d.orientationTo); sb.append(',')
    sb.appendJsonField("projectChanged", d.projectChanged.toString()); sb.append(',')
    sb.appendJsonField("projectFrom", d.projectFrom ?: ""); sb.append(',')
    sb.appendJsonField("projectTo", d.projectTo ?: ""); sb.append(',')
    sb.appendJsonField("truncated", d.truncated.toString())
    sb.append('}')
    return sb.toString()
}

private fun StringBuilder.appendJsonField(key: String, value: String) {
    append('"').append(escape(key)).append("\":\"").append(escape(value)).append('"')
}

private fun StringBuilder.appendRaw(s: String) {
    append(s)
}

private fun escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
