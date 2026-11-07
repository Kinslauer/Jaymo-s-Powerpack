# Agent board

Every agent writes its note straight into the main checkout's `Live/Agents/` (`E:\Origins Mod\docs\vault\Live\Agents\`),
which git ignores, so the table shows an agent the moment it starts. Notes are made from `Templates/Agent.md`; the
table reads their properties (`status`, `task`, `branch`, `pr`, `versions`, `owns`). Finished ones move to
`Live/Agents/Done/`.

![[Board.base]]

**Status values:** `running` (editing), `in-review` (PR open, waiting for Andy), `blocked` (waiting on an answer),
`merged`.

**Rule:** at most three `running` agents, and no file in two `owns:` lists. Agents without Obsidian check with:

```bash
grep -A14 '^status: \(running\|in-review\|blocked\)' "/e/Origins Mod/docs/vault/Live/Agents/"*.md
```

See [[How agents use this vault]].
