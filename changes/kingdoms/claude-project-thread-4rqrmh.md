# shadows: see-through, sized, wispy, gone between strikes, unmade by the sun
- **Look**: a Shadow is now drawn as the ghosts are, a person's shape in the ghost's see-through skin, but in a cold near-black with pale eyes (`ShadowRenderer`, skin from `kingdoms/tools/gen_shadow_skin.py`; the old box model is gone from `tools/gen_creatures.py`).
- **Sizes**: each one is born its own size, 0.6x to 1.5x (most near a man's), its hitbox with it. A player's own peeled-off shadow stays their size.
- **Wispy tails**: 2 in 5 have the ghost's wispy tail instead of legs; they hang a little off the ground, bob, and lean into their drift with the tail streaming behind.
- **Gone between strikes**: once it has prey, a Shadow strikes, lingers a moment (about 0.8 s), then thins away to nothing for 1.5-3.5 s. While gone it can't be seen, struck or bumped, and it keeps closing in; it gathers again within 4 blocks of its prey and needs half a second before it can touch. If it never lands a touch it goes after 4 s anyway.
- **Sun**: out under the day's sky (no rain, sky light 13+ at its head, so a thin canopy doesn't save it) it comes apart into smoke at once, takes no damage and leaves nothing; a peeled shadow gives its Might back. Under cover, a bright lamp still withers it as before.
- Built; not played.
