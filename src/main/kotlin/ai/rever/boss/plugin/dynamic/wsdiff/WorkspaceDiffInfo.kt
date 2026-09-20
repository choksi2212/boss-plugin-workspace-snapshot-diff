package ai.rever.boss.plugin.dynamic.wsdiff

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Layout

/**
 * Describes the Workspace Diff panel: id, sidebar icon, default slot.
 *
 * Lives in the left bottom slot at priority 86 so it sorts above the
 * Keyboard Shortcut Explorer (80) but below the heavier helper panels
 * that ship with the host.
 */
object WorkspaceDiffInfo : PanelInfo {
    override val id = PanelId("workspace-snapshot-diff", 86)
    override val displayName = "Workspace Diff"
    override val icon = FeatherIcons.Layout
    override val defaultSlotPosition = left.bottom
}
