# V works again: the voice chat menu moves off the power keys

- **Echolocation (and every power's V) works again.** Simple Voice Chat opens its menu on V. That screen opened before the power key was read, so pressing V never reached your power: Resonance's Echolocation never fired (no outline, no ripple, no chime) and the voice chat menu popped up instead. The same went for every power's V, and for G (Voice Chat's group screen, when groups are on).
- Once per install, as the pack already does for other clashing keys (`KeyConflicts`, now v8): the **voice chat menu moves to F7**, and its **group** (G) and **hide icons** (H) keys are unbound; both are buttons inside the voice chat menu. Only keys still on those defaults are moved; your own rebinds are kept.
- Resonance's own code is unchanged: read against the 26.3 sources, the ping, its packet, the outline mixin (Sodium's culling lets outlined creatures through) and the heartbeat ring hold up. The ring still only shows while a creature is within 12 blocks.
- Built; not played. Simple Voice Chat's defaults are from its README and public source, not checked in the 26.3 jar.
