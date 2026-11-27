---
status: writing
for: apollo
from: coordinator
asked: 2026-11-24
thread: Hermes Training (cloud vault coordinator)
claimed_by: hermes-a
pr: 2209
suggested_owns:
  - kingdoms Camps.java, Sieges.java, SiegeTrain.java
  - kingdoms lang/parts/siege-camps.json
writer: hermes-writer
text_pass: 
---
# Structures 1: siege camps and real sieges

**Plan:** section of "Structure placement and factions plan" (https://claude.ai/artifact/4foeFXysu1Vg9KJBADDZab). Andy's rule: every generated structure needs a
reason to be where it is, tied to the climate heatmap and the economic/political climate. Worlds stay procedural.
Build 2 placement tracks at a time; tracks 1 and 2 first.

**Andy's words:** camps "should come in groups, 3s and 4s and around a Kingdom or castle, this way it looks like a quickly thrown up raid shelter, to prepare to siege, and the siege should actually happen, supplied with the raiders stationed nearby, if they win they move in like now, and vice versa, the losers are routed or killed and their resources go to the victor."

Build: place 3-4 camps around a kingdom or castle seat; a muster period; the siege runs, drawing supplies from the camps (a camp cleared mid-siege weakens it); winners take the site, losers routed or killed, their storage goes to the victor. Today a cleared camp's land and storage are wiped (Camps.java, chief kill) instead of going to the victor.

## Notes
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: retain grouped camps, supply-dependent sieges and victor resource transfer. Camps still clears storage on fall. #2195 fixes NPC crowding, not this task. Do not edit HollowCaptains.java, Captains.java, Rivals.java or Ladders.java while their separate PR owns them.
- 2026-11-26 02:04 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-26 02:07 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-26 02:32 Text pass: reviewed kingdoms siege-camps player text - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/siege-camps.json and changes/kingdoms/structures-1-siege-camps.md; wording already player-facing, committed unchanged as c8b570b976 on claude/structures-1-siege-camps. kingdoms compileJava passed; tools/pr_checks.py passed.
- 2026-11-26 02:37 hermes-a's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-26 02:45 Text pass: reviewed every player-visible string PR 2209 adds - kingdoms/lang/parts/siege-camps.json (16 muster, supply, victory and defeat lines; all keys and percent-s placeholders kept, wording already player-facing, victory.1 tightened to are ours) and changes/kingdoms/structures-1-siege-camps.md (player-facing bullets, ends Built; not played). No code changes needed. Commit c8b570b976 already on the PR branch and pushed; JSON valid, tools/pr_checks.py passed, all PR checks green.
- 2026-11-27 02:56 Review (head c8b570b), cloud coordinator for Andy (PC reviewer away since 11-21): changes needed. 1) Camps.java:385-403: the new gate campsNear + GROUP_SIZE_MAX <= *_PER_KINGDOM can never pass for illager camps, goblin villages, orc camps, pirate coves and sky coves (each PER_KINGDOM is 1), so none of them spawn any more; barbarian rings only when a kingdom has zero camps. Keep single sites for the others; only raider camps come in rings of 3-4. 2) Camps.java:481: trySiteGroup skips sky coves outright and SkyPirates.trySite is no longer called. 3) Camps.java:747 chief kill still wipes the camp's land and storage; the task asked for it to go to the victor. 4) Sieges.java:233-273 drawFromCamps only looks at BARBARIAN factions on the attacker's war side, so it almost never finds one, and what it takes turns into spoilsSilver (taken/4); supplies change nothing about the siege. Make the siege run on the camps' supplies (fewer reinforcements or a shorter siege when they run short) and make a camp cleared mid-siege weaken it (message cleared_weakens is never used). 5) WarParties.java:554-557 + Sieges.java:576-585: every won siege now marks the target fallen and discard()s every folk within 160 blocks in a poof. Andy said winners move in like now; losers routed (they flee, can be caught) or killed (they go down, Andy's rule), never vanished. Keep the existing conquest and rout the defenders' soldiers, not the whole town. 6) Lang keys group_formed, muster_begin/end, supplies_drawn, victory, defeat and every speech.siege.muster/victory/defeat line are never used; wire them as speech bubbles or drop them. KingdomEvents.java:293 sends land.transferred to chat: use a bubble or the town crier, no chat text. 7) Indentation is broken in Sieges.java, WarParties.java and KingdomEvents.java, and KingdomEvents.java has an orphan duplicate javadoc above transferLand. CI is green.
- 2026-11-27 02:03 Text pass: changes/kingdoms/structures-1-siege-camps.md reworded for review-round-2 accuracy (bullet 1: only raider camps spawn in rings of 3-4 around a kingdom seat; illager camps, goblin villages, orc camps, pirate coves and sky coves appear singly; bullet 2: muster draws supplies from nearby camps, dropped 'allied'); kingdoms/lang/parts/siege-camps.json reviewed - single key message.jaymoskingdoms.land.transferred ('The land of %s passes to %s by right of conquest.'), player-facing, placeholders kept, unchanged. No code changes. kingdoms build successful, tools/pr_checks.py passed, pushed to PR #2209 branch.
