---
status: cancelled
for: apollo
claimed_by: hermes-a
from: Andy (Claude chat, 2026-11-26)
asked: 2026-11-26
pr: 2245
writer: hermes-writer
text_pass: 
---

# Hollow captains: keep the count, 12 per player, own ladders

## What's wanted

Follows PR #2144 (squash 668d46a). Agent note: [[Agents/hollow-captains|hollow-captains]]. A review by Codex found two
problems, both checked in the code. Andy said: "yes add that to the PR" and "there should be 12 captains per player,
they should all have their own ladders, and the captains can intersect." One PR for all of it, in Kingdoms.

**Owns:** `kingdoms/src/main/java/com/jaymoskingdoms/HollowCaptains.java`, `Captains.java`, `Rivals.java`,
`Ladders.java` (and the client `LadderScreen` if needed). No other task touches them.

1. **Bug, the count goes over its limit:** `HollowCaptains.died()` (around line 472) takes a captain who is due back
   off `WALKING` but leaves `COUNT` as it is. A roaming Hollow or a horde fills the open spot, then `comeBack()`
   (around line 371) adds his body without checking for room, so 6 becomes 7. A captain who is due back keeps his
   place.
2. **Tuning numbers written into the code:** move them into `static final` constants at the top of the class: the
   10-tick path refresh (around line 625), the 2.0F mend threshold (around lines 660/663), and the retreat 8/3 and speed
   1.1 (around lines 690-692).
3. **12 per player, each on their own ladder** (Andy clarified 2026-11-26): every player has 12 Hollow slots on their
   own ladder, replacing the world-wide `START = 6`. Put the 12 in a constant. A Hollow still becomes a named captain
   only after it kills a player (kill-only promotion stays). Each ladder fills, ranks and refills on its own.
   "Intersect": captains from different players' ladders can meet and fight together, and one captain can be on more
   than one player's ladder (one who killed two players, say). Decide what happens while a player is offline, and say
   what you chose in the PR. Read `Captains` (LADDER, the tribe ladders), `Rivals.ladder()` and `Ladders` first.

## Rules

- MCA only through `McaCompat`.
- Every Minecraft API checked against the 26.3 sources.
- Add a change note `changes/kingdoms/<branch>.md`. Don't touch `version=`, the MODLOGs or `docs/codemap/`.
- Update the lore paragraph "The Hollow among the dead" in Lore.md as `(new)` for Andy, and update the agent note.
- Build on CI or the Deck. Turn on Auto-fix, or watch CI yourself.
- Ask Andy if "intersect" turns out to mean something that changes the design.
- When it's done: what a player will see, what wasn't tested, and the PR link.

## Delegation note (YOUTH, 2026-11-26)
Delegated to Apollo (Andy: hand both jobs to the Hermes bots now). It was briefly claimed by hermes-fixer/Hephaestus, but Hephaestus is on the stop list since Nov 21 and this is not narrow-bug-only work (adds 12 captains per player, own ladders, intersecting captains), so it goes to Apollo.

## Notes
- 2026-11-26 06:07 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 11:51 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 14:12 Text pass: rewrote changes/kingdoms/hollow-captains-fix.md to be player-facing (per-player ladders of up to 12, kill-only promotion, intersecting ladders, captain-due-back keeps their ladder place, count bug fix; no code internals). The PR adds no lang, chat, screen or dialogue strings — HollowCaptains.java and Captains.java changes are code only (per-player attachments, named tuning constants). Kingdoms builds (sh ./gradlew build --no-daemon) and python tools/pr_checks.py pass; not play-tested.
- 2026-11-27 02:59 Review (head 7a79807), cloud coordinator for Andy: changes needed. 1) HollowCaptains.java:402: the original bug isn't fixed, it now ends the other way: a due-back captain isn't counted in playerRoom (line 452 counts only walking bodies), so roam fills his slot and comeBack then returns with no retry; he never comes back. Count due-back captains on the player's ladder as taken in playerRoom, so their place really is reserved. 2) Plain Hollow (not captains) risen for a player go into PLAYER_WALKING via rise(), but died() and the unload hook (lines 187 and 629) only find an owner through the ladder, so plain Hollow are never removed from PLAYER_WALKING; after 12 die, no more rise near that player. Store the owner on the Hollow itself (entity attachment) and remove by that. 3) comeBack and findPlayerWithCaptain only look at online players, so a captain whose player is offline never comes back; the change note says their Hollow keep walking and their ladder is kept. Decide and build it (e.g. due-back captains wait until the owner is on). 4) Each player's own ladder isn't shown anywhere: Ladders, LadderScreen and Rivals.ladder() still show the world-wide one, so 'they should all have their own ladders' isn't visible in game. 5) START and the world-wide COUNT/WALKING still run for non-captain paths; the task said 12 per player replaces START = 6. 6) Lore.md 'The Hollow among the dead' (new) paragraph not written. 7) MEND_THRESHOLD javadoc says 'how often'; it is a health margin. CI is green.
- 2026-11-27 04:44 Text pass on PR #2245 hollow-captains-fix (Kingdoms): reviewed every player-visible string. kingdoms/lang/parts/captain-ground.json all 33 keys player-facing, plain and brief, incl. new ladder.jaymoskingdoms.hollow Hollow captains of %s; all %s/%1$s/%2$s placeholders kept, no format codes touched. changes/kingdoms/hollow-captains-fix.md already player-facing and matches final code (PER_PLAYER=12, MEND_THRESHOLD=2.0F, PATH_REFRESH_TICKS=10, RETREAT 8/3, speed 1.1). Java changes (HollowCaptains, Ladders, Captains) add no hardcoded player strings; all UI text uses lang keys. No rewrites needed.
- 2026-11-27 04:46 Text pass content is complete (reviewed captain-ground.json lang keys and changes/kingdoms/hollow-captains-fix.md; all player-facing, no rewrites needed), but hand-off is blocked: PR #2245 Build Kingdoms check is red (CI run 45974970598 job 140424319552). Two compile errors in the author code branch, not text - I changed no code: HollowCaptains.java:222 calls player.getServer() which does not exist on ServerPlayer in MC 26.3 (needs player.level().getServer()); Ladders.java:135 uses HashMap with no import java.util.HashMap. The code author must fix their branch and push; then text_pass=done can be set.

- 2026-11-28: cancelled (Andy: "Cancel all youth/hermes tasks"); was blocked.
