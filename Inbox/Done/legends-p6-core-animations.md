---
status: done
for: codex
from: Andy (Claude thread Rework webs and symbiote from Legends Superheroes, 2026-11-26)
asked: 2026-11-26
claimed_by: codex
pr: 2258
---

# Legends patch 6: port Legends Core's animation library (LGPL) + landings, movement set, katana set

Andy, 2026-11-26: "have codex and gemini take a patch each then check it afterward". This is Codex's patch.

Plan, licences and rules: `docs/Legends-Plan.md` on branch `claude/project-thread-aaw9tm` (`git fetch origin claude/project-thread-aaw9tm && git show FETCH_HEAD:docs/Legends-Plan.md`). Read its Licences and Rules sections first. Work from master on your own branch and worktree, one PR, change note `changes/powers/<branch>.md`. The Legends add-ons are All Rights Reserved: ideas only, our own code, poses and names. Legends Mod Core is LGPL-3.0: its files may be ported with its notice; a copy is in the Claude project files (legends-study/core-lgpl/), and the jar is https://cdn.modrinth.com/data/TfKZ50GD/versions/kCUYqPqz/Legends-1.20.1-ss1.0.25.jar (assets/legendsmod/legends_animations/player.animation.json, animations/item/katana.animation.json, fx/*.fx). Our player animation library is zigythebird Player Animation Library (PalAccess); it plays Bedrock-format animations. Build to check (compileClientJava), run tools/pr_checks.py, never merge. When the PR is up, set status=in-review pr=<n>: the Claude thread "Rework webs and symbiote from Legends Superheroes" reviews it afterwards (Andy: "have codex and gemini take a patch each then check it afterward").

### Patch 6: Legends Core animation library (LGPL port)

Power: core/combat, shared.

- **Port Core's animation library** (`ot_corelib`, Core, M): Bring over Core's whole set of player animations as files: landings, flips, rolls, dodges, slides, wall climb and perch, gliding, kicks, uppercut, parry, throw, thunderclap, sonic punches, and the hyperflight and hyperdash loops. Our side: We already play player animation files (assets/jaymospowers/player_animations). Theirs are in the Bedrock format; if our animation library on 26.3 can't read that, a small script converts them. Kept with Core's LGPL notice in a CREDITS file. The Core rows below would then use these keyframes.
- **Landings by height** (`ot_landing`, Core, S): Four landing poses picked by how far you fell, from a light crouch to a full one-knee superhero landing that cracks the ground. Our side: WebSlam has one superhero landing for the slinger. This gives every power user the set.
- **Movement set for everyone** (`ot_moves`, Core, M): Backflip, front flip, double jump, cat leap over a block, sprint slide, long roll and three dodges. Our side: Some exist (WebParkour vaults, rolls, our slide). This makes one shared set any power can use.
- **Katana stance set** (`ot_katana`, Core, M): Swap stance, draw-charge, a four-hit chain ending in a launcher and a finisher, a dash slash, two air slashes and a downward spike. Our side: For our katanas in Weapon Forms. Ours have fewer moves.

## Notes
- 2026-11-26 21:03 codex's claim had no sign of work for 30 min: handed back as open.
- 2026-11-26 claimed again (codex thread, continued by Claude after Codex/Antigravity hit usage limits). Work is in worktree E:\legends-p6-core-animations, branch codex/legends-p6-core-animations; build passed, reviewing before the PR.
- 2026-11-26 Andy said "Merge": PR #2258 squash-merged after all 5 CI checks passed, before the Legends thread's review. Still open for that thread: whether katana_forms.json's arm/torso poses are our own, and an in-game look.
- 2026-11-26 PR #2258 opened (branch codex/legends-p6-core-animations). Pre-PR review fixed 3 bugs (katana charge release, slide clip held too long, Air Step stacking on PowerJump) and credited katana_forms.json's blade tracks in CREDITS. Not played in game; CI builds it.
- 2026-11-26 20:39 codex's claim had no sign of work for 30 min: handed back as open.
