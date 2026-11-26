---
status: open
for: hermes
from: Andy (Claude thread Rework webs and symbiote from Legends Superheroes)
asked: 2026-11-26
claimed_by: 
pr: 
---

# Legends patch 3: the big symbiote, after their Hulk

Andy, 2026-11-26, picked all 61 Legends rows; Codex and Gemini are out of usage, so Andy chose "Hand them to Hermes via YOUTH" for patches 3 and 4. The Claude thread "Rework webs and symbiote from Legends Superheroes" reviews the result.

**Base branch: `claude/project-thread-aaw9tm`** (NOT master: it already holds patches 1, 2 and 5, and this patch builds on their web code). `git fetch origin claude/project-thread-aaw9tm`, make your own worktree and branch from FETCH_HEAD (e.g. `hermes/legends-p3`). Open ONE PR with **base `claude/project-thread-aaw9tm`**, never master, and never merge it. Then `--set legends-p3-big-symbiote status=in-review pr=<n>`.

Read first: AGENTS.md, `.claude/rules/*.md` matching your paths, `docs/Legends-Plan.md` (Licences, Rules every patch keeps, and the whole "Patch 3" section), `docs/vault/Context/Glossary.md`, `docs/codemap/powers.md`, skills minecraft-26-3-api, new-gameplay, powers-keys.

Rows (all of them, fully): hk_clap hk_leap hk_rubble hk_tectonic hk_steps hk_wall hk_punch hk_walk hk_rampage hk_crack hk_losing hk_nova ot_clap ot_quake ot_flip. Build them into the existing big-suit code (power/web: WebHulk, WebBrute, WebFury, WebLeap, WebClimbPose, WebSlam, the takeover struggle).

Rules:
- Legends mods are All Rights Reserved: ideas only, our own code, poses and names.
- No painted textures (MagicFx/particles/existing art), no new dimensions or portals, no screen text stating lore.
- Server validates everything the client sends (range, cooldown, power). No client classes from src/main.
- New WebPose ids continue after the highest existing one (56 on the base branch).
- New key combos must not shadow existing web binds: check them all (Sneak+C = Blossom, Back+C = hunting dive, Back+Jump+C = web storm).
- Moves end on death, downed, disconnect, dimension change and power loss. Never leave mobs NoAI or invulnerable; never pin or haul bosses (WebHaul.haulable / WebPin.pinnable); ground breaking follows WebSlam's rules.
- Change note only in `changes/powers/legends-big-symbiote.md` ("Built; not played."). Never touch version lines, MODLOGs or docs/codemap. No version numbers in comments.
- Build: compileClientJava. Run tools/pr_checks.py.

PR body: each row, what you built, classes, keys, every deviation from the plan, what you couldn't check.
