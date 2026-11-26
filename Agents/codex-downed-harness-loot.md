---
status: stalled
task:
branch: codex/downed-harness-loot
with: claude (T3 sub-agent of thread b48e4ca8, Sonnet 5.5)
pr:
mods: kitchen, kingdoms
versions:
started: 2026-11-25
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java (additive: the harness read/write beside putGear, openLoot, hasGear)
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseLoot.java, CorpseLootMenu.java (a mob body's harness section)
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/CorpseLootScreen.java (the section drawn)
  - kitchen lang for the section's title
  - kingdoms/src/main/java/com/jaymoskingdoms/HeraldryWear.java, GuardKit.java, McaBridge.java (additive only: everyday harness for humanoid NPCs; these files are also in the in-review mordhau-3-body / mordhau-4-legs notes' owns lists)
  - changes/kitchen/codex-downed-harness-loot.md, changes/kingdoms/codex-downed-harness-loot.md
---
# Harness section on the loot screen of a fallen NPC, and harness as everyday dress

**Andy's ruling (2026-11-25, Mordhau harness thread, verbatim):** "the downed loot screen should have a harness section, most people should opt for harness gear."

## Reading taken (check this first)
- The only loot screen in the three mods is Kitchen's `CorpseLootMenu` (opens on a tap of a body, `CorpseEntity#openLoot`). A downed NPC that is still alive
  has no screen: it only has Powers' body menu rows ("Take their weapons", "Pick their pockets"), and `JaymosKingdoms.java` says the downed are
  "disarmed, never looted". Every downed NPC that is finished (third blow, execution, bleeding out) becomes a corpse and ends up on the screen.
- So the harness section goes on Kitchen's corpse loot screen. **No screen for the living downed was added.** Open question for Andy: does he also want
  one (taking harness off someone who is still alive but down)?
- "Most people": read as the humanoid NPCs that already wear harness pieces (the realm's folk, knights, guards, villagers who get a kit), and as
  ordinary clothing in the under-layer places (padding, hose, hood, cloak) by role and means, not plate for everyone, and never the player's gear.

## Plan
- Base: draft #2187 (claude/mordhau-4-legs, which carries #2185). Neither is merged, so this PR stacks on them (the HOSE slot is the 13th place).
  Retarget to master once they merge.
- Kitchen reads the dead mob's harness out of its look (`jaymoskingdoms:harness`, found by name wherever Fabric nests the attachment) and
  edits the look in place, as `putGear` does for `equipment`. One source of truth: a rise as a zombie, a reload or a second viewer all see the same.

## Notes
- Vault sync lock was busy (another agent's sync); notes edited by hand.
- No local Gradle on this PC (Andy plays here): CI compiles.
- 2026-11-26 01:20 No sign of work for 30 min: marked stalled (whoever ran it stopped).
