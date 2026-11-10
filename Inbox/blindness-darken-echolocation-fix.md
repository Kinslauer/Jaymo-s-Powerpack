---
status: open
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by:
pr:
suggested_owns: [src/main/java/com/jaymospowers/power/resonance/, src/client/java/com/jaymospowers/client/power/resonance/, src/main/java/com/jaymospowers/power/smoke/SmokeSmother.java, src/client/java/com/jaymospowers/client/]
---

# Blinding isn't dark enough, and echolocation doesn't work

Two separate complaints on Powers' sight powers.

## What's wanted

Tiv, in the Tasks thread: "the blinding in game isnt dark enough and the echolocation doesnt work."

## Blindness

- Powers applies vanilla `MobEffects.BLINDNESS` (see `SmokeSmother`, `BandFlash`, `ZFighterTechniques` etc.). Vanilla blindness darkens the whole screen evenly — a flat vignette, not actually dark. Tiv wants it darker.
- There's a worktree already (`E:/wt/blindness-echolocation`, branch `claude/blindness-echolocation`) with this topic's earlier work; pull it before starting rather than branching fresh.
- Client-side overlay vs server-side potion effect: a darker blindness means a client overlay (full-screen vignette), not just the potion effect. Decide whether this replaces vanilla's darkness or stacks on it.

## Echolocation

- `ResonanceEcho` (V, Echolocation): every creature within 32 blocks is outlined through walls for 4 s, on the user's screen only. There's also a passive Weak Echolocation (a faint ring round the crosshair; creatures beat on it).
- "Doesn't work" — no outline, no ring, no ripple, no sound: the report doesn't say which. The work needs to find what broke: the ping, the outline mixin (`ResonanceGlowMixin`), the client listener (`ResonanceSenseClient`), or the server half that sends it.
- `docs/resonance.md` has the spec: weak echolocation always on; V outlines creatures for 4 s, 8 s cooldown; outline shows only for the user.

## Notes

- These are two separate faults on the same theme (seeing, or not seeing). Don't bundle the fix into one PR unless they share code — the blindness fix is client rendering, echolocation is resonance networking + rendering.
- A full-screen blindness overlay is a new thing in Powers; check `docs/AI-HANDBOOK.md` for how overlays are attached (`HudElementRegistry.attachElementAfter`, `VanillaHudElements.MISC_OVERLAYS`) and reuse that pattern.
- Say plainly in the PR what was tested: whether echolocation's outline and ring now appear, whether the blast of darkness is darker, and what was not bootable in game.
