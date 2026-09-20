package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The Decompose panel component for the Workspace Snapshot Diff.
 *
 * Owns the [WorkspaceDiffViewModel], a clipboard channel for the
 * "Export diff as JSON" action, and the small bit of Compose state
 * the panel UI needs (the "Save as snapshot" name field, the export
 * confirmation toast).
 */
class WorkspaceDiffComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val provider: WorkspaceDataProvider?,
    private val clipboardProvider: ClipboardProvider?,
) : PanelComponentWithUI, ComponentContext by ctx {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val viewModel: WorkspaceDiffViewModel = WorkspaceDiffViewModel(provider)

    private val _saveName = MutableStateFlow("")
    val saveName: StateFlow<String> = _saveName.asStateFlow()

    private val _exported = MutableStateFlow<String?>(null)
    val exported: StateFlow<String?> = _exported.asStateFlow()

    fun refresh() = viewModel.refreshSnapshots()

    fun setA(origin: String) = viewModel.setA(origin)
    fun setB(origin: String) = viewModel.setB(origin)
    fun swap() = viewModel.swap()
    fun runDiff() = viewModel.runDiff()

    fun setSaveName(text: String) {
        _saveName.value = text.take(MAX_LABEL_LENGTH)
    }

    fun submitSave() {
        val name = _saveName.value
        val origin = viewModel.saveSnapshot(name)
        if (origin != null) {
            _saveName.value = ""
            viewModel.setB(origin)
        }
    }

    fun copyDiffJson() {
        val text = viewModel.diffAsJson()
        if (text == null) {
            viewModel.reportError("Run a diff before exporting")
            return
        }
        val cp = clipboardProvider ?: run {
            viewModel.reportError("Clipboard provider unavailable")
            return
        }
        scope.launch {
            if (cp.setText(text)) {
                viewModel.reportStatus("Diff JSON copied")
            } else {
                viewModel.reportError("Clipboard write failed")
            }
        }
    }

    fun copyWorkspaceJson(origin: String) {
        val json = viewModel.exportSnapshotAsJson(origin)
        if (json == null) {
            viewModel.reportError("Workspace no longer available")
            return
        }
        val cp = clipboardProvider ?: run {
            viewModel.reportError("Clipboard provider unavailable")
            return
        }
        scope.launch {
            if (cp.setText(json)) {
                viewModel.reportStatus("Workspace JSON copied")
            } else {
                viewModel.reportError("Clipboard write failed")
            }
        }
    }

    fun dismissExport() {
        _exported.value = null
    }

    fun clearMessages() = viewModel.clearMessages()

    @Composable
    override fun Content() {
        WorkspaceDiffContent(component = this)
    }
}
