---
status: done
task:
branch: codex/downed-harness-loot
with: claude (T3 sub-agents of thread b48e4ca8, Sonnet 5.5)
pr: 2188
mods: kitchen, kingdoms
versions:
started: 2026-11-25
owns:
  - kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseHarness.java (new), CorpseEntity.java (additive: the harness read/write beside putGear, openLoot, hasGear), CorpseLoot.java, CorpseLootMenu.java
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/CorpseLootScreen.java, kitchen lang part corpse_harness.json
  - kingdoms/src/main/java/com/jaymoskingdoms/Everyday.java (new), HeraldryWear.java (two additive call sites)
  - kingdoms/src/main/java/com/jaymoskingdoms/DownedHarnessMenu.java (new), client/DownedHarnessScreen.java (new), Harness.java (menu type, taken tag, dress gate), Downed.java (the "harness" body-menu row), GuardKit.java (two skip lines), HeraldryWear.java (one gate), HeraldryClient.java (screen registration), lang parts body_menu.json + harness-downed.json
  - kingdoms/src/main/java/com/jaymoskingdoms/Rulers.java (two Harness.issued lines: crown, retitle)
  - E:/Jaymo Vault Live/Lore.md (one (new) paragraph: What ordinary people wear)
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
- Base: master. #2185 and #2187 merged 2026-11-26 00:32-00:33 while this was being written; the branch was rebased onto master.
- Kitchen reads the dead mob's harness out of its look (`jaymoskingdoms:harness`, found by name wherever Fabric nests the attachment) and
  edits the look in place, as `putGear` does for `equipment`. One source of truth: a rise as a zombie, a reload or a second viewer all see the same.

## Notes
- Vault sync lock was busy (another agent's sync); notes edited by hand.
- No local Gradle on this PC (Andy plays here): CI compiles.

## Done
- 2026-11-25 Draft PR #2188 (stacked on #2187/#2185, base master). Kitchen: CorpseHarness + harness block on CorpseLootMenu/Screen; Kingdoms: Everyday (80% of working Folk and non-guard villagers in cloth harness, zero armour). Lore: one (new) paragraph, "What ordinary people wear". pr_checks passes; Java compiled only by CI; not played.
- Not done / open: no screen for the living downed (question for Andy); mob harness doesn't shed with lost limbs; raiders and bandits left on Andy's own wardrobe shares.

## 2026-11-26 retry (T3 sub-agent, Sonnet 5.5): the living downed's screen
- Andy's parent thread: the screen for the living downed MUST be built, not asked about. Building it in Kingdoms (it owns the harness): a "Take their harness" row on Powers' body menu (`PowersCompat.bodyOption`, like disarm), opening `DownedHarnessMenu` (ExtendedMenuType keyed by the body's entity id, like TackMenu) with the 13 real places, take only, written through `Harness.set`.
- Checked: no other running agent or open PR touches these files (only #2188). Fabric attachment key verified by `javap` on fabric-data-attachment-api-v1: entity data holds `fabric:attachments` (compound) -> `<id>` per attachment; Kitchen's CorpseHarness now reads exactly that path.
- Farm guard: GuardKit re-kits guards and re-issues harness every review, and Harness.dress re-dresses mobs, so a stripped body is tagged `jaymoskingdoms.harness_taken` and nothing re-dresses or re-kits it.

## Done (retry)
- 2026-11-26 PR #2188 head c2ed877: living-downed screen built (Kingdoms `DownedHarnessMenu` + `DownedHarnessScreen`, body-menu row "Take their harness", lang part downed_harness.json); `Harness.TAKEN_TAG` stops every re-dress (Harness.dress, GuardKit sweep/harness/forget, HeraldryWear restyle clear); Kitchen `CorpseHarness` now reads `fabric:attachments` -> `jaymoskingdoms:harness` (checked in fabric-data-attachment-api-v1 2.2.34); shift-click moves a copy so the take is always marked. Lore paragraph extended. PR body, change notes and Decisions updated.
- Not played; compiled only by CI. Open limits are in the PR body.

## 2026-11-26 lifecycle fix (T3 sub-agent, Sonnet 5.5), head b9bc730
- Regression from the retry: the blanket `harness_taken` tag stopped GuardKit restoring untouched heavy pieces after off duty, and left promotions/knighthoods/coronations without harness or crest. Replaced by a tag per place (`jaymoskingdoms.harness_taken.<place>`): `Harness.dress`, GuardKit's kit/look-over and restyle skip only those places; `Harness.issued` clears them on a deliberate new issue (a guard's first kit or a new settlement's, `HeraldryWear.knight` for a new recruit, `Rulers.crown`/retitle). A gunner/archer flip or demotion is NOT a new issue (repeatable). Rank rise doesn't exist for GuardKit guards (rank is from the UUID; recruits with RANK aren't kitted by it).
- Fabric pin checked: pack pins fabric-api 0.165.2 (installed in E:/CurseForge/Instances/Jaymo's Powers/mods too), bundling data-attachment 2.2.34; `fabric:attachments` + EntityMixin on saveWithoutId/load confirmed by javap; serializing classes identical in 0.161.0/0.162.0/0.165.2.
- Open limits: `McaBridge.convertGuard` makes a fresh villager who gets a first kit (pre-existing); free cloth floor returns with any new issue; not played; compiled only by CI.
- 2026-11-25 21:57 PR #2188 is merged into master; moved to Agents/Done.
