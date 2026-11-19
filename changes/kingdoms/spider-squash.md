# spiders squash underfoot, die to one blow, and NPCs can hit them
- **Squashed underfoot**: a small spider (no taller than half a block: the usual half-size spider, cave spiders, spiny spiders, spiderlings, young ones) dies when anyone walks over it: a player, one of the folk, or a beast of some size (a cow, a horse). Monsters don't squash their own.
- **Jump and land**: a bigger spider (a netcaster, a mother, a tarantula, a spider at the game's own size) is squashed by a jump that lands on it; small ones too. The brood queen is fought as before.
- **One blow**: any blow someone lands on either size kills it, a bare punch included, from a player or anyone else.
- Squashed spiders die crushed, so Kitchen bursts them into a few loose legs and no body.
- **NPCs hit small spiders**: a barbarian's (or any fighter's) slow wind-up let a small spider skitter out of reach before the blow fell. Against one the blow now comes down at once and still finds it a block past the reach.
- wild/SpiderSquash (tiers, walk-over tick, landing, one blow), mixin/wild/SpiderBlowMixin (hurtServer) and SpiderLandingMixin (causeFallDamage), MobCombat#small/slack.
- Built; not played.
