---
status: blocked
for: prometheus
from: pc-claude
asked: 2026-11-18
claimed_by: hermes-modeller
pr: 
writer: hermes-writer
text_pass: 
---

# Wither: a new model, animations and sounds (modelling bot)

**Andy's words (2026-11-18):** "yes send it to prometheus" (the Wither's new look, the one pick left from the Fire and Wither list: "A new look and sound (wr_look): our own Wither model, animations and sounds in the pack's style, like the boss remodels ... previews to Andy first").

**What's wanted:** the vanilla Wither drawn as our own Blockbench model in the pack's style, with animations for everything its new fight does and its own sounds. The behaviour is already in the game (PR #1717, Kingdoms `wither/`); this task is only how it looks and sounds.

**Previews to Andy first.** Before any of it goes into the mod, write a spec doc (`docs/Wither-Model-Spec.md`, like `docs/Fortress-Knight-Model-Spec.md` and `docs/Lair-Beasts-Model-Spec.md`) and render previews of the model in its main poses with `tools/bbmodel_preview.py`. Post them as a question under this task's Notes (`py tools/vault_sync.py --note wither-model "..."`) and set `status: blocked` until Andy answers. Only build the rest once he's said yes.

**What it must animate** (the moves in `kingdoms/src/main/java/com/jaymoskingdoms/wither/WitherMoves.java` and `WitherFight.java`): idle hover, the three heads looking on their own, the ravage (lobbing skulls at buildings), the orbit (circling, faster and angrier in its last third), the beam tell and firing, the charge tell, the rush and the stun after a blocked charge, climbing for the skull rain, throwing blue skulls, the spawn and the death. Moves the server starts go on an `action` controller through `triggerAnim` from the existing hooks; head look stays in `adjustModelBonesForRender`.

**Sounds:** our own, made for this model (no vanilla Wither sounds re-pitched as the whole answer, nothing copied): ambient, hurt, death, spawn, the beam's charge and shot, the charge rush, the skull throw. Credit and licence beside each file if any source is used (CC0 or CC BY only).

**Where to start:** `docs/AI-HANDBOOK.md` ("Adding a GeckoLib mob" / "Animating a Blockbench model with keyframes of our own"), `kingdoms/src/client/java/com/jaymoskingdoms/client/beasts/BbMobRenderer.java` and how `BeastsClient` swaps a vanilla mob's renderer, the boss remodels that already went through it, vanilla `WitherBoss` and its renderer in the 26.3 sources, `tools/bbmodel_geckolib.py`, `tools/bbmodel_preview.py`. Lore: Lore.md "The Wither's coming".

**Rules:** CLAUDE.md as always: one PR, change note in `changes/kingdoms/`, no version bumps, write keyframes with a script into the editable `.bbmodel`, typed MagicFx/Fx for any glow, credits beside the art and in `textures/entity/bb/CREDITS.txt`, Wither: Reincarnated is All Rights Reserved (ideas only, never its art or sounds), builds on the Deck or CI only (never Andy's PC). Say plainly in the PR that it hasn't been seen in game.

## Notes
- 2026-11-18 03:34 Model spec and minimal .bbmodel created. Pending Andy's preview approval.
- 2026-11-18 11:31 hermes-writer's claim had no sign of work for 30 min: handed back as in-review.
- 2026-11-18 11:50 Text pass: no PR yet and no player-visible text to review. The modeller (prometheus/hermes-modeller) owns this task; it is pending Andy's preview approval on the Wither model spec and .bbmodel. Nothing for the writer to text-pass until a PR exists.
