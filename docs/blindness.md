# Blindness (a condition, not a power)

Andy (2026-11-11): "blindness should stay as is, just no longer considered a power, meaning you can go blind from battle,
or malicious intent. resonance gains the new abilities. so the two stay, but resonance is buffed, and you can awaken
resonance while blind with the fork."

## Losing eyes [Blind]

| How | What happens |
|---|---|
| Battle | A blow of 6 damage or more to a **bare head** has an 8% chance to take an eye; a shot (arrow, bolt, bullet) 20%. A helmet, or anything worn on the head except a blindfold, keeps the eyes safe. One eye goes at a time. |
| Someone else | Sneak + shears on a **downed player**, twice within 5 s. The first use warns both of you; the second carves both eyes out. Needs PvP on and the two able to hurt each other (not teammates). |
| Yourself | Sneak + shears on nothing, twice within 5 s (`BlindnessDevotion`). Anyone can, with or without a power. |
| The Unseeing | Their services do it to their own followers. |

- A fresh socket bleeds like a stump until the head is bandaged. The eyes stay gone for that life (they are scars:
  `Scars.Kind.LEFT_EYE`, `RIGHT_EYE`, `CARVED_EYES`).
- Empty sockets show on the face, one or both (`BlindnessSockets`), unless something covers it.
- Tuning: the constants at the top of `Blind`.

## What you see [client BlindnessPowerClient, BlindnessEcholocationClient]

- **One eye gone**: that side of the view is dark, fading out across the middle.
- **Both gone**: the whole view is dark, and **sound paints it**. Every vibration within 32 blocks (steps, blows, doors,
  blocks, landings: what sculk would hear) opens a ring on the dark where it came from, bigger for louder and nearer
  noises; one behind you presses against that edge of the screen. Your own steps ripple too. A Pulse or Shriek that
  catches you shows violet. Hush, a sneaking Resonance holder and Quiet make no rings.
- **A blindfold** on someone without Resonance is the Blindness effect, with the same rings.

## Resonance in the dark

See `docs/resonance.md`: the fork awakens the blind, Resonance holders hear the living through the dark, Sky Sight.

## The Unseeing

- **Zealots Pulse**: with their quarry 2.5 to 7 blocks off and in line of sight, a Zealous Unseeing screams a Pulse at it
  every 5 s (4 damage, thrown back, a raised shield or a meal knocked down).
- **Services are hushed**: within 10 blocks of a service while its eldest lives, no player's power keys answer
  (`BlindnessService.hushed`, a `Powers` silencer). A grey ring runs out over the ground every 3 s to show the edge.
- Enclaves, services, the creed and the blindfold gift are unchanged.

## Files

`power/blindness/`: `Blind` (the condition, the ways to lose eyes, the fork rule, the old power's hand-over),
`BlindnessDevotion` (blindfold, vigil, self-carving, fresh sockets), `BlindnessEcholocation` + `BlindnessEcho` (rings from
vibrations, hooked in `ResonanceGameEventMixin`), `Unseeing`, `BlindnessService`, `BlindnessEnclaves`.
`client/power/blindness/`: `BlindnessPowerClient` (the dark), `BlindnessEcholocationClient` (the rings),
`BlindnessDevoutSight` and its mixins (Resonance's sight in the dark, Sky Sight), `BlindnessSockets`, `UnseeingRenderer`.
Hook: `core/wounds/Wounds` calls `Blind.struck` for a head hit.

## Check first in game

1. Lose an eye (`/damage` to a bare head, or the shears twice while sneaking): half view dark, then both, the rings.
2. Rings sit where the noise is and press to the edge when it's behind you; none from a Hush dome.
3. Blind and powerless, strike a Tuning Fork: Resonance awakens. Blindfold vigil (5 min) then the fork: the same.
4. A Resonance holder blindfolded: thin dark, living things outlined; ascended, sneak + V lifts the view.
5. Hold B: no step sounds for others, sculk sensors stay dark; tap B still casts the dome.
6. A player who had the Blindness power joins: they hold Resonance.
