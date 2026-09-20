package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.ClipboardProvider
import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.WorkspaceDataProvider

/**
 * Workspace Snapshot Diff dynamic plugin.
 *
 * The plugin shows a side panel with two workspace pickers and a
 * "Compare" button; the result is a structural diff between the two
 * saved workspaces. The same comparison is reachable from in-terminal
 * agents through four `workspace_*` MCP tools.
 *
 * The panel lives in the left sidebar (bottom slot, priority 86).
 */
class WorkspaceDiffDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.wsdiff"
    override val displayName: String = "Workspace Diff"
    override val version: String = manifestVersion()
    override val description: String =
        "Visual diff between two saved BOSS workspaces - panels added/removed/moved, " +
            "tab count delta, split orientation change, project change. The first " +
            "workspace-diff tool for BOSS."
    override val author: String = "Choksi"
    override val url: String = "https://github.com/choksi2212/boss-plugin-workspace-snapshot-diff"

    private var mcpProvider: WorkspaceDiffMcpToolProvider? = null
    private var workspaceProvider: WorkspaceDataProvider? = null
    private var clipboardProvider: ClipboardProvider? = null

    override fun register(context: PluginContext) {
        workspaceProvider = context.workspaceDataProvider
        clipboardProvider = context.clipboardProvider

        context.panelRegistry.registerPanel(WorkspaceDiffInfo) { ctx, panelInfo ->
            WorkspaceDiffComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                provider = workspaceProvider,
                clipboardProvider = clipboardProvider,
            )
        }

        val toolProvider = WorkspaceDiffMcpToolProvider(
            providerId = pluginId,
            provider = workspaceProvider,
        )
        mcpProvider = toolProvider
        context.registerMcpToolProvider(toolProvider)
    }

    override fun dispose() {
        mcpProvider = null
        workspaceProvider = null
        clipboardProvider = null
    }

    /**
     * The version from this plugin's own manifest.
     *
     * Every BOSS plugin ships `/META-INF/boss-plugin/plugin.json` at the
     * same resource path, so a `getResourceAsStream` that returns the
     * first hit could read someone else's manifest if the host ever loads
     * plugins through a parent-first classloader. Only the entry that
     * names this plugin id is accepted.
     */
    private fun manifestVersion(): String =
        runCatching {
            javaClass.classLoader
                ?.getResources("META-INF/boss-plugin/plugin.json")
                ?.asSequence()
                ?.mapNotNull { url -> runCatching { url.readText() }.getOrNull() }
                ?.firstOrNull { text -> field(text, "pluginId") == pluginId }
                ?.let { text -> field(text, "version") }
        }.getOrNull() ?: "unknown"

    private fun field(manifest: String, name: String): String? =
        Regex(""""$name"\s*:\s*"([^"]+)"""").find(manifest)?.groupValues?.get(1)
}
