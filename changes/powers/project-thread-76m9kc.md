# sparks off clashing steel, splintering shields
Andy (2026-11-11): "when swords clash or when parries occur there should be particle sparks", and "splintering wood from
shattering shields", in real fights as well as the film.
- **Sparks** (`combat/ClashFx`, `client/combat/ClashFxClient`): our own spark, in place of vanilla's crit stars. A shower
  flies out from the point the steel met, mostly to the sides of the blow, falls, bounces once and cools from white through
  orange to dull red, with a small flash at the meeting point. Every parry throws one (the shared weapon and shield parry,
  a Darktide's blade), bigger for a perfect parry, and a guard that breaks throws one too.
- **Every blocked blow** shows what it hit (`BlockFxMixin`): a few sparks off a blade's guard or a metal shield, chips of
  wood off a wooden one, for players and mobs alike. Arrows chip wooden shields too.
- **Wooden shields break apart** (`combat/ShieldShatter`): a player's shield that runs out of durability bursts into
  tumbling shards that lie on the ground a moment. A mob's wooden shield breaks after 28 points blocked (an axe counts
  double), the mob reels, and it takes up a spare four minutes later once it's out of the fight. Metal shields never break
  this way. What counts as wood: the `jaymospowers:wooden_shields` item tag, else any shield with no metal or stone in its name.
- For the films and other mods: `ClashFx.sparks`, `ClashFx.splinters`, `ClashFx.met`, `ShieldShatter.shatter` on the
  server; `ClashFxClient.sparks` and `ClashFxClient.splinters` with no packet on the client. Sprites from `tools/gen_clash_fx.py`.
- Compiled; not played. The spark and shard sizes, counts and speeds are constants at the top of `ClashFx` and `ClashFxClient`.
