---
status: in-review
for: apollo
from: Andy (Waiting on Andy page, 2026-11-26)
asked: 2026-11-26
claimed_by: hermes-a
pr: 2280
claim_handbacks: 3
writer: hermes-writer
---

# Small armies pick list: build Andy's 4 picks

**Andy's picks (2026-11-26, Waiting on Andy page):** 4 of 10 rows on `Picklists/Small-armies pick-list.md` (ticks written into that file). No notes. All sources are All Rights Reserved or Forge-only: ideas only, our own code and art.

- `su-tentacles` (Symbiotes Unleashed / Extra Visuals): **Living tentacle animations**: Animated tentacles, organic armor movement, dynamic symbiote visuals on a host entity. For us: GeckoLib animation layer on an existing mob — tentacle bones on our xeno or parasite mobs, not a new entity.
- `su-host` (Symbiotes Unleashed / Extra Visuals): **Symbiote host possession**: A symbiote bonds to a player or mob, changes their look and abilities. For us: Powers already has xenoforms and ultimates — this is a skin and a passive on top, not a new system.
- `hog-powers` (Hand of God): **Nine divine powers**: Control Sun and Moon, time, matter, reality and lightning — god-like player abilities with visual effects. For us: Powers already has time (Chronomancy), weather, lightning, matter (alchemy). This is a power school, not new mechanics.
- `hog-celestial` (Hand of God): **Sun and Moon control**: Move or freeze the Sun and Moon. For us: Kingdoms' world time or Powers' existing celestial hooks.

Not picked: clay soldiers, Clay Warfare colours/buffs/remote/book.

## Rules
- Lore first (who grants divine powers, which patron gods; who carries symbiotes). Seven patron gods + The Truth are canon.
- Builds on existing systems as the rows say (Chronomancy, weather, lightning, alchemy; xenoforms, GeckoLib layers). No slash commands for player features. Plan doc first, then patches.

## Notes
- 2026-11-26 15:47 YOUTH delegated this to apollo (Claude approved): build Andy's 4 picks from scratch; the sources are All Rights Reserved, so copy no code or art
- 2026-11-26 16:39 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-26 16:40 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-26 16:45 2026-11-26 16:45 apollo: worktree created at E:/wt/small-armies-picks-2 (branch claude/small-armies-picks-2). Ready to start coding: plan doc first, then the 4 picks (tentacles, host possession, divine powers, celestial control). Picking up next run.
- 2026-11-26 17:18 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 17:54 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 18:01 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-26 18:58 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 19:34 hermes-a's last run stopped before finishing; handed back as open.
- 2026-11-26 20:22 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 21:00 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 21:32 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 21:39 hermes-a's last run stopped before finishing; handed back as open.
- 2026-11-26 21:57 2026-11-26 20:55 apollo: PR #2259 opened (plan doc + change note, no code). pr_checks pass. Worktree has broken disk state; next run restores it and sets in-review.
- 2026-11-26 22:35 hermes-a's claim had no sign of work for 30 min: handed back as open.
- 2026-11-27 11:45 apollo: PR #2280 (Hand of God: Celestial Hold and Celestial Turn) — all GitHub checks green. Plan doc + change note + code (CelestialCommand, HandOfGodPower). Built, not played. Set in-review.
- 2026-11-27 00:28 Text pass: reviewed PR #2280 (Hand of God: Celestial Hold and Celestial Turn). Player-visible strings: src/main/resources/assets/jaymospowers/lang/parts/handofgod.json (power name/desc, Celestial Hold/Turn ability keys, hold_taken/hold_cooldown/turn_cooldown messages); in-game system messages in src/main/java/com/jaymospowers/power/handofgod/CelestialCommand.java (same message keys, %s seconds); registration only in JaymosPowers.java; HandOfGodPower.java has no player-visible strings. All strings already plain, brief and in-voice, matching chrono/web cooldown phrasing and the documented ability-key convention, so no rewrite was needed. changes/powers/small-armies-picks-4.md already has the player-facing description, cause (power/handofgod/), changes and the 'Built; not played.' line.
