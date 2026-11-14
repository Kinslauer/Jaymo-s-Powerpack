# Elemental Corruption (Jung)

Jung (2026-11-13): when a corrupted Elemental power user (Wind, Smoke, Gravity or Cryo) kills a human who isn't a
barbarian, the gods of their power warn them twice, then take it back. Core: `elemental/ElementalCorruption`; each
element's part in its own power's package (`WindWrath`, `SmokeWrath`, `GravityWrath`, `CryoWrath`), registered from
its power's `init`. Lore: *The gods of the elements* in Lore.md (for Andy to approve).

Jung's answers to the open points: any corrupted elemental user counts, whatever mode they're in; "humans" are
villagers only (vanilla's, MCA's, a Kingdoms settlement's folk: `Carry.isVillager`), never a camp's
(`KingdomsCompat.barbarian`: a barbarian raider, chief or builder, or any camp crew that is an MCA villager: pirates,
goblins, orcs); and Cryo got a simple corruption of its own (below), since it had none.

## The killings (`ElementalCorruption.died`)
A killing counts when the killer's power is one of the four, it is corrupted (`Fate.isCorrupted`), they aren't in the
wrath or reclaimed, and the victim is an innocent. The count is saved (`elemental_killed`, kept through death).
1. "You are angering things you cannot defend yourself from.", or one of four other wordings (Jung: "add variation";
   `WARNING_WORDINGS` 5 each, picked at random, in `lang/parts/elemental_corruption.json`).
2. "We will not stand for injustice in our name.", or one of four others.
3. "You have enraged the Gods of your power and they now reclaim it": the reckoning, then the wrath. The count resets.

## The reckoning (once, `Wrath.erupt`)
- **Wind**: the overworld turns to a thunderstorm (`STORM_TICKS` 6000) and `BOLTS` 8 real bolts fall 4-10 blocks round
  the killer.
- **Smoke**: `FOG_PUFFS` 48 puffs of thin smoke (`FOG` 0.3, under the field's sight-blocking 0.5), nobody's, out to 16
  blocks: a fog that dims the view without blinding it.
- **Gravity**: every creature (bosses aside) and loose item or falling block within 12 blocks rises about 10 blocks
  (Levitation IV for 2 s; items and blocks a 1.1 kick up), and `RING_BLASTS` 6 blasts (strength 3, nobody's, obeying
  mobGriefing) go off in a ring 10 blocks out.
- **Cryo**: every block within 12 blocks becomes ice (but air, blocks that hold something, unbreakable ones, and the two
  the killer stands in), and `SPIKES` 6 spikes of packed ice 7 tall, on a short foot, burst up 4-10 blocks out.

## The wrath (`WRATH_TICKS` 6000, five minutes; `elemental_wrath`, saved)
- **Double power**: every blow the holder deals (arrows and other things they own included) is `POWER` x2 (a mixin on
  `LivingEntity.hurtServer`, `ElementalWrathDamageMixin`), and `RECOIL` 25% of what it dealt comes back on them as magic
  damage (`AFTER_DAMAGE`).
- **It burns them**: `BURN` 1 (half a heart) every `BURN_EVERY` 100 ticks; the corrupted element pours off them
  (Wind: storm sparks; Smoke: smog miasma; Gravity: violet motes; Cryo: frost motes).
- **Everything runs**: every 20 ticks each mob within 16 blocks drops them as a target and paths away (16 blocks off,
  speed 1.35), but bosses (`Mortality.boss`, Kingdoms' boss bars) and orcs (`KingdomsCompat.orc`) stand.
- **The element works on the world**, on other players too (Jung), never the holder, a creative or a spectating player:
  - Wind: the storm is kept up; every `STRIKE_EVERY` 140 ticks (7 s) lightning (the look of it) strikes a random living
    creature (other players included) within 16 blocks for `STRIKE` 2 (a heart).
  - Smoke: every `SMOTHER_EVERY` 60 ticks (3 s; Jung named no pace) every creature and other player within 8 blocks chokes for 1 (half a heart,
    Smoke's own choke damage); the fog is topped up (6 puffs every 2 s).
  - Gravity: every 10 ticks a loose block (sand, red sand, gravel, dirt, coarse dirt, grass, cobblestone, mossy
    cobblestone) on the surface within 10 blocks cracks (particles and sound), drops loose as a falling block, or rises
    about 4-5 blocks and slams back down; a slammed one knocks out the block it lands on and the open loose blocks
    round it (a small crater). Every 5 ticks each creature and other player within 14 blocks (bosses aside) is pulled toward the holder
    (0.08 b/t).
  - Cryo: every 10 ticks each passive creature (animals, bats, other non-hostile ageable mobs), villager and other player within 8
    blocks is frozen through (powder snow's `ticksFrozen`, kept 60 past full, so the game's own freezing damage hurts
    them) and held still (Slowness X, 1.5 s, refreshed).

## The aftermath (`ElementalCorruption.end`)
- What the wrath raised goes: Wind's storm clears; Smoke's fog thins away; Gravity's lifted blocks are let be; Cryo's
  ice stays and melts as ice does.
- The power is reclaimed for `RECLAIM_TICKS` 72000 (three in-game days; `elemental_reclaimed`, saved): its keys do
  nothing and its passive tick stops (a Powers silencer and pauser, as a Dampening Draught does).
- Purified meanwhile (the power's own cure, or **a Purifying Draught drunk while the power is gone**: penance,
  `ElementalCorruption.penance`), the corruption is cleansed for good and the power comes back **at once** (Jung).
  Not purified, it comes back after the three days, still corrupted. A line in chat says which.

## Cryokinesis' corruption (`CryoCorruption`)
- **Meter**: each creature that dies frozen (`ticksFrozen` > 0) by a Cryo user's hand adds `MOB` 4, a villager or
  player `PERSON` 15 (`Fate.addCorruption`; 100 corrupts).
- **Corrupted**: a chat line ("The cold has found your heart, and it is hungry."), frost motes off them every half
  second, and the gods of the elements watching (above). Nothing else changes in the kit.
- **The cure**: a Purifying Draught drunk within 3 blocks of a lit campfire (`CryoCorruption.thaw`) purifies it for good.

## Testing
`/power wrath <targets> start|end|lift|clear`: the reckoning and wrath now, the wrath over now (power taken), the power
back now, everything forgotten (count too). `/power corrupt @s` corrupts the power.
1. `/power set @s wind`, `/power corrupt @s`. Kill a villager: chat line 1 (dark red). Kill a second: line 2. Kill a
   Kingdoms barbarian, a pirate or a zombie: nothing. Kill a third villager: line 3, a thunderstorm and 8 bolts round
   you.
2. In the wrath: punch a zombie: about double damage, and you lose a quarter of what it took. Every 5 s you take half
   a heart. Sparks pour off you. Mobs round you run (a Kingdoms orc or a boss doesn't). Every 7 s lightning hits a mob
   near you for a heart.
3. `/power wrath @s end`: the storm clears, chat says your power is gone; R G V B, Z and C do nothing; Wind's passive
   tick stops. Drink a Purifying Draught: purified, and at once "Your power stirs in you again, clean."; the keys
   work. Without the draught, `/power wrath @s lift`: "...It is still not clean."
4. Smoke (`/power set @s smoke`, corrupt, `/power wrath @s start`): a thin fog round you; mobs within 8 choke for half a
   heart every 3 s; smog pours off you. End it: the fog thins away.
5. Gravity: on start, mobs and dropped items round you float up about 10 blocks and six blasts go off 10 blocks out.
   Stand on sand or dirt: blocks crack, hop, or rise and slam into little craters; mobs creep toward you.
6. Cryo: on start, everything within 12 blocks turns to ice (a chest doesn't) and six packed-ice spikes 7 tall stand
   round you. Cows and villagers near you freeze stiff and take freezing damage; a zombie doesn't.
7. Cryo's corruption: `/power set @s cryo`, freeze a cow with Frost Breath and hit it to death while frozen: the
   corruption meter goes up 4 (a villager 15). `/power corrupt @s 90`, then one more frozen kill of a villager:
   corrupted, the chat line, frost off you. Drink a Purifying Draught away from a fire: nothing; beside a lit
   campfire: purified.
8. Log out mid-wrath and back in: the wrath goes on where it was (it's counted in game time); log out reclaimed: still
   reclaimed.
