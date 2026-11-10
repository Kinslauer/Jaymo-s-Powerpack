---
status: changes
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: hermes-a
pr: 1256
suggested_owns: [src/main/java/com/jaymospowers/entity/ape/, src/main/java/com/jaymoskingdoms/beast/]
writer: hermes-writer
text_pass: 
---

# Ape/giant trees-flying, screenshake and dust on steps/landings

Tiv, in the Tasks thread: "The apes and giants should send trees flying when they hit them while sprinting. There should also be massive scaling screenshake and dust particles for all their steps and landings."

## What's wanted

1. **Trees flying on sprint hit**: When a great ape or giant entity sprints into a tree (or similar wooden block/plant), the tree should be knocked flying / broken and send wood/log items flying outward, as if the impact displaced the tree.
2. **Massive scaling screenshake**: A screen-shake effect that scales with the entity's size — great apes and giants produce a much more intense shake than a player, with the intensity proportional to the entity's height/damage type.
3. **Dust particles on steps and landings**: Whenever a great ape or giant takes a step or lands from a jump, dust particle effects should spawn at the entity's feet, with scale matching the entity's size (giants kick up larger, longer-lasting dust clouds).

## Notes

- Existing ape entities: check `src/main/java/com/jaymospowers/entity/ape/` for current movement/step/attack logic; the apes-xeno-voices-parasites-blood PR (#1241) recently added voice/titan changes for ape forms — this task extends that work.
- Giant entities exist under Kingdoms (`com.jaymoskingdoms.beast`); those also have step/landing logic.
- Trees: use the pack's existing tree/wood block interaction patterns (e.g. how axes chop trees, or how entities break blocks) as the model for "tree flying."
- Screenshake: follow the existing screenshake pattern in the mod (check `References/263-api-pitfalls.md` or `docs/codemap/powers.md` for any existing shake utilities).
- Dust particles: use `ParticleTypes.SMOKE_NORMAL` / `ParticleTypes.DUST` with appropriate `ParticleOptions` and `double` values for speed/lifetime scaled to entity size.
- Keep changes modular: each feature (tree-flying, shake, dust) can be a separate PR, but they share the same entity touch/step events.
- Say back the task name and that it's open for Hermes, and add it to the todo list as `pending`.
- 2026-11-10 21:14 Review of #1256 (head aeccdfe), cloud reviewer, changes needed:
1. The branch was cut from cloud/astronomy-scholar-journal and carries two old pr-1236/pr-1239 merges, so the PR would also ship the unmerged Astronomer's Journal and Varasuchus GeckoLib work (49 files, 574k lines). Rebuild the branch from origin/master with only commit aeccdfe (GiantTrample, ZFighterLineage, AetherGiant) plus GiantTrees.java if that is new.
2. No PR checks ran on the head, and there is no change note for this feature (changes/powers/<branch>.md and changes/kingdoms/<branch>.md). The two notes on the branch belong to the astronomy and Varasuchus work.
3. GiantTrample.sprintHit: the search box runs from getEyePosition() along the look vector, so a tall giant looking ahead checks only at head height and misses trunks at its feet. Search from the feet (giant.position()) up SPRINT_HEIGHT, along the travel direction (getDeltaMovement, horizontal) instead of the look.
4. Mob giants (AetherGiant etc.) rarely report isSprinting(), so the tree throw would almost only happen for player giants. Use horizontal speed above a threshold instead.
5. GiantTrample: the Javadoc adds '(0.21.XX)'; CLAUDE.md says no version numbers in comments. The indentation of tick/sprintHit/kingdoms() is off by one tab.
Not tested in game.
