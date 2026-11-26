---
status: blocked
for: quill
from: Andy (decision card, 2026-11-24)
asked: 2026-11-24
claimed_by: hermes-writer
pr: 
---

# Blood Awakening: a pick list of per-blood upgrades

## What's wanted

Andy (2026-11-24, on the Blood Awakening card in thread "Build Blood Awakening passive upgrade"): "Shared rule now, per blood
later". The shared rule went in with the blood-awakening-passive-upgrade PR: learned, every wait on a blood's move is halved
(cooldowns, Kindle's heat, taking a foe in, the ice prison twice a day) and what a heritage-key move gives lasts 1.5x
(Kingdoms `TreeLocks.awakened` / `TreeLocks.wait` / `AWAKENED_WAIT` / `AWAKENED_LONGER`; Powers `core/TreeLocks.wait`).

Now: make a tickable pick list (artifact page, db picks/andy) with one or more upgrade ideas per blood move, for Andy to tick.
Each row: the blood (or look), its move, what Blood Awakening would add on top of (or instead of) the shared rule: a stronger
or longer version, a second step to the move, a cheaper cost, a new use. Ask whether a ticked per-blood upgrade replaces the
shared rule for that blood or adds to it.

## Where it lives

- Moves: Kingdoms `HeritageKits.Move` (45 moves, `kind(...)` and `look(...)` tables), `HeritageKits.cast`.
- Held moves: Powers `HeritageMoves` (Thunderclap, Sunflare), `EmberKindle`, `RaceMoves` (Stretch), `FrostBeam`; Kingdoms
  `FrostPrison`, `BioBuilt`, `RubberFolk`, `HeritageHold`.
- The Verdant's push-button regrowth (`ZFighterLineage.regrowNow`) is already built; leave it unless Andy says otherwise.

## Notes
- 2026-11-25 23:01 2026-11-25 approved cleanup: Approved cleanup: #2117 implemented the shared rule, not this pick list. Prepare one or more selectable ideas for every move; no upgrades implemented before Andy selects them. Question sent: add to shared bonus, replace it, or decide per move.
- 2026-11-26 00:28 YOUTH delegated this to Quill (Claude approved): tickable pick list of per-blood upgrades for Andy; whether a pick adds to or replaces the shared bonus is decided per move.
- 2026-11-26 00:45 Text pass N/A — this is a writing task, not a PR text pass. Wrote the per-blood upgrade pick list to E:/Jaymo Vault Live/Picklists/Blood awakening per-blood picks.md covering all 47 move/kits (20 heritage kinds + 27 look variants), each with 1-2 upgrade ideas (stronger/longer, second step, cheaper cost, new use), grounded in HeritageKits.Move cooldowns/gifts/flaws and the shared rule from PR #2117 (halved waits + 1.5x duration). Left every row unticked (- [ ]) for Andy to select, with an add-vs-replace question flagged per row.
- 2026-11-26 00:46 Question for Andy: when a per-blood upgrade is ticked, does it add to the shared rule (halved waits + 1.5x duration) for that blood, or replace it? The pick list leaves each row marked add/replace blank for Andy to decide per move. No upgrades implemented yet.
