# Ape/Giant: sprint hit trees fly, scaling screenshake, scaled dust particles

- **Great Apes and Giants sprinting into trees** tear the tree up and throw it outward, sending logs flying. Without Kingdoms the trunk breaks and drops scatter; with Kingdoms the whole tree topples away from the impact.
- **Screen shake** now scales with the entity's size. A Great Ape's steps, slams and stamps shake the screen proportionally to its height. Giant entities (Aether Giants, player giants) produce much stronger shake.
- **Dust particles on steps and landings** scale with entity size. Giants kick up larger, longer-lasting dust clouds; Great Apes produce dust scaled to their ape size.
- **Ape stomp shake** (ZFighter lineage) now scales with the player's current ape size.
- Classes: `core/GiantTrample` (sprint hit, scaled push), `power/zfighter/ZFighterLineage` (scaled ape shake), Kingdoms `remnants/AetherGiant` (scaled footfall/slam/stamp particles and shake).
- Built; compiles. Not played: sprint-hit tree throw, scaled shake feel at different sizes, dust cloud visibility at distance.