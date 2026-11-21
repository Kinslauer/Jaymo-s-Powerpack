# Hackers 'n Slashers picks: plan

Andy (2026-11-19) linked Hackers 'n Slashers (DnDats, All Rights Reserved, NeoForge 1.21.1: ideas only, our own code)
and asked for "similar features, especially the sparks upon clashing blades or otherwise hitting metal". The sparks on
metal are PR #1809 (`combat/MetalHits`). He then ticked 14 rows on the pick list
(https://claude.ai/artifact/BFgKwnWSPGmCR8xp2xkrLx, db `picks/andy`). All but "replace Better Combat".

His notes:
- Backstab: "dont make it break commandments for those who have them". Reading taken: the Crowned Sun forbids
  `sneak_attack` (Commandments), so a player sworn to a patron whose law holds `sneak_attack` gets no backstab bonus
  from the new for-everyone rule, and nothing new adds a commandment break. The Sneak Attack perk is unchanged.
- Dulling: "blades should be kept sharp, and you should see the blacksmith sharpening its guards blades".
- Riposte: the counter-attack after a parry (yes).

Four patches, one track each, two at a time (CLAUDE.md). Tuning numbers sit at the top of each class.

## Patch 1: crits (Powers, `combat/`)
- **hs_riposte_kind** Ripostes by weapon kind, in `Guard#riposte`, by `Armed.Kind`: heavy (TWO_HAND, POLE heavy) staggers
  and slows; one-handed sword cuts deeper (a bleed through Powers' Bleeding); knife/dagger lands a quick extra hit;
  polearm shoves back.
- **hs_crit_cue** `combat/CritCue`: one short sharp sound (synthesised, `tools/gen_blade_sounds.py`) and a small white
  flash burst at the hit point for every special hit: riposte, perfect parry, and public `CritCue.cue(...)` for
  Kingdoms (sneak attack, assassinate, headshot) through `PowersCompat` reflection.
- **hs_burst** Crit camera punch: the attacker's camera gets a short shake/punch on a cue (our screen shake), with a
  client config toggle.
- **hs_heavy_crit** A crit with a heavy weapon (great weapon, warhammer, mace) throws a typed ring (MagicFx ring, never
  recoloured dust) and knocks back those close by.
- **hs_attrs** Attributes `jaymospowers:riposte_power`, `sneak_attack_power`, `headshot_power`, `parry_recharge`
  (multipliers, base 1). Guard reads riposte power and parry recharge; Kingdoms reads the other two by registry
  lookup. Raised by existing gear only (no new items): e.g. the hidden blade, ranger gear, a fitting bauble or two.

## Patch 2: stealth and posture (Kingdoms)
- **hs_backstab** A melee hit on a mob facing away and not targeting you: a small bonus for everyone (Senses), under the
  Sneak Attack perk's. None for a player sworn to a patron whose law forbids `sneak_attack`. Calls the crit cue.
- **hs_cover** Senses sight sum: tall grass/leaves/bushes hide everyone a little (the Stalker Ranger keeps far more),
  rain hides a little, sprinting gives you away, the mob's last attacker is easier to see.
- **hs_posture** Posture for ordinary fighters (MCA guards/raiders/knights, armed monsters): BossPosture's idea scaled
  down. Blocked blows and parries wear it; broken, they reel open for a moment so a hit always lands; shown as a
  sagging stance, no bar.

## Patch 3: edges and sparks (Powers `combat/MetalHits` + an edge, Kingdoms smiths)
- **hs_stone** Steel on stone: a weapon or pick striking stone sometimes throws a few dull sparks (ore/deepslate/flint
  more often).
- **hs_dull** + Andy's note: blades keep an edge. Hits on metal and stone dull it most, flesh a little; a dull blade
  does less damage. Sharpen at a grindstone (or a whetstone), with sparks. Kingdoms: town weaponsmiths/armourers are
  seen at a grindstone sharpening blades, and guards get their edges kept by them. Lore placement (Anvil-Father) as a
  `(new)` paragraph for Andy.
- **hs_kindle** Sparks that catch: a big shower landing on dry grass, hay, cobweb or a gunpowder trail can light it
  (never in rain).
- **hs_armour_ring** Plate rings, mail rasps, leather thuds: two or three more synthesised hit sounds, picked by the
  plate_armor/mail_armor tags MetalHits reads.

## Patch 4: shove and procs (Powers `combat/`)
- **hs_bash** A shove for everyone: ShieldBang's bash opened to every weapon (pommel) and a free off hand (shoulder);
  lunge half a step, knock back, break the guard briefly; costs a dodge feather, own cooldown.
- **hs_procs** Weapon on-hit effects by `Armed.Kind` (Dungeons-style small chances): axe cleaves into the next foe,
  mace dazes, spear pierces, sickle swipes twice. Beside `tinkering/TraitEffects` so traits and kinds don't stack
  into nonsense.

Checks: each patch compiles (`./gradlew compileClientJava`, and in `kingdoms/` for Kingdoms) and passes
`tools/pr_checks.py`, with a change note per mod. Not played; Andy tests by merging.
