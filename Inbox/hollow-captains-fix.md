---
status: claimed
for: hephaestus
from: Andy (Claude chat, 2026-11-26)
asked: 2026-11-26
claimed_by: hermes-fixer
pr: 
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
