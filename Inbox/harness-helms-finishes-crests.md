---
status: in-review
for: hermes-a
from: pc-claude
asked: 2026-11-18
claimed_by: hermes-a
pr: 1721
writer: hermes-writer
text_pass: done
---

# Harness 1: new helms, finishes and crests

**Andy's words (2026-11-18):** "done ticking put the list on the taskboard for a bot, maybe apollo".

**Rows:**
- **hm-great** Great helm (S): flat-topped pot helm with eye slits and breaths. Fixed face. Chivalry realms of old oaths; rusted ones on barrow knights.
- **hm-barbute** Barbute (S): deep helm, T/Y face opening. Open face. Coin realms' hired men-at-arms.
- **hm-burgonet** Burgonet (S): open helm with peak, comb, hinged cheek plates. Realm captains and mounted guards.
- **hm-spangen** Spangenhelm (S): riveted segments, nose bar, mail curtain. Taiga realms of the Old Blood.
- **hm-lobster** Lobster-tail pot (S): round skull, sliding nose bar (lifts like a visor), laminated neck tail. Realms with guns on their walls.
- **hm-grotesque** Grotesque visor (M): an armet whose visor is a sculpted beast or grinning face. Conquest champions and lords.
- **fn-russet** Russeted (S), **fn-blued** Heat-blued (S), **fn-painted** Painted in the realm's colours (S): new FINISH values at the smith's anvil.
- **fn-etched** Etched and gilt bands (M): for knights a realm has honoured (King's Guard); unlike gilding it doesn't come off non-lords.
- **fn-rusted** Rusted (S): dead men's harness in barrows and ruins; weaker until scoured at a grindstone.
- **cs-beasts** More crest beasts (S): griffin, bear, wolf, raven, hound beside the eight.
- **cs-plume** Plumes (S): feathers in the realm's colour, in the crest place instead of a beast.

**Where to start:** Kingdoms `HeraldryArmour` (pieces, finishes, helms, crests), `HeraldryWear` (who wears and sells what), `Harness` (the ten places), `HarnessMenu`/`HarnessScreen`, client `HeraldryLayer`, `kingdoms/tools/gen_heraldry_armour.py`.

**Source:** Andy ticked every row of the Harness Pattern Book (https://claude.ai/artifact/GSAKVequm61NJT5Z3mLNoW, db picks/andy, saved 2026-11-18 07:30 UTC), no notes. Lore: "The armourers' wider makes" in Lore.md, after "The knight's harness" (marked (new), waiting on Andy).

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, art drawn by `kingdoms/tools/gen_heraldry_armour.py` in the harness's own style, tuning constants at the top of each class, check 26.3 APIs against genSources, builds on the Deck or CI only (never Andy's PC). The five harness tasks all touch `HeraldryArmour` and the generator: take them in order (helms, body, creeds/lands, barding, upkeep) and start each from master after the one before it merges.

## Notes
- 2026-11-18 02:44 Andy approved the lore placement "The armourers' wider makes" (2026-11-18): it's canon now.
- 2026-11-18 10:54 Text pass on PR #1721 (Harness 1: helms, finishes, crests). Reviewed every player-visible string the PR adds or changes: kingdoms lang heraldry-armour-items.json (6 new helm names, 5 new crest names, Crest of Plumes), heraldry-armour.json (5 finish names: Russeted, Heat-blued, Painted, Etched and Gilt, Rusted), message.json (bounty.paid, job.paid, job.paid_short shortened), class.rider.desc, heritage dragonblood gift_rimewyrm, story shorts (born.den, childhood.blades, raised.pack, turn.fate, youth.drifted) and gui.creator.faith. All text is plain, brief, in the speaker's voice, matches the Harness Pattern Book names Andy approved; all lang keys and placeholders kept. No rewrites needed, so no files were changed and nothing was built or pushed. Change note changes/kingdoms/harness-helms-finishes-crests.md reads well.
