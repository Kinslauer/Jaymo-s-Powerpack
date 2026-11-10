---
status: done
for: hermes
from: hermes (Andy's pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-b
pr: 1210
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/]
---

# Permanent hearts found in ore

Powers: extra hearts come out of the world — mined, not levelled.

## What's wanted

From the pick-list, row **Crystal hearts found in ore** (Scaling Health's crystal hearts, MIT). Andy's note: "finding permanent hearts is a good addition."

## Notes

- **Design first, before code.** This changes what health is: right now health is a level, and Powers' wounds/downed state assumes that. A found heart has to survive death, or be lost on death (Andy decides), and it must not break the downed state or the wound system.
- Suggested shape: a rare crystal in deep ore, each one a permanent half-heart, capped, with the cap shown somewhere quiet.
- Bring Andy the design in two lines before building; he answers, then it is a normal PR.
- 2026-11-08 20:31 Design (two lines, before code): a rare 'heart crystal' ore generates deep in deepslate; mining it drops a crystal, and using one raises max health by one permanent half-heart, up to a cap, shown quietly (an extra line under the health bar or an inventory tooltip). Questions for Andy, he decides then I build: 1) Does a found heart survive death, or is it lost on death? 2) Cap: how many half-hearts total, and where should it show?
- 2026-11-08 20:58 2026-11-08 Andy approved adding the ore hearts ("i approved adding the ore hearts, send it to the modeller"). Art handed to the modeller as task ore-hearts-art: heart_crystal_ore + heart_crystal_ore_deepslate textures, heart_crystal item, models and blockstates, ids jaymospowers:heart_crystal_ore / _deepslate / heart_crystal. Still blocked on Andy's two design answers: does a found heart survive death, and what is the cap / where does it show; both asked in the Needs Andy thread 1569168864384913589.
- 2026-11-08 21:02 2026-11-08 Andy's answers: 1) a found heart is LOST when you die, but it grants you a life - another revive before reincarnation; 2) cap five full hearts above base, shown quietly as a faint extra run on the health bar. Read as: each heart held is an extra sure wake - a death burns one heart and is turned away (left for dead, as Mortality's SURE_WAKES wakes do) instead of rolling; with no hearts left the cause roll runs as today, and reincarnation clears the hearts and the extra max health. Art is with the modeller (ore-hearts-art); ids jaymospowers:heart_crystal_ore, _deepslate, heart_crystal. Unblocked, back in the pool.
- 2026-11-09 07:53 Released by ops triage (t_a6774332): Apollo's run (started 05:55) hung on a model call just after 06:05 and was hard-interrupted at 07:20 (session turn lease lost; cron job inbox-builder-a failed). No PR was opened. Partial work is committed and pushed as `fa72e7dea` on `claude/ore-hearts` (worktree E:/wt/ore-hearts): new core/wounds/HeartCrystals.java plus hooks in JaymosPowers.registerAttachments(), Downed's AFTER_RESPAWN handler and Mortality.roll(); the last two still carry broken indentation from a half-applied patch, so tidy those before building. Continue in that worktree instead of cutting a new branch - the ore and item art is already on master (PR #1167): heart_crystal_ore, heart_crystal_ore_deepslate, heart_crystal.

## Stall report

**2026-11-09 07:55, by ops triage (t_9ffb4e46). Claim already released at 07:53 (t_a6774332).**

**Blocker (specific):** not the code — the run died. `inbox-builder-a` (hermes-a / Apollo) started ore-hearts at 05:55:51 and had the item, the attachment and the mortality hook written by 06:01. The model provider stopped returning chunks at 06:05:57; Hermes killed the stale stream at 07:18:35 after 72 minutes and the job ended `RuntimeError: Operation interrupted: waiting for model response (4424.7s elapsed)`, then paused for unverified admission. Nothing was committed and no PR opened.
Behind that: Apollo spent 05:56-06:05 retrying `execute_code`, which is hard-blocked for cron runs ("no user present to approve it"), instead of writing the JSON assets with `write_file`. The next run must create the JSON by hand, not by generated script. The claim also did not auto-release because `vault.py`'s 45-minute rule reads the failed run's own 07:20 teardown as bot activity.

**What already exists** (branch `claude/ore-hearts`, worktree `E:/wt/ore-hearts`, now committed and pushed as `fa72e7dea` so it cannot be lost — the triage note above saying "uncommitted" is out of date):
- `core/wounds/HeartCrystals.java` (new): `HEARTS` attachment (persistent, copyOnDeath, synced to owner, `MAX_HEARTS = 10`), the `heart_crystal` item (CONSUMABLE 1.6s, lore line from lang, own creative tab), `grantHeart` / `applyExtraHealth` / `clearExtraHealth`, `tryBurnHeartOnDeath`, `onFinalDeath`, `onRespawn`.
- Hooks: `JaymosPowers.init` calls `HeartCrystals.register()` and lists `core.wounds.HeartCrystals` in `registerAttachments()`; `Downed`'s `AFTER_RESPAWN` calls `onRespawn`; `Mortality.decide` burns a heart before the final roll and `endLife` clears them.
- Build state: `sh ./gradlew compileJava` fails with **exactly one error** — `HeartCrystals.java:63: error: cannot find symbol` (uses `Identifier` with no `import net.minecraft.resources.Identifier;`). Everything else in the tree compiles.
- Art from PR #1167 is on master: `textures/block/heart_crystal_ore*.png`, `textures/item/heart_crystal.png`, block models, blockstates, and the `.bbmodel` sources under `art/heart-crystal/`.

**Remaining work (in order):**
- [ ] Add `import net.minecraft.resources.Identifier;` to `HeartCrystals.java`; compile clean.
- [ ] Undo Apollo's indentation damage, so the diff shows only the real change: extra tab on the closing brace of `JaymosPowers.init`, and `Downed`'s `AFTER_RESPAWN.register(...)` block re-indented (two lines left at column 0).
- [ ] Drop the redundant `import com.jaymospowers.core.wounds.HeartCrystals;` in `Mortality` (same package).
- [ ] Register the two ore blocks (`heart_crystal_ore`, `heart_crystal_ore_deepslate`) — a plain `Block` is enough; the models and textures are already on master.
- [ ] **Item definitions are missing and the item/ores will show as a missing texture without them** (the art review flagged this at 00:17): `assets/jaymospowers/items/heart_crystal.json`, `heart_crystal_ore.json`, `heart_crystal_ore_deepslate.json`, in the shape of `items/heartstone_amulet.json` (`{"model":{"type":"minecraft:model","model":"jaymospowers:item/<id>"}}`).
- [ ] Worldgen: a `worldgen/feature/ore_*.json` + `worldgen/placed_feature/ore_*.json` pair for deep deepslate, rare, copying an existing ore pair in this repo and injected into the same biome set.
- [ ] Loot tables: `data/jaymospowers/loot_table/blocks/heart_crystal_ore.json` and the `_deepslate` variant, both dropping `heart_crystal`.
- [ ] Tags: put both blocks in the pack's pickaxe-mineable / needs-iron-tool tags, matching the other ores.
- [ ] Lang: a `lang/parts/hearts.json` part with the block names, `item.jaymospowers.heart_crystal`, `itemGroup.jaymospowers.hearts`, `tooltip.jaymospowers.heart_crystal`, and `message.jaymospowers.hearts.granted` / `.full` / `.burned` — the code already asks for those keys, and hard-coded English gets sent back in review.
- [ ] The health bar's faint extra run is **not started**: no health-bar HUD code exists yet to extend, so it needs a new client element (`HudElementRegistry.attachElementAfter`, as `CorruptionClient` does) or a mixin. It has to read as a quiet extra run, not vanilla-style hearts.
- [ ] Fix the double count Apollo left in `tryBurnHeartOnDeath`: it increments `Mortality.WOKEN` itself and `leftForDead` increments it again, so burning a heart also eats one of the three sure wakes — Andy's design says a heart *is* the extra wake. Keep one increment.
- [ ] Check it against the existing `crystal_heart` artifact (`ArtifactBaubles`, belt/charm, +10 max health, "Five more hearts"). Do not change the artifact; say in the PR body whether they stack, and if that reads as too much health, ask Andy in the Needs Andy thread rather than deciding.
- [ ] `changes/powers/claude/ore-hearts.md` change note (`# <title>` first line, no version), then `python tools/pr_checks.py`, then the PR with a "not tested in game" line.

**Assets needed:** none — hermes-modeller delivered the ore and item art in PR #1167. The only missing art-side files are the three item-definition JSONs, which are code-side and listed above.

## Text review

All player-facing strings the feature introduces (from `HeartCrystals.java` and the stall report checklist), with exact keys and file paths for `src/main/resources/assets/jaymospowers/lang/parts/hearts.json`.

### Block names
- `block.jaymospowers.heart_crystal_ore` → **Heart Crystal Ore**
- `block.jaymospowers.heart_crystal_ore_deepslate` → **Deepslate Heart Crystal Ore**

Matches existing ore naming (e.g., `block.jaymospowers.vault_keystone` → "Vault Keystone"). Title Case, noun phrase.

### Item name
- `item.jaymospowers.heart_crystal` → **Heart Crystal**

Matches the artifact `crystal_heart` ("Crystal Heart") but distinct — this is the raw consumable crystal, not the bauble. Title Case.

### Creative tab
- `itemGroup.jaymospowers.hearts` → **Heart Crystals**

Follows `itemGroup.jaymospowers.armory` ("Jaymo's Armory"), `itemGroup.jaymospowers.alchemy` ("Alchemy") — short, descriptive, Title Case. "Heart Crystals" plural because the tab holds the crystal item (and future heart-related items).

### Item tooltip (lore line, rendered gray italic via `DataComponents.LORE`)
- `tooltip.jaymospowers.heart_crystal` → **Consume for a permanent half-heart. Lost on death, but each grants an extra life.**

Style: one sentence, imperative mood, explains effect and death behavior. Consistent with artifact tooltips like "Five more hearts" (crystal_heart) and "Turns a death aside once by throwing you somewhere near" (chorus_totem) — concise, no mechanic jargon.

### System messages (sent via `player.sendSystemMessage`, colored)
- `message.jaymospowers.hearts.granted` (arg: new half-heart count) → **A heart crystal takes root in you. (%s half-hearts)**

Tone: atmospheric, second-person present, like mortality messages ("You wake cold. Nothing hurts."). Light purple color in code fits the "takes root" phrasing. Arg is half-heart count (code passes `current + 2`; see code note below).

- `message.jaymospowers.hearts.full` → **You cannot hold more heart crystals.**

Direct, second-person. Matches `message.jaymospowers.baubles.full` ("No free bauble slot takes that") — blunt limitation feedback.

- `message.jaymospowers.hearts.burned` (arg: remaining half-hearts) → **A heart crystal shatters, turning death aside. (%s half-hearts remain)**

Atmospheric, matches mortality wake messages ("Everything goes dark. Something in your blood keeps moving after your heart stops."). Red color in code fits "shatters". Arg is remaining half-hearts (code passes `current - 1`).

---

### Code notes for the builder (not text changes)
- `grantHeart` passes `current + 2` to `granted` but the new count is `current + 1`. The arg should be `current + 1` (the actual new half-heart total).
- `tryBurnHeartOnDeath` increments `Mortality.WOKEN` itself *and* `leftForDead` increments it again — double-counts the sure wake. Keep only the `leftForDead` increment (the heart *is* the wake).
- Cap is 10 half-hearts (5 full hearts). Messages show half-heart counts; consider whether to show full hearts instead (divide by 2) for player clarity.
- Existing `crystal_heart` artifact ("Crystal Heart", tooltip "Five more hearts") stacks with this — both grant +10 max health. PR body should note this and flag if it reads as too much health.
- 2026-11-09 21:58 PR #1210 is merged into master; moved to Inbox/Done.
