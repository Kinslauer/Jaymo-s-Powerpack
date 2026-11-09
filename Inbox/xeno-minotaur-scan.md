---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [src/main/java/com/jaymospowers/power/xenoform/, src/main/resources/assets/jaymospowers/lang/parts/xenoform.json, docs/xenoform.md]
---

# A xeno of its own, scanned from the minotaur

Scanning a minotaur should teach you a bull-headed alien, not a boar.

## What's wanted

Tiv, in the Tasks thread: "I want a xeno that is scanned from the minotaur."

## Notes

- Today a minotaur scan teaches **Tusk**, the war boar — it's in `XenoDna`'s list along with hoglins, Naturalist boars and Aether stock (`docs/xenoform.md`, "More DNA"; `docs/ScanGaps-Plan.md`). Settled with Tiv: the minotaur comes **off Tusk's list** — a bull-headed giant should not teach a boar, and the new alien is what a minotaur scan gives you.

## The form

A bull-headed brute, heavy through the shoulders, horns, the same build as the thing it was copied from. Look answers to Kingdoms' Minotaur (eanncrod's model, CC BY 4.0): if it reuses that geometry the credit stays, otherwise Prometheus designs the xeno's own silhouette.

## Its kit (Tiv's spec)

1. **The charge (held).** Hold the key and it lowers its head and goes:
   - **it can steer, but barely**: a very narrow arc, under 30° — it goes where it was pointed, and turning is a slow lean, not a run round a corner;
   - it breaks through **dirt, wood and leaves** — not stone, and it doesn't matter who placed the block: no owner is spared, no claim protects a wall of planks;
   - an enemy caught on the horns is carried along with it, and **stunned after every impact** it takes on the way;
   - that enemy is not helpless: they can swing at the xeno while being carried, with whatever they have in hand, and it plays out as a punch or a blow with that weapon;
   - damage climbs with every object it puts them through — the more it hits on the way, the worse it is for whoever it's carrying;
   - **stone stops it dead**: hitting something solid runs the opponent through, the charge ends there, and that one takes heavy damage to the torso;
   - **the xeno is invulnerable for the whole charge** (but the carried enemy can still hit it, per above — that's the one thing that gets through).
2. **The roar (held).** A shockwave that goes out from it, and the longer the key is held the further and harder it goes.
3. **Big weapons.** It can take up the same weapon the minotaur carries (the great hammer) and large weapons of all sorts — our `TwoHanded` (Better Combat's `two_handed`: claymores, war axes, glaives, spears, scythes and the rest) is the ready-made answer for what counts.
4. **The minotaur's own attacks.** Its blows are the minotaur's: the hammer swing, the slam that throws everyone around it off its feet, and the gore.

## Still to settle in the build

- What the carried enemy is doing while carried — held on the horns, or ridden along in front — and whether they can be dropped early.
- The damage ramp counts **blocks only** — creatures hit on the way don't add to it. (Whether it resets per charge still wants a number.)
- The roar **ragdolls and knocks back** whoever it catches; the hold only decides how far and how hard that reaches.
- "Runs them through" wants the torso: `Breaks` already has body parts, so a torso wound or break is the honest result, not just a number.
- How the carried enemy's blows land while they're pinned: normal damage through the invulnerability, or their own reduced number.
- How the roar's growth is measured — reach, force, or both — and what it costs to hold that long.
- How long invulnerability lasts (whole charge, or a moment past its end).

