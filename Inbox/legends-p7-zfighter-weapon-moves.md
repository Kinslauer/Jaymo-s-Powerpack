---
status: open
for: gemini
from: Andy (Claude thread Rework webs and symbiote from Legends Superheroes, 2026-11-26)
asked: 2026-11-26
claimed_by: 
pr: 
---

# Legends patch 7: Great Ape moves, staged beams, instant step, stacking power-up, chain blades, cross slash

Andy, 2026-11-26: "have codex and gemini take a patch each then check it afterward". This is Gemini's patch.

Plan, licences and rules: `docs/Legends-Plan.md` on branch `claude/project-thread-aaw9tm` (`git fetch origin claude/project-thread-aaw9tm && git show FETCH_HEAD:docs/Legends-Plan.md`). Read its Licences and Rules sections first. Work from master on your own branch and worktree, one PR, change note `changes/powers/<branch>.md`. The Legends add-ons are All Rights Reserved: ideas only, our own code, poses and names. Legends Mod Core is LGPL-3.0: its files may be ported with its notice; a copy is in the Claude project files (legends-study/core-lgpl/), and the jar is https://cdn.modrinth.com/data/TfKZ50GD/versions/kCUYqPqz/Legends-1.20.1-ss1.0.25.jar (assets/legendsmod/legends_animations/player.animation.json, animations/item/katana.animation.json, fx/*.fx). Our player animation library is zigythebird Player Animation Library (PalAccess); it plays Bedrock-format animations. Build to check (compileClientJava), run tools/pr_checks.py, never merge. When the PR is up, set status=in-review pr=<n>: the Claude thread "Rework webs and symbiote from Legends Superheroes" reviews it afterwards (Andy: "have codex and gemini take a patch each then check it afterward").

### Patch 7: Z Fighter and weapon moves

Power: Z Fighter, weapons.

- **Great Ape moves** (`ot_ape`, Anime, M): A full set for the giant ape: swipes left and right, a roar, a ground slam, a leap smash and a boulder throw, each with its own pose. Our side: ZFighterApe already grabs, carries and slams. This fills in swipes, the boulder throw and the leap smash.
- **Staged beam firing** (`ot_beam`, Anime, S): Beams go in stages: gather in the hands, hold, fire, keep firing, end. Foes inside a beam you're holding are outlined red so you can see you're on them. Our side: ZFighterBeams and the beam clash exist. This adds the red outline and smoother stages.
- **Instant step** (`ot_transmit`, Anime, S): Two fingers to the brow and you're a few blocks ahead in a flash, or behind the foe in your sights if you sneak. Our side: A Z Fighter move once ki sense is learned. A short shimmer where you left and where you land.
- **Stacking power-up** (`ot_kaio`, Anime, M): Hold to raise a power-up by stages, a red aura growing each time. Above half, it hurts you every few seconds; letting go keeps it for a while, then leaves you slowed. Our side: A Z Fighter form boost, on ZFighterKiAura's aura with its own colour. Our own name for it.
- **Chain blades** (`ot_chain`, Misc, M): Chained blades that pull a foe to you, and a spinning cyclone of them all around you. Our side: Our chain weapons (the chain weapons models) could take both moves.
- **Cross slash** (`ot_xslash`, Power Rangers, S): Dash through a foe drawing an X across them; a moment later it bursts. Our side: A Weapon Forms move for swords.

## Notes
- 2026-11-26 16:10 gemini's claim had no sign of work for 30 min: handed back as open.
