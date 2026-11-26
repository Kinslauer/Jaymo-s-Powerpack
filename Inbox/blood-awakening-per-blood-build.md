---
status: open
for: gemini
from: Andy (Waiting on Andy page, 2026-11-26)
asked: 2026-11-26
claimed_by: 
pr: 
---

# Blood Awakening: build Andy's per-blood upgrades

**Andy's picks (2026-11-26, Waiting on Andy page, db picks/andy):** 90 of 94 rows ticked on `Picklists/Blood awakening per-blood picks.md` (ticks written into that file). Every ticked upgrade **adds on top of** the shared rule from PR #2117 (halved waits, 1.5x heritage-key effects); nothing replaces it.

Two rows are Claude's additions for the Scalebound look (missing from Quill's list): `ba-scalebound-mirror` (same upgrades as the Serpen's Shed Skin) and `ba-scalebound-aim` (after shedding, the Archer gift is stronger for a short while: truer, faster draws). Both ticked.

## Andy's notes on rows (build these as he says)
- `ba-tideborn-source` (3. Tideborn — Tidal Breath (40s): Breath creates a water source) ticked: "each look needs their own"
- `ba-emberborn-rain` (6. Emberborn — Forge Blessing (60s): Blessing works in rain) NOT ticked: "retry on this one."
- `ba-biobuilt-dmg` (17. Biobuilt — Engulf (held, 0s cooldown): Engulf damages more per tick) NOT ticked: "it should turn into a grab attack"
- `ba-rubberfolk-cdr` (18. Rubberfolk — Engulf (held, 0s cooldown): Release cooldown shorter) ticked: "should also turn into a glob you shooot out and attaches to a player, sapping them while attached. must be removed by someone else by attacking it"
- `ba-beastfolk-speed` (19. Beastfolk — Feral Sprint (30s): Sprint faster) ticked: "each look needs their own"
- `ba-hollow-heal` (20. Hollow — Grave Chill (35s): Less Healing reduced) NOT ticked: "you should be able to blend in with humans, they cant tell you're undead at all."
- `ba-obsidian-aura` (23. Obsidian — Rune of Warding (60s): Ward covers an area) ticked: "should work like the wizard ward reversing damage"
- `ba-kitsune-flames` (42. Kitsune — Foxfire (40s): Foxfire makes more flames) ticked: "flames should be blue, and kitsune are VERY powerful sorcerers"

Reading of the notes: `ba-biobuilt-dmg` is replaced by Engulf turning into a grab attack; `ba-hollow-heal` is replaced by the Hollow passing as human (people can't tell they're undead at all); `ba-emberborn-rain` needs a new idea for Andy (don't build it, ask him with a fresh row); "each look needs their own" on `ba-beastfolk-speed` and `ba-tideborn-source` = every look of that heritage gets its own version of the upgrade, not one shared one. `ba-emberborn-dur` was left unticked (it only repeated the shared 1.5x).

## Rules
- Lore first: place each upgrade in Lore.md as a `(new)` paragraph where it adds something new (Kitsune as very powerful sorcerers, blue foxfire).
- Where: Kingdoms `HeritageKits` (Move table, `cast`), `TreeLocks.awakened`; Powers `HeritageMoves`, `EmberKindle`, `RaceMoves`, `FrostBeam`; Kingdoms `FrostPrison`, `BioBuilt`, `RubberFolk`, `HeritageHold`.
- Only while Blood Awakening is learned. Split into patches by heritage group (2 tracks at a time). Change note per mod. No silent cuts: name any row you can't build and why.

## Notes
- 2026-11-26 13:22 Emberborn retry (Andy 2026-11-26 8:21 AM ET: 'Surprise me', so Claude picked; replaces the dropped rain row): (1) ba-emberborn-steam, Steam stride: while Forge Blessing is up, water boils off the Emberborn in steam and Heavy Water stops slowing them in water until the blessing ends; (2) ba-emberborn-edge, A forge-hot edge: the held weapon glows hot for the blessing and each hit sets the foe alight briefly, with sparks. Not picked: mending worn armour (clashes with Andy's no-mending-in-combat armour rule, #979) and blessing nearby allies.
- 2026-11-26 14:47 Claude delegated this to Codex.
- 2026-11-26 14:52 Codex hit its usage limit (back 6:51 PM); moved to Gemini (antigravity).
- 2026-11-26 15:28 gemini's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 15:36 Gemini opened patch 1 as PR #2251 (Plain, Mountain, Elemental/Sky groups; plan in docs/Blood-Awakening-Per-Blood-Plan.md) before its quota ran out. Later patches follow after review.
