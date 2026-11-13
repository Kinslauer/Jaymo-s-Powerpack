# BossRise Plan: boss fights, duellists and the glass gauntlet

Andy (2026-11-12) linked Bosses'Rise, Epic BossesRise, Animated Universe (Void Powers) and Obsidian Manipulation
(Obsidian Arsenal), and ticked 39 of the 40 rows of the pick list (https://claude.ai/artifact/QWcw1NTkpWQ2E2aSNTGMza,
db `picks/andy`). The glass-plate armour is the one row left out. All four mods are All Rights Reserved and Forge
1.20.1 only: ideas only, our own code, names and vanilla-style art, clean-room (behaviour spec first).

**Andy's notes on the picks.**
- Guards with real movesets: "PLEASE, this is the MOST IMPORTANT part of the modpack, we NEED good combat". Patch 1.
- Fight meter: an invisible meter. Every "near death" unlock needs you to have been brought to the brink or been in a
  serious fight, the meter past 80%. Before that none of those chances can come up.
- Launcher, juggle and slam: only flying characters and powers can juggle, plus Web, Darktide and Gravity.
- Lair garrisons: neither new skeleton is needed.
- Lairs: the Skeleton Lord is in the Stormborn dungeon (he has his lair).
- Sand worm: an old Stoneborn legend. They speak of it, and a Stoneborn who is with you when it shows faints.
- The tool gauntlet: only Arsenal can pull it out, by chance, and it is favourited. No other way to get it.
- Volcanic glass spells: a new spell type.

**What it builds on.** `MobCombat` (humanoid mobs already swing their weapon's Better Combat combo, sidestep and wait
their turn), `MobParry`/`MobShields`, `Comrades` and `Callouts` (leanings, sides, band orders), `Temperaments`,
`BossBars`, `BossPosture`, `BossIntros`/`BossIntroClient`, the champions' `MoveKit`, `AttackTells`, Powers' `Dodge`,
`LockOn`, `BeamClash`, `ZFighterImpactFramesClient`, `Trophies`, `ClawForms` and the Fang Gauntlets, `ArsenalRelics`,
`Arcana`/`SorceryPicks`, `MagicFx`/`Fx`.

One PR per patch, each with its change notes. Patches 4 to 7 each add a creature and wait on its Lore.md paragraph.

## Patch 1: duellists (ebr_movesets, ebr_roles)

The pick list said guards lack movesets. That was wrong: `MobCombat` has given every armed humanoid its weapon's combo
since 0.7.30. What a fight still lacks is everything round the swing, so that is this patch (`Duellists`):

- **Footwork.** A fighter with a foe in reach no longer stands and trades. Between swings it circles to a side, and
  steps back out of reach after its own blow when its temperament is careful. Brutes and the reckless keep walking in.
- **Closing blows.** From 4 to 7 blocks a fighter may run in and open with its weapon's lunge or leaping blow (a spear
  thrusts, a two-hander comes down overhead), with the tell every swing has.
- **Punishing.** A fighter reads you: it answers at once when your swing misses, when you eat or drink in reach, and
  when you turn your back.
- **Flinch.** A blow that lands on a fighter mid wind-up may break the swing (light weapons no, heavy ones yes), so
  striking first matters; veterans shrug light blows off.
- **Roles show (ebr_roles).** In a band the shield-bearer walks in guard up and holds your front, bowmen and
  crossbowmen keep their distance behind him, and the leader hangs back until the band thins. When the leader falls the
  band's nerve is tested by temperament: the timid break, the rest fight on.

## Patch 2: the shape of a boss fight (br_enrage_film, br_death_anim, br_armoured, br_boss_audit, au_impact_all)

- **Half-health turn.** `BossIntros` gains a second beat fired by health: a two-second film of the boss changing, then
  it comes back quicker with one new move. Once per fight, never while a player is mid-air or downed.
- **Death scenes.** Each BossBars boss dies its own way with a short closing shot (the dragons already do).
- **Armoured state.** Bosses with hide or plate (Giants, Ogre King, the new snow giant) take a fraction until their
  posture breaks, full while they kneel.
- **Three moves and a turn.** An audit first (listed in the PR), then every boss short of three telegraphed moves gets
  them through `MoveKit`.
- **Impact frames** for every ultimate, boss slam and riposte, under the existing client setting.

