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
   - it breaks blocks as it goes — it ploughs through the world rather than being stopped by it;
   - an enemy caught on the horns is carried along with it;
   - damage climbs with every object it puts them through — the more it hits on the way, the worse it is for whoever it's carrying;
   - hitting something solid, stone and the like, **runs the opponent through**: the charge stops and that one takes heavy damage to the torso;
   - **the xeno is invulnerable for the whole charge.**
2. **The roar (held).** A shockwave that goes out from it, and the longer the key is held the further and harder it goes.
3. **Big weapons.** It can take up the same weapon the minotaur carries (the great hammer) and large weapons of all sorts — our `TwoHanded` (Better Combat's `two_handed`: claymores, war axes, glaives, spears, scythes and the rest) is the ready-made answer for what counts.
4. **The minotaur's own attacks.** Its blows are the minotaur's: the hammer swing, the slam that throws everyone around it off their feet, and the gore.

## Open questions to settle in the build

- Who steers the charge once it starts, and can it be turned at all.
- What the carried enemy is doing while carried (held on the horns? ridden along in front?) and whether they can be dropped early.
- Whether "objects" means blocks only or creatures too, and whether the damage ramp resets per charge.
- "Runs them through" wants the torso: `Breaks` already has body parts, so a torso wound or break is the honest result, not just a number.
- Which blocks the charge may break — stone and dirt yes, but not obsidian, bedrock or anything a claim protects, and it must not chew through someone's build.
- How long invulnerability lasts (whole charge, or a moment past its end) and what it does and doesn't stop.

