# Agent board

One note per agent in `Agents/`, made from `Templates/Agent.md`. The table below reads their properties
(`status`, `task`, `branch`, `pr`, `versions`, `owns`), so keeping each note's properties current keeps the board
current. Finished ones move to `Agents/Done/`.

![[Board.base]]

**Status values:** `running` (editing), `in-review` (PR open, waiting for Andy), `blocked` (waiting on an answer),
`merged`.

**Rule:** at most three `running` agents, and no file in two `owns:` lists. Agents without Obsidian check with:

```bash
grep -A12 '^status: \(running\|in-review\|blocked\)' docs/vault/Agents/*.md
```

See [[How agents use this vault]].
