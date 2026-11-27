---
status: open
for: apollo
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

## Notes
- 2026-11-26 18:30 YOUTH delegated this to apollo (Claude approved): multi-system Java in the big-suit code (WebHulk, WebBrute, WebFury, WebLeap, WebSlam, takeover struggle); branch from claude/project-thread-aaw9tm and open the PR against that branch, NOT master, and never merge it
- 2026-11-27 01:36 legends-p3's claim had no sign of work for 30 min: handed back as open.
- 2026-11-27 03:02 Update 2026-11-27: PR #2257 (patches 1, 2, 5) is merged into master, so branch from origin/master now and open the PR against master.
- 2026-11-26 23:58 Update 2026-11-27, Claude (reviewer): Codex built every row and hit its usage limit; Claude compiled it, fixed 3 compile errors and 2 rule breaks, and pushed. START FROM BRANCH codex/legends-p3 (head 006772254b, compileClientJava + pr_checks pass): git fetch origin codex/legends-p3, own worktree, new branch hermes/legends-p3 from FETCH_HEAD. PR against master (patches 1,2,5 are merged), never merge. Keep changes/powers/legends-big-symbiote.md (Built; not played). Remaining work, each its own commit: (1) DEFAULT KEYS: WebBruteClient.DEFAULTS uses Numpad 2-8, which clash with Kingdoms PlanGhosts (Numpad 8, 2) and MedallionClient (Numpad 1-9). Pick defaults that no mod in the pack (Powers, Kingdoms, Kitchen) or vanilla uses; grep every KeyMapping and InputConstants.KEY_ in all three mods and list them in the PR. (2) WALL vs LEAP: WebBruteMovement ~137 runs the climb before the charged leap, so Jump at any touching wall (even behind you) climbs; only climb when facing the wall (look within ~60 deg of the wall normal) so a leap can still be charged with your back to a wall. (3) BUSY WHILE STONES FLY: WebBruteArts ~231 keeps a move busy until all stones land, blocking hooks, G and other moves, and pound() can restart (~359, 442); end the move when its own ticks end and let stones finish on their own list. (4) DOC COMMENTS: three javadocs in WebSymbiote sit above the wrong member after new code was inserted (near 1008, 3214, 4798); move them back. (5) PLAN GAPS (docs/Legends-Plan.md Patch 3): hk_rubble must roll at the aimed foe (steer like hk_crack does) and tear the ground through WebSlam.breakGround; ot_flip slabs must tear the strip up through WebSlam.breakGround (no new block-breaking path); ot_quake boulders must fall on foes around you, not just near you; hk_losing: hitting the beat must win control back faster than mashing (check WebSymbiote.struggle numbers and say the math in the PR). Rules: check every 26.3 API against E:/mcsrc-26.3 (TamableAnimal is net.minecraft.world.entity.TamableAnimal); no client classes in src/main; bosses never dragged/shoved (WebBruteArts.velocity guards it, keep it); no version numbers in comments; build with ./gradlew compileClientJava and run tools/pr_checks.py before pushing. PR body: each fix, keys chosen, what you could not check.
- 2026-11-27 00:06 YOUTH delegated this to apollo (Claude approved): Multi-system Java in the big-suit code (WebHulk, WebBrute, WebFury, WebLeap, WebSlam, takeover struggle). Start from codex/legends-p3 head 006772254b, own worktree and branch hermes/legends-p3, PR against master, never merge. Claude's 5 numbered fixes from the 2026-11-27 note: default keys (no pack/vanilla clashes), wall-facing climb check, move busy ends when its ticks end, three misplaced javadocs in WebSymbiote, Patch 3 plan gaps (hk_rubble steer, ot_flip/ot_quake/ot_breakground via WebSlam.breakGround, hk_losing struggle math). Tell it to read skills/minecraft/minecraft-modpack-workflow/references/review-lessons.md lessons 24-29 first, then set legends-p3-big-symbiote in-review with the PR number.
