# AGENTS.md

## Project Overview

`boss-plugin-workspace-snapshot-diff` is a BOSS plugin that shows the
structural difference between two saved workspaces. It is plugin #18
in the same family's serial build (see sibling directories under
`/c/Users/niklaus/boss-plugins/`).

## Module Structure

- `src/main/kotlin/ai/rever/boss/plugin/dynamic/wsdiff/`
  - `WorkspaceDiffDynamicPlugin.kt` - plugin entrypoint, registers
    the panel and the MCP tools.
  - `WorkspaceDiffInfo.kt` - `PanelInfo` for the sidebar (left bottom,
    priority 86).
  - `WorkspaceDiffComponent.kt` - the Decompose panel component.
  - `WorkspaceDiffViewModel.kt` - state for the panel and the MCP
    tools.
  - `WorkspaceDiffContent.kt` - the Compose UI.
  - `WorkspaceSnapshot.kt` - normalized snapshot of a workspace.
  - `WorkspaceSnapshotter.kt` - reads `WorkspaceDataProvider` and
    builds snapshots.
  - `DiffEngine.kt` - pure-function diff between two snapshots.
  - `WorkspaceDiffMcpTools.kt` - MCP tools contributed via
    `context.registerMcpToolProvider`.
- `src/main/resources/META-INF/boss-plugin/plugin.json` - plugin
  manifest (`mixed` type, panel in left_bottom at priority 86).

## Snapshot shape

A `WorkspaceSnapshot` carries the structural info that matters for
diffing: id, name, origin (`current` / `last-session` / saved name),
project path, total tab count, panel count, split orientation, and
the list of `PanelSnapshot` (each with panel id, position label,
tab count, tab types, priority).

The snapshotter walks the live `LayoutWorkspace`'s split tree via the
api's own `extractPanels` helper so position labels match what the
host's vertical bar prints. Panels are capped at 200, tabs per panel
at 100, and the live snapshot ring is capped at 1,000 entries (with
the live entries always kept).

## Diff algorithm

`DiffEngine.diff(a, b)` is a pure function over two snapshots:

- panels present in B but absent from A go in `addedPanels`
- panels present in A but absent from B go in `removedPanels`
- panels with the same id but different position go in `movedPanels`
- panels with the same id and position but a changed priority go in
  `priorityChangedPanels`
- counters (`tabCountDelta`, `orientationChanged`, `projectChanged`)
  are computed independently of the lists

Every per-panel list is bounded at 500 entries; when any list would
exceed that cap, the diff engine drops the remaining entries and
sets `DiffResult.truncated = true` so the summary header can warn
the user.

## MCP tools

Four tools, all read-only:

- `workspace_list_snapshots` - one JSON object per snapshot
- `workspace_snapshot(name)` - one snapshot by name
- `workspace_diff(a, b)` - full diff as JSON
- `workspace_diff_summary(a, b)` - one-line summary

The `name` / `a` / `b` arguments accept the `origin` value
`workspace_list_snapshots` returns, so a caller can string-copy
between tools.

## Constraints

- `MAX_SNAPSHOTS` = 1,000 (drop oldest in-memory, live entries kept)
- `MAX_PANELS_PER_WORKSPACE` = 200
- `MAX_TABS_PER_PANEL` = 100
- `MAX_DIFF_ENTRIES` = 500 per diff section
- `MAX_LABEL_LENGTH` = 256 chars (truncate any string longer)

All caps are enforced in `WorkspaceSnapshot.kt` and `DiffEngine.kt`.

## Build

`./gradlew buildPluginJar -x test` produces
`build/libs/boss-plugin-workspace-snapshot-diff-0.1.0.jar`. The CI
workflow at `.github/workflows/test.yml` runs `./gradlew build` on
every pull request; the release workflow at
`.github/workflows/build.yml` (with `permissions: contents: write`)
delegates to the host's reusable release workflow on push to main.

## Compatibility

BOSS plugin API 1.0.93, host BOSS 9.4.2 or newer.
