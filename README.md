# Workspace Snapshot Diff

A BOSS plugin that shows the structural difference between two saved
workspaces. No plugin currently answers "what changed between workspace
X and Y?" - users see a saved workspace's name in the picker but not
its structural difference from the current one or another saved one.
This plugin is the first.

## What it does

The plugin adds a left-side panel (`Workspace Diff`) with two workspace
pickers and a `Compare` button. The result lists:

- panels that exist in B but not in A (green)
- panels that were in A but are absent from B (red)
- panels that moved position (amber)
- panels whose priority changed (amber)
- tab count delta (`+N tabs` vs A)
- split orientation change (`horizontal -> vertical`)
- project path change (`/path/a` -> `/path/b`)
- one-line verdict in the summary header

Bounded at 500 entries per diff section, with a `truncated` marker on
the summary when the cap is hit. Panels are capped at 200, tabs per
panel at 100, and the live snapshot ring is capped at 1,000 entries
(keeping the live `current` and `last-session` entries regardless).

## Why this is unique

BOSS exposes `WorkspaceManager.workspaces` and `currentWorkspace` but
no plugin currently compares them. The host's Space picker shows names
but not structural differences. This plugin is the first to do that
for both humans (the panel) and in-terminal agents (the MCP tools).

## MCP tools

The plugin contributes four tools on the `boss` MCP server while it
is active:

- `workspace_list_snapshots` - every snapshot the host exposes
- `workspace_snapshot(name)` - one snapshot by name
- `workspace_diff(a, b)` - full structural diff as JSON
- `workspace_diff_summary(a, b)` - one-line summary

All four are read-only. Each `name`, `a`, `b` argument accepts the
`origin` value from `workspace_list_snapshots`.

## Install

1. Build the jar: `./gradlew buildPluginJar`
2. The jar lands at
   `build/libs/boss-plugin-workspace-snapshot-diff-0.1.0.jar`.
3. Install it through the BOSS Toolbox: open the host's Toolbox,
   switch to the Plugins tab, click "Install from file", and pick the
   jar.

The plugin declares no extra dependencies beyond the BOSS plugin API
(1.0.93) and the Compose runtime.

## Save current as snapshot

The panel has a `Snapshot name` field and a `Save` button. Type a name
and press `Save`: the live workspace is recorded as a new saved
snapshot and added to both pickers, with the new entry selected as B
so the next click of `Compare` diffs against the new snapshot.

## License

MIT. See the repository for details.
