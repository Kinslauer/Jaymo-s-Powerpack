# Devil's Hole Pupfish — write-up

## What it is now

The Devil's Hole Pupfish is an Alex's Mobs entity (`alexsmobs:devils_hole_pupfish`). It is harmless and does not affect any other mod feature. It cannot spawn naturally: world generation fails to find a valid spawn chunk for it because the desert terrain and cave generation are altered, so "this world will have no pupfish." The only way to get one is to summon it with `/summon alexsmobs:devils_hole_pupfish`.

Kitchen already knows the pupfish as a fish corpse (MODLOG 0.4.29): click a dead one with anything in hand and it drops its loot and vanishes with a splash.

## Devil's Palm

The Devil's Palm is a biome structure concept from a forum post by "Jung": a structure that increases monster spawn rates. It is not in the code yet. The idea is a place where the world is worn thin and more monsters come through.

## Mana Spirit

A mana spirit is a companion a player earns after a brush with death — dropped to a fifth of their health or put down — followed by up to five days of spirit sickness, then one spirit for life. Each spirit has a personality and combat abilities, and they speak like old wizards looking after a child (docs/Spirits-Plan.md). An NPC quest, the ancient tablet of inscribed runes, unlocks a spirit once the five-day sickness timer runs down.

## The connection

The pupfish are what remains of the Devil's Palm's waters. The Palm reshaped the desert and its caves so completely that the pupfish's spawning grounds are gone — which is exactly why they can't spawn naturally. The Palm is a place where too much spawns (monsters); the pupfish is a creature that can't spawn at all. Same cause, opposite result.

An attuned player whose spirit has woken can read the same ancient tablet that calls a mana spirit and call a pupfish instead: a small, harmless companion that follows and remembers the Palm. It does not fight. It is a sign that the player has been somewhere the world has forgotten.

## Open questions

- Is the pupfish a minor mana spirit, or something outside the "one spirit for life" rule?
- Does calling it cost mana, use the tablet, or stay a summon command?
- Where does the Devil's Palm generate, and what does it look like?

## Not decided

No code yet. This is a write-up to decide whether it becomes a feature spec.
