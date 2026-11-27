---
status: in-review
for: claude
from: Andy (Claude thread Rework webs and symbiote from Legends Superheroes)
asked: 2026-11-26
claimed_by: claude-thread
pr: 2283
---

# Legends patch 4: symbiote suit moves and the suit-ups (incl. four Rig suit-ups)

Andy, 2026-11-26, picked all 61 Legends rows; Codex and Gemini are out of usage, so Andy chose "Hand them to Hermes via YOUTH" for patches 3 and 4. The Claude thread "Rework webs and symbiote from Legends Superheroes" reviews the result.

**Base branch: `claude/project-thread-aaw9tm`** (NOT master: it already holds patches 1, 2 and 5, and this patch builds on their web code). `git fetch origin claude/project-thread-aaw9tm`, make your own worktree and branch from FETCH_HEAD (e.g. `hermes/legends-p4`). Open ONE PR with **base `claude/project-thread-aaw9tm`**, never master, and never merge it. Then `--set legends-p4-symbiote-suit-moves status=in-review pr=<n>`.

Read first: AGENTS.md, `.claude/rules/*.md` matching your paths, `docs/Legends-Plan.md` (Licences, Rules every patch keeps, and the whole "Patch 4" section), `docs/vault/Context/Glossary.md`, `docs/codemap/powers.md`, skills minecraft-26-3-api, new-gameplay, powers-keys.

**Wait for patch 3 (task legends-p3-big-symbiote) to be merged into claude/project-thread-aaw9tm before starting**: both touch the symbiote code.

Rows (all of them, fully): sy_grind sy_stilts sy_lunge sy_tempest sy_snuff ot_suitup. Build them into the existing symbiote code (WebHeldGrab, WebTendrilWalk, satiety, the fire-fear reflex, costumes) and the Rig.

Suit-ups, Andy's words: on ot_suitup "rig too.", then "the rig should have a few different suit ups", then "and one where it assembles on you while falling from a great height, then thrusters kick in to stop the momentum". So: the symbiote suit-up pose (arms crossed, thrown out as it finishes), costume changes, and FOUR Rig suit-ups as the plan lists: plates slam on standing, step-in fold shut, flight-in midair lock, and the falling assembly where thrusters fire to stop the fall (no fall damage). Find the Rig in client/rig/* and the rig package.

Rules:
- Legends mods are All Rights Reserved: ideas only, our own code, poses and names.
- No painted textures (MagicFx/particles/existing art), no new dimensions or portals, no screen text stating lore.
- Server validates everything the client sends (range, cooldown, power). No client classes from src/main.
- New WebPose ids continue after the highest existing one (56 on the base branch).
- New key combos must not shadow existing web binds: check them all (Sneak+C = Blossom, Back+C = hunting dive, Back+Jump+C = web storm).
- Moves end on death, downed, disconnect, dimension change and power loss. Never leave mobs NoAI or invulnerable; never pin or haul bosses (WebHaul.haulable / WebPin.pinnable); ground breaking follows WebSlam's rules.
- Change note only in `changes/powers/legends-symbiote-suit-moves.md` ("Built; not played."). Never touch version lines, MODLOGs or docs/codemap. No version numbers in comments.
- Build: compileClientJava. Run tools/pr_checks.py.

PR body: each row, what you built, classes, keys, every deviation from the plan, what you couldn't check.

## Notes
- 2026-11-26 23:01 Held until legends-p3-big-symbiote is merged into claude/project-thread-aaw9tm; the Claude thread sets for=hermes then.
- 2026-11-27 03:02 Update 2026-11-27: patches 1, 2, 5 are merged into master (#2257): branch from origin/master and PR against master once patch 3 is merged.
- 2026-11-27 10:27 Andy 2026-11-27: 'finish the entire plan here': the Claude thread builds it.
