---
status: in-review
for: prometheus
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: claude-cloud
pr: 1230
suggested_owns: [art/heart-crystal/, src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore.png, src/main/resources/assets/jaymospowers/textures/block/heart_crystal_ore_deepslate.png, src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore.json, src/main/resources/assets/jaymospowers/models/block/heart_crystal_ore_deepslate.json, src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore.json, src/main/resources/assets/jaymospowers/blockstates/heart_crystal_ore_deepslate.json]
---

# A new heart crystal ore block, modelled in Blockbench

Tiv's reference photos: a purple crystal heart, glowing like amethyst, with one big crystal in the middle.

## What's wanted

Tiv, in the Tasks thread: "I want prometheus to work on a new heart crystal ore block preferably in blockbench it should look like the reference photos, glowing in the dark like amethyst and a large single crystal in the senter of the ore."

## References

- `E:/Jaymo Vault Live/Assets/heart-crystal-reference-1.jpg` — a dense purple druse in a heart outline on black: hundreds of small pointed crystals, pale lilac highlights, deep violet crevices.
- `E:/Jaymo Vault Live/Assets/heart-crystal-reference-2.jpg` — the same heart as a slice, with a pale rough rock rim around the crystal field, and more amethyst behind it.

## Notes

- Blockbench model, not a flat cube texture: one large crystal standing in the middle of the ore, smaller crystals clustered round it in a heart shape, the plain stone or deepslate rock around them.
- "Glowing in the dark like amethyst": amethyst in vanilla doesn't actually light the room, it just reads bright. Decide and say in the PR whether this block emits light or only looks lit, and make the texture carry the glow either way.
- Both variants — the ore exists in stone and deepslate; the deepslate one should look like the same heart in darker rock.
- There was an earlier art task for these textures (`ore-hearts-art`); check what is in master first and say what this replaces.
- If the model is cut out rather than a full cube, say what that costs: blockstate variant, item model, and how it behaves next to other blocks.

## Report

PR says what a player will see when they find the ore in the dark, and a "not tested in game" line if it hasn't been placed in a world.
- 2026-11-09 11:27 Question: The original amethyst-heart models, textures, editable sources and inspected renders are pushed as WIP 9db47f62a, and PR checks pass. Four full-build attempts were stopped or stalled by concurrent Gradle activity; the offline retry found an abandoned Loom cache lock. Can this be resumed for the build and PR once Gradle is free?
- 2026-11-09 11:56 2026-11-09 Answer: yes, resume - build now. Gradle is idle (no daemon, no stale fabric-loom lock: the one that blocked the four attempts is gone), and only one builder builds at a time now, so nothing else should be compiling over you. Carry on from your WIP 9db47f62a on origin/claude/heart-crystal-ore-block (no worktree yet: git worktree add E:/wt/heart-crystal-ore-block origin/claude/heart-crystal-ore-block, then set upstream and pull), run the full sh ./gradlew build in the foreground with a generous timeout, then open the PR with what a player sees in the dark and a not tested in game line.
- 2026-11-10 Resumed: full ./gradlew build passed on JDK 25; PR #1226 opened. Emits no light (looks lit only). Not tested in game.
- 2026-11-10 Moved to the task branch cloud/heart-crystal-ore-block; PR #1230 replaces #1226 (closed). Head c189cf7d: build passed, pr_checks passed.
