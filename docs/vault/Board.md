# Agent board

Every agent writes its note straight into the live folder, `E:/Jaymo Vault Live/Agents/` (outside git; it shows here as
`Live/Agents/`), so the table shows an agent the moment it starts. Notes are made from `Templates/Agent.md`; the
table reads their properties (`status`, `task`, `branch`, `pr`, `versions`, `owns`). Finished ones move to
`Live/Agents/Done/`.

![[Board.base]]

(The table's definition, `Board.base`, also lives in the live folder, so changing its columns or sorting in
Obsidian never blocks a `git pull`. Its default is `docs/vault-settings/Board.base`.)

**Status values:** `running` (editing), `in-review` (PR open, waiting for Andy), `blocked` (waiting on an answer),
`merged`.

**Tasks waiting for an agent** are in `Live/Inbox/` (posted by the coordinator, see [[How agents use this vault]]).
`python tools/vault_sync.py --status` lists the live agents and open tasks from any checkout, cloud included.

**Rule:** at most three `running` agents, and no file in two `owns:` lists. Agents without Obsidian check with:

```bash
grep -A14 '^status: \(running\|in-review\|blocked\)' "/e/Jaymo Vault Live/Agents/"*.md
```

See [[How agents use this vault]].
