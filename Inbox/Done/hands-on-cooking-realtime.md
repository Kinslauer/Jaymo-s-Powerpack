---
status: done
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-16)
asked: 2026-11-16
thread: 1572081014958858340
claimed_by: hermes-fixer
pr: 1957
suggested_owns: []
writer: hermes-writer
text_pass: 
---

# Hands-on cooking / realtime minigames (inspired by Hands-On Delight)

Andy wants to research https://www.curseforge.com/minecraft/mc-mods/hands-on-delight and make cooking/minigames in the pack hands-on and realtime — like stirring soup in an open cooking pot instead of menu screens.

## What Hands-On Delight does

Cooking pot opens in the world, you stir the soup yourself with a spoon, chop with a knife on a cutting board, skillet fries food, sandwiches built on board, keg brewing, kettle tea, sushi rolling. No menus — blocks open in the world. Every recipe of Farmer's Delight and its addons. No new blocks or items.

## What's wanted

Research Hands-On Delight's approach and apply it to Powers/Kingdoms/Kitchen cooking — make cooking hands-on and realtime, like minigames. Andy wants "our minigames into realtime like this. All cooking should be hands on."

## Notes

Research/investigation first — determine what stations exist in our mods and how to make them hands-on instead of menu-based. No code changes yet.
- 2026-11-17 06:02 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-17 08:15 hermes-b's claim went stale (nothing changed for 54 min); handed back as open so a bot can take it.
- 2026-11-17 08:44 Question: research is complete and verified against origin/master code (agent note + hands-on-cooking-realtime-SPEC.md by hermes-a, ready for implementation). Kitchen cooking stations/minigames all exist as documented; no code changes yet per task scope. Shall an implementation bot pick up Phase 1+2 (cutting-board CHOP + pot STIR as in-world, hands-on interactions) as the first PR?
- 2026-11-19 20:17 Andy (2026-11-19): "yes" - build phase 1+2 now: chopping on the cutting board (CHOP) and stirring the pot (STIR) in the world, as in hands-on-cooking-realtime-SPEC.md. One PR.
- 2026-11-19 20:20 The spec is at Agents/Done/hands-on-cooking-realtime-SPEC.md.
- 2026-11-19 23:37 Vault coordinator 2026-11-19 23:40: set blocked again from the PC with no new question. Andy already answered the 08:44 question on 2026-11-19 ("yes": build phase 1+2), so this is open to build. If a builder has a NEW question, write it as a 'Question:' note before blocking.
- 2026-11-20 21:28 hermes-writer's claim had no sign of work for 30 min: handed back as in-review.
- 2026-11-20 21:34 Text pass: added 4 missing lang keys to kitchen/src/main/resources/assets/jaymoskitchen/lang/en_us.json — item.jaymoskitchen.spoon ('Spoon'), item.jaymoskitchen.real_time_cooker ('Realtime Cooker'), block.jaymoskitchen.real_time_cooker ('Realtime Cooker'), message.jaymoskitchen.already_cooking ('You're already cooking somewhere.'). No other player-visible strings in the PR (no chat/GUI text in the new client classes; change note already reads well). Build is red: 90 pre-existing compile errors in the PR's new Kitchen code (wrong readUuid/writeUuid names, public records in wrong files, missing permits/imports, 26.3 API mismatches) — not a text-pass fix, left for the code bot.
- 2026-11-21 04:47 Review (head c0b76b7): not reviewed. PR #1957 conflicts with master (mergeable CONFLICTING) and no PR checks ran on this head. Merge master in, resolve the conflicts, push and get the checks green; it gets a full review then.
- 2026-11-21 05:21 hermes-fixer took over the review fixes from hermes-a.
- 2026-11-21 06:07 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-21 08:17 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-21 11:14 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-21 11:32 Text pass: reviewed PR #1957 player-visible strings. kitchen/src/main/resources/assets/jaymoskitchen/lang/en_us.json — item.jaymoskitchen.spoon 'Spoon', item.jaymoskitchen.real_time_cooker 'Realtime Cooker', block.jaymoskitchen.real_time_cooker 'Realtime Cooker', message.jaymoskitchen.already_cooking 'You're already cooking somewhere.' (all plain, brief, player-voiced, consistent with existing kitchen strings; no placeholders to keep beyond keys). changes/kitchen/claude-hands-on-cooking-realtime.md — player-facing 'What a player will see' bullets plus a 'Not tested in game' list; reads well. PR's new Java (HandsOnClient, HandsOnInputHandler, RealTimeCookingBlockEntity, HandsOnSession/Station/Ticker, payloads) contains no player strings; all translatable keys it references already exist in en_us.json. No rewrites needed, no files changed.
- 2026-11-21 11:33 Blocked: text pass complete, no changes needed (reviewed PR #1957's player-visible strings: en_us.json keys spoon / real_time_cooker item+block / already_cooking, and changes/kitchen/claude-hands-on-cooking-realtime.md — all plain, player-voiced, keys and placeholders kept; PR's new Java has no player strings and references only existing lang keys). Cannot hand in: vault --set status=in-review text_pass=done refused (exit 4) because PR #1957's GitHub 'Build Kitchen' check is failing from pre-existing Kitchen compile errors, not from text. Re-hand in once hermes-fixer's build fixes land and checks go green.
- 2026-11-21 23:34 Status blocked -> changes (pending-work pass, 2026-11-21): nothing here waits on Andy. PR #1957's Build Kitchen check fails, so it needs a bot's fixes before review.
- 2026-11-23 05:24 Done (2026-11-23): PR #1957 closed unmerged on 2026-11-22 because master already does hands-on cooking in the world (kitchen cooking/hands: HandsOn, ChopRun, StirRun, CookTools, the hands_on_cooking game rule). Superseded; nothing to fix.
