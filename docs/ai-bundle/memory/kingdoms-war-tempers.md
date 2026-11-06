---
name: kingdoms-war-tempers
description: Kingdoms 0.5.12 (PR #29 merged) and PR #42 (Kingdoms 0.5.28, merged 2026-10-12): javelin/rock tiers, treasuries, war-party join + spoils carts: javelins, destructive attackers, five ruler tempers, villagers flee monsters
metadata:
  type: project
---
2026-10-12 Andy asked: wars were won by archers (war parties were all melee guards), so guards need pathing or thrown
weapons; attackers should burn what blocks them and be more destructive; kings get personalities (tyrant, noble, +3);
then MCA villagers should flee and hide from monsters like from fire.
PR #29 merged 2026-10-12 on Andy's "merge the pr" (Kingdoms 0.5.12, pack 0.8.39):
`Javelins` (tridents, no pickup), `Assault` (Strength, break doors/fences, burn wooden walls, torch enemy buildings),
`Tempers` (tyrant/noble/warlord/paranoid/reveler, drawn from the ruler's UUID, so nothing is saved), `Frights`.
Seen in game: javelins, fires, broken trapdoors, paranoid shove, vanilla+MCA villagers fleeing an enderman.
Dev run now includes MCA: a fresh client gets MCA's character screens (click Done, then "Somewhere", then Next x4).
See [[kingdoms-living-economy]], [[cloud-playtest-setup]].
2026-10-12 follow-up: Andy found tridents too strong for ordinary guards. PR #42 (Kingdoms 0.5.28 after renumbering): guards and barbarians
throw Balkon's WeaponMod javelins (WeaponModJavelins, ~5 dmg); tridents only for guards of the 2 richest kingdoms
with 300+ emeralds (Javelins.elite). Andy then asked: poor kingdoms (<50 emeralds) and barbarians throw rocks
(a cobblestone snowball re-dealt as 3 damage).

Dev-server gotcha: with no player online, entities stop ticking after a few seconds even in forceloaded chunks, so
AI tests need the client (restart.sh) or run in the first seconds after start.
Andy also asked (same PR #42): kingdoms start with 10-700 emeralds and treasuries move with trade and war ->
`Treasuries` (trade toll via VillagerTradeMixin, caravan dues, war-party wages, plunder per kill).
Then: `/kingdom war join` (march with your side's party) and every war party needs a horse-drawn spoils cart
(Wagon.WAR_CART); parties sack seats (30% treasury + 9 stacks) and haul home; players get 25% + rep. Full loop
verified in game 2026-10-12. Testing tip: Norreach's small parties lose; rig with resistance + clearing defenders,
and found a fresh enemy with `/kingdom found` near Norreach. Never `pkill -f`/`pgrep -f` a pattern that appears in
your own command line (it kills your shell); kill by PID from `ps`.
