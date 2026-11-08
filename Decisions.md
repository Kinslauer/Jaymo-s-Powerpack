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

### 2026-11-07: one live spot for agents and decisions
- **Decided:** agent notes and this decision log live in `E:\Origins Mod\docs\vault\Live\` (ignored by git), written
  there directly by every agent, so the board shows agents the moment they start and everything is in one place.
- **Applies to:** every agent session, every assistant.
- **Agent:** [[vault-live-board]]

### 2026-11-07: who bleeds what, and ground blood lies still
- **Decided:** Andy: "the emberborn should have the same type of cooling magma blood that the cubes have, and so should the
  magmavein stoneborn. all zombies should get the rotten blood, and the rest of the blood changes are good to go too. just
  no redstone textures ... the blood that falls on the ground shouldnt randomly sputter or splat or move unless directly
  interacted with." Endborn ender-purple, Divine gold, Verdant sap approved; corpses bleed their own blood.
- **Applies to:** Powers blood (`BladeBlood`, client blood), Kingdoms `PowersCompat.heritageBlood`.
- **Agent:** [[blood-heritage]] (PR #1109)

### 2026-11-07: magma bubbles, blood falls; Kitchen bleeds the body's blood
- **Decided:** Andy: "the magma should bubble as it cools throwing off those popping lava particles, the BLOOD should not.
  it can still move and fall and drip, like water does through blocks, but over ledges. this way a bleeding body above a
  ledge can drip a pool up to 20 blocks below it". Skeletons don't bleed; stump bleeds are red spurts, bigger once fallen.
  Kitchen's blood follows the body (checked at Andy's ask). Merged as #1109.
- **Applies to:** Powers blood, Kitchen `KitchenBlood`.
- **Agent:** [[blood-heritage]]
- 2026-11-07: ParCool can't ship (Forge/NeoForge only, LGPL); Andy picked 12 of its moves to remake ourselves (not zipline, grapple, energy drink), split into Inbox tasks parcool-ledge-and-wall-moves and parcool-running-and-falling.
- 2026-11-07: Andy: "the hermes reviews should be handled here": reviews of Hermes's Inbox PRs run in the Claude session "Train Hermes to work like the Claude threads" (vault-review skill), not in the coordinator thread.
- 2026-11-07: Andy: "review what the fixer sends you then send it to the coordinator, dont ask me for that, the coordinator will bring it to my attention and i can kick it back down the ladder if need be." The PC Claude reviewer runs vault_sync --send on a passing Hermes PR without asking Andy first. Also: three Hermes bots (hermes-a, hermes-b, hermes-fixer) set up as Hermes profiles with scheduled Inbox jobs.

### 2026-11-07: Critter Armory vs Fresh Animations
- **Decided:** "Build a compat fix" (decision card): fit Critter Armory armour to Fresh Animations models for pigs, sheep, horses.
- **Applies to:** Powers compat, task critter-armory-fit.
- **Agent:** coordinator thread.
- 2026-11-07: Andy, on the fishing send-back (metal rod, sharks, fish processing): "i phrased those as three separate things intentionally, its fine if they would have taken one each, next time break it up that way." When Andy lists separate features, the coordinator posts one Inbox task per feature (each its own branch and PR), not one bundled task.