## Patch 3: entrances, lairs and the dragon's tower (br_staged_intro, br_dragon_tower, br_lairs, br_garrison)

- Per-boss staged entrances, Ignivorus first: the fly-in, the landing on the tower, the roar (Andy's reference gif).
- A Nether roost tower for Ignivorus, climbed through its garrison.
- Small lairs of our own for the Gorgon, the Ogre King and the Grizzly. The Skeleton Lord keeps the Stormborn Tomb.
- Garrisons from folk we have (draugr, quivered skeletons, orcs, illagers). No new skeleton kinds.

## Patch 4: the fortress knight (br_knight, br_knight_sword)

A champion of the demons' country holding a Nether fortress, the first boss on the humanoid move library and Weapon
Forms: he can be parried and posture-broken. His sword is an Arsenal relic with its own stance, found only on him.

## Patch 5: the snow giant (br_yeti, br_gauntlet)

Cold-plains great beast on the Grizzly's rig: slam, leap, chilling blows, ice thrown after the turn, a barrage. Left
alone, it leaves you alone. Its gauntlet is a claws-family relic with three of its moves through `ClawForms`.

## Patch 6: the sand worm (br_sandworm)

Under desert ruins; read by the sand moving. An old Stoneborn legend: Stoneborn talk of it obliquely, and a Stoneborn
villager or companion with you when it surfaces faints (Powers' downed state, gets up after).

## Patch 7: the thing under the wreck (br_kraken, br_kraken_parts)

The Deep Mother's own, under some treasure wrecks and in the far grounds. Takes hold of a ship. Teeth are a Tinkers
head material for harpoons and tridents; the tentacle charm (bauble charm slot) pulls you back from one drowning.

## Patch 8: trophies and dragon parts (br_trophy_set, br_dragon_parts)

One trophy per boss kind for the trophy stand; dragon bone as a Tinkers material (a trait per dragon kind), the skull
on the stand, a hide shield that shrugs off fire.

## Patch 9: fighting without a power (au_mortal_kit, au_aerial, au_launcher, au_chain_axe, au_fight_meter, au_pvp_cap)

- Four bare-handed moves for players with no power: power strike, sweep kick (the bolas trip), second wind, battle cry.
- One falling attack per weapon kind.
- **Launcher, juggle and slam only for those who fly**: flying forms and powers, and Web, Darktide and Gravity.
- The axe on a chain, forged in the Tool Forge.
- **The fight meter (`Brink`)**: hidden, no HUD. Damage dealt and taken in a real fight fills it; it drains out of
  combat. Every near-death unlock (the mana spirit's waking, False Super, the symbiote's and heritage ones: each
  listed in the PR) can only come up while it stands past 80%.
- A server setting capping ability damage between players at a share of health, off by default.

## Patch 10: the glass gauntlet (om_gauntlet_tool, om_gauntlet_combo, om_guard_stance, om_stretch_dodge)

No recipe, no loot: Arsenal alone may pull it, by chance, and it comes favourited. Work mode (dig, chop, shovel,
till); fight mode is a seven-blow Weapon Form for the claws family. The clawed guard pose. The stretch dodge look for
Rubber-folk and Verdants.

## Patch 11: Sorcerer spells (om_glass_school, om_fire_rings, om_fire_pillars, om_black_flame, om_fire_beam, om_storm_cloak)

Volcanic glass as a new spell type (four spells) that any Sorcerer can learn, whatever their element (Andy: "anyone can learn"); it is not a tenth element. Fire: ring ward, orbiting pillars, a held beam that registers with
`BeamClash`. The flame that won't go out, a forbidden art that Restoration puts out. The lightning cloak.

## Patch 12: screen and world (au_lensing, au_beam_scars, au_phoenix)

Light bending round Gravity's black hole when Iris runs; beams scar soft ground (mobGriefing, spread over ticks); a
fire dive for Ember or Pyre in place of a weak move.

## Open questions

1. Patch 1's flinch: should a player's own wind-up be broken the same way by a fighter's blow?
2. The glass gauntlet with no Arsenal user on the server: is it then simply not in that world?
3. Answered (Andy, 2026-11-12): any Sorcerer can learn the glass spells.
4. Which near-death unlocks the Brink gates is listed in patch 9's PR for a yes or no each.
5. Answered (Andy, 2026-11-12): the lore placements for the knight, snow giant, sand worm, kraken and the glass gauntlet are approved.
