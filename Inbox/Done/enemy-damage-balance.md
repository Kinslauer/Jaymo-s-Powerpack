---
status: done
for: hermes
from: tiv (Discord, Hotspot / parthenon / Dashboard thread, 2026-11-18)
asked: 2026-11-18
thread: 1569107033607643388
claimed_by: claude-desktop-origins-mod-f1
pr: 1970
suggested_owns: []
writer: hermes-writer
text_pass: done
---

# Enemy damage balance: basic attacks primarily, heavy moves occasionally

Andy wants enemies to deal far less damage overall. Basic attacks should be the primary move and deal less per hit; more damaging moves should be used only occasionally.

## What's wanted

- Enemy damage reduced across the board
- Basic attacks used most of the time, low damage per hit
- Heavy/damaging moves used occasionally, not every hit
- Applies to mobs/enemies in combat (Powers and/or Kingdoms combat systems)

## Notes

Combat balance task — likely touches Powers combat (zfighter, power systems) and/or Kingdoms mob combat. Needs investigation into which classes control enemy attack damage and move selection. No code changes yet — research first.
- 2026-11-21 09:45 Text pass on PR #1970 (claude/enemy-damage-balance): code changes are damage numbers and cooldowns only (Powers: ZFighterRogue, ZFighterNpcKi, RivalAlien, XenoDouble, ChronoEcho, Unseeing; Kingdoms: Grizzly, Feral Wolf, Coyote, Moose, Wild Boar) - no player-visible strings in code, no lang/dialogue/hint changes. Player-visible text is the two change notes, changes/powers/enemy-damage-balance.md and changes/kingdoms/enemy-damage-balance.md; both are already plain, player-facing and follow changes/README.md (title line, what the player will see, 'Not tested in game.'), so no rewrites were needed. pr_checks passed: 13 changed files against origin/master.
- 2026-11-22 12:47 PM Claude (desktop session, Andy: "Pickup 1970"): merged master, undid the re-indentation (numbers only now), added MobCombat heavy-move 1-in-4 for armed humanoids, rewrote the change notes (heavy moves hit softer). CI running.
- 2026-11-22 14:29 PR #1970 is merged into master; moved to Inbox/Done.
