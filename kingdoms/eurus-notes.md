# Eurus notes (delete once folded in)

Built: EurusDragon (160 hp, armour 6, toughness 1; cruise 0.9 / swoop 1.65, x1.25 in rain; favourite cooked rabbit;
STORMBORN bonds x2; riderSpeedFactor 1.3 Wind+rain, 1.15 Wind clear), DragonStorm (chain lightning: 20 block range,
7 damage, up to 3 arcs at 0.75x, sparks and thunder; real bolt 12% on the first hit only when griefing and thundering),
EurusDragonModel (lean, 4-segment neck, whip tail with forked fin, long narrow wings), gen_eurus.py (hides, marks,
egg icon, egg item json, loot table). villager.png untouched.

Leaving: a wild, untamed, unridden, non-baby Eurus with no foe for 1200 ticks (own `idle` counter, reset by `fight`)
flies off high with sparks and is discarded (thunder crack). A spawn-egg Eurus does the same if nobody is near.
Its `customServerAiStep` skips the base wild AI while leaving. `home` is nulled while leaving.

Heritage: Heritage.Kind.STORMBORN exists, used directly.

Lang keys: none beyond those already registered.

MODLOG: "Eurus, the east wind dragon: fastest and frailest; chaining storm bolt (DragonStorm); wild ones fly off when idle;
Wind users ride it faster."

UNVERIFIED APIs (no repo example): BossEvent.BossBarColor.PURPLE; Entity.discard(); LightningBolt spawned non-visual
(Relics uses setVisualOnly(true); same create/snapTo/addFreshEntity); level.damageSources().lightningBolt() via
dragon.damageSources() (Heritage uses level.damageSources()); ServerLevel.canSeeSky(BlockPos) on a ServerLevel
(Heritage uses it on a level); SoundEvents.LIGHTNING_BOLT_THUNDER/IMPACT (both used elsewhere). Model pose not seen
in-game (no renderer here): check leg/wing fold and crash/sit poses.
