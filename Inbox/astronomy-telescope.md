---
status: in-review
for: prometheus
from: hermes (Andy's astronomy pick-list, 2026-11-08)
asked: 2026-11-08
thread: 1569144710264791172
claimed_by: hermes-modeller
pr: 1189
suggested_owns: []
text_pass: done
writer: hermes-writer
---

# Astronomy 2: the telescope

A telescope you place and aim, and a reading that can fail.

## What's wanted

From the pick-list, rows **The telescope** and **Measurements that can fail**:

- A basic telescope to begin observing; the Altazimuth Mount skill adds azimuth and elevation scales, and each telescope keeps its own aim.
- A reading depends on daylight, weather, a clear sky, and the target being above the horizon.

## Notes

- **Depends on `astronomy-sky`** — there is nothing to point at until stars exist.
- The block is closer to our cannons and ballistae than to furniture: it should have an aim, a state, and a face you can see it pointing.
- "Can fail" is the point. A cloudy night or a daytime attempt should say why it failed in one plain line, not silently do nothing.
- Keep the scales a visual on the mount, not a debug readout — the numbers belong to the instrument, not to a HUD.

## Also ours to decide

Whether one telescope is enough or there are tiers (basic, mounted, brass) — decide and say why in the PR.
- 2026-11-09 08:34 Andy (2026-11-09): Prometheus to start on the telescope.
- 2026-11-09 09:05 Question: PR #1185 now supplies the base telescope art. For continuous per-block azimuth/elevation, should I convert that model to an articulated GeckoLib rig after #1185 merges rather than open a duplicate static-model PR?
- 2026-11-09 09:28 Andy (2026-11-09): use GeckoLib. After #1185 merges, convert its telescope model into an articulated GeckoLib rig (base, azimuth turn, elevation tilt) instead of a second static-model PR; follow docs/AI-HANDBOOK.md 'Adding a GeckoLib mob to Kingdoms' for the GeckoLib setup.
- 2026-11-09 10:03 hermes-modeller's last text pass stopped before finishing; handed back so it can carry on.
- 2026-11-09 11:31 Text pass: rewrote change note (changes/kingdoms/claude-astronomy-telescope.md) for player-facing clarity — changed technical GeckoLib rig description to focus on what the player will see (smooth aiming animation, mount turns/barrel tilts, single telescope tier with engraved scales). Build and pr_checks have pre-existing environment issues (Windows file locking, version not bumped per merge-time policy).
- 2026-11-09 11:33 Text pass: rewrote change note (changes/kingdoms/claude-astronomy-telescope.md) for player-facing clarity
- 2026-11-09 11:44 Review (head a729532): pass for the rig. GeckoLib telescope with base > azimuth > elevation bones: the tripod stays put, the mount yaws and the barrel tilts on its axle; idle/azimuth/elevation/aim animations; one telescope, no tiers, reasons given. Not tested in game. THE TASK IS NOT FINISHED: no block registration, saved per-telescope aim, Altazimuth skill, or readings that fail with a plain reason. That gameplay needs a builder task (it also depends on astronomy-sky). For that task: models/block/telescope.json (merged in #1185) has UVs up to 64, but block-model UVs are 0-16, so it would sample outside its sprite if ever drawn; render the block through the GeckoLib renderer (invisible block model) or divide those UVs by 4.
