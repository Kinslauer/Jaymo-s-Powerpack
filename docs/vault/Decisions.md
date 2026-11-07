# Decisions

Every decision Andy makes, newest **last**. Append with `Templates/Decision.md`. Standing rulings from before this
log started are in `docs/AI-HANDBOOK.md` section 7 (see [[Rules]]).

### 2026-11-06: project vault
- **Decided:** the vault lives in the repo at `docs/vault/`, committed and shared by every agent. Obsidian installed
  on the PC with winget.
- **Applies to:** all three mods; every agent session.
- **Agent:** [[Done/obsidian-vault-4d9705|obsidian-vault-4d9705]]

### 2026-11-06: agents work only in their own worktree
- **Decided:** every agent, Hermes included, works only in its own git worktree, never switches branches in a shared
  checkout, and never runs `git checkout -- .`, `git clean -f` or `git reset --hard` (Hermes lost unsaved work that way).
- **Applies to:** every agent session, every assistant.
- **Agent:** the project thread (PR #1100 review session).

### 2026-11-07: the hooded cloak is only for the RPG Series archer hoods
- **Decided:** "Just the RPG SERIES archer hoods." No cloak for the soothsayer or ichorcloth hoods: "this should not
  exist, i never asked for that". Every Archers hood tier gets one: "they all get hoods matching their respective
  desings" (each tier's cloak matches that hood's design).
- **Applies to:** Powers, `HoodedCloak`, `HoodedCloakLayer`, `tags/item/cloak_hoods.json`; PR #1104.
- **Agent:** [[Agents/handbook-hermes-rules|handbook-hermes-rules]]

### 2026-11-07: review lessons go in the handbook
- **Decided:** rules learned from reviewing Hermes's PRs go in `docs/AI-HANDBOOK.md` (section 11).
- **Applies to:** every assistant.
- **Agent:** [[Agents/handbook-hermes-rules|handbook-hermes-rules]]

### 2026-11-07: Hermes keeps fixing its own PRs
- **Decided:** Hermes keeps doing small fixes and iterations on its own PRs (#1104, #1106) "till he gets it"; Claude
  reviews them and adds what it finds to `docs/AI-HANDBOOK.md` section 11.
- **Applies to:** Hermes's PRs.
- **Agent:** [[Agents/handbook-hermes-rules|handbook-hermes-rules]]
