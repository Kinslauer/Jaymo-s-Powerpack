# Devil's Hole Pupfish — write-up

Asked by tiv for Andy (Discord, 2026-11-18): the Devil's Hole Pupfish in relation to Jung's Devil's Palm and Mana Spirit
ideas. A write-up to decide whether it becomes a feature; nothing is built.

## What's in the pack today (checked)

- **The fish**: `alexsmobs:devils_hole_pupfish` (Alex's Mobs). Harmless. Alex's Mobs only places it at world
  generation, in one spot it looks for in a desert; when our altered desert and cave generation leave no valid spot it
  says "this world will have no pupfish", and the only way to get one is `/summon alexsmobs:devils_hole_pupfish`.
- **Kitchen** already treats it as a fish corpse (Kitchen 0.4.29: one click loots it and the body vanishes with a splash).
- **The Devil's Palm** is not in the code (searched for `devils_palm` / "Devil's Palm" across all three mods and the docs).
  It is Jung's idea from his forum post (2026-10-26): a biome structure where monster spawn rates go up.
- **Mana spirits** are built (Kingdoms `spirits/`, `docs/Spirits-Plan.md`): an *attuned* player with no spirit who is
  left at a fifth of their health or less, or downed, has a 1 in 18 chance of a spirit taking notice; spirit sickness
  follows for up to 5 days (sooner with meditating, casting, another brush with death), and the spirit's waking cures it.
  One spirit for life. Jung's "ancient tablet of inscribed runes" quest is **not** built: today the spirit wakes by itself.

## The real fish (the sympathy rule)

The real Devil's Hole pupfish lives in one place on Earth: a single water-filled limestone cavern in the Nevada desert,
on one shallow shelf of rock where the light reaches and algae grows. Its whole species has numbered from a few dozen to
a few hundred; it is counted by divers every year, and the shelf is fenced and watched. It needs warm, still water and
that one shelf to spawn on. Anything we build should teach that: one pool, a tiny count, easily lost, and kept alive by
whoever looks after it.

## The canon, and what doesn't fit

- **Not a mana spirit.** Lore.md ("The long defence") says the mana spirits were great wizards once (Ambrose, Grimsby,
  Thessaly, Varro, the Quiet One). A fish can't be one of them, and "one spirit for life" stays as it is. The first draft's
  idea (call a pupfish from the tablet instead of a spirit) would break both, so it is dropped.
- **Mana and the attuned** are canon: the attuned see what others can't. That is the honest link between the fish and
  the spirits: both belong to the attuned's side of the world.

## Proposal (for Andy to pick from)

1. **The Devil's Hole** (the fish's home, sympathy first): one deep, warm sinkhole pool in a desert per world, a single
   lit shelf near the surface where the fish spawn, a handful of pupfish and no more. We place it ourselves, so a world
   always has one (Alex's Mobs' own placement keeps failing in ours).
   - *Feedback loop*: the pool's count rises slowly while the shelf is lit and the water left alone, and falls when it's
     fished, muddied (blocks broken on the shelf) or the water cools (snow or ice near it).
   - *Stewardship*: a player who keeps the count up (no fishing it, the shelf left lit) gets a small reward over time;
     nobody is punished for never finding it.
2. **The Devil's Palm** (Jung's structure, placed by the Hole): a ring of dead desert palms round the sinkhole where
   monsters come thicker at night (his higher spawn rate). The fish and the monsters share a cause: the Palm is a thin
   place in the world, where too much comes through, and the warm water under it is the last of what was there before.
3. **The attuned's link** (instead of a spirit): an attuned player who looks into the pool sees the pupfish glow faintly
   (as they see mana), and a player with a mana spirit hears it say something about the place. No new companion, no
   change to the spirit rules.
4. **Jung's tablet** stays a separate pick: whether waking a spirit should need a quest at all is its own question.

## Open questions

- Build 1-3 (the Hole, the Palm round it, the attuned's glow), or only the Hole?
- What's the stewardship reward? (A pupfish to keep in a tank the player builds, a mark in the Fisher's Journal, Survival
  experience.)
- Does the Devil's Palm raise spawns only at night, and by how much?
- Should the tablet quest be added to how a mana spirit wakes?

Lore placement for the Hole and the Palm is in Lore.md as a `(new)` paragraph, waiting on Andy.
