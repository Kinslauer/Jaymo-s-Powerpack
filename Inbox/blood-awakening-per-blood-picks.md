---
status: open
for: any
from: Andy (decision card, 2026-11-24)
asked: 2026-11-24
claimed_by: 
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
