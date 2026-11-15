# Fantasy Armor plan

Andy linked Fantasy Armor (Medieval Series, kenddie) on 2026-11-15 and picked all 20 rows of the pick list
(https://claude.ai/artifact/QFhWbwbJfsmULu8nMNWAeX). Model sheet of its 30 sets for review:
`/mnt/project-files/art-overhaul/fantasy-armor/fantasy-armor-models.png`.

**Licence.** DSMSLv2: All Rights Reserved in effect. Nothing of its code, models or textures is copied. It may sit in the
pack as a CurseForge/Modrinth reference (never a bundled jar), and resource packs and datapacks for it are allowed.
Fantasy Armor 1.3 has a Fabric 26.3 build; it needs GeckoLib and Cloth Config (both already in packMods).

**Andy's notes.** Repaint: "let me see the models first." Upgrade: "diamond gear should be removed, and replaced with
something like this, netherite should remain as plating but for steel armor." Armour only or tools too is on a decision
card; Andy chose armour, tools and weapons (2026-11-15).

**Lore.** One grouped `(new)` entry, "What the armourers make" and the eight paragraphs after it, sits before "Rules for
writing against this" in Lore.md, for Andy's approval. Nothing is built before he approves it.

**Overlaps found while planning.** Armory already has a Ronin lamellar, a Gladiator set and steel plate, and the Nether
war forts' warlords wear the Primal Visage. So blossom lamellar and the pit harness are upgrades of Ronin and Gladiator,
not new sets, and the ashen plate is the demon captains' (Nether depths), not the war-fort warlords'.

## Patches

Two tracks at a time, one per system. Every patch: change note per mod, `./gradlew build` (cloud: staged-jar compile
check, see memory cloud-compile-check), an independent verifier agent, one PR, Andy merges.

### 1. Diamond out, steel in (Powers + Kingdoms)
- Diamond helmet/chestplate/leggings/boots: grid recipes removed (recipe overrides), hidden from JEI's craftable view;
  pieces already in worlds keep working.
- Netherite upgrade: smithing table takes Armory's steel plate (not diamond) with the upgrade template and an ingot.
- Royal tiers: rich queens wear trimmed steel plate where they wore diamond (`Regalia.plate`, `RoyalDress.conform`);
  `McaEquipmentMixin` swaps any diamond armour for steel plate. Loot tables that give diamond armour give steel plate.
- Found while starting: diamond tools, armour and weapons are already uncraftable (Powers 0.8.124), and netherite gear
  is loot-only because its smithing needs diamond gear. So this patch is: netherite armour by smithing over steel plate,
  steel tools and weapons (Tinkers' steel is already netherite-tier) taking Tinkering's Netherite modifier as the
  plating, and diamond gear taken out of NPC kits, relics and rewards (MonsterGear, Regalia, Relics, QuestBoard) in
  favour of steel.
- Diamond tools and swords go too (Andy's choice): grid recipes removed; steel tools and blades forged through Tinkers
  fill the tier, and netherite tools are made from steel ones (smithing table or Tool Forge) instead of diamond ones.

### 2. Set framework (Kingdoms, Armory)
- **Temper-stone** (`upgrade-catalyst`): a diamond, amethyst and glow ink at the Tool Forge; temper-stone + a finished
  steel plate or netherite piece + one token item = that set's piece. One table of set -> token.
- **Per-piece bonuses** (`piece-spread`): each set's pieces carry their own small modifiers (speed on boots, attack speed
  on the chest, health on the helm, luck on hoods), constants at the top of the set class.
- **Whole-set sign** (`set-motes`): faint MagicFx motes in the set's colour while the whole set is worn.
- **Set capes** (`set-capes`): `ClothCape` hung from chosen chestpieces, in the set's colour.
- **Villager remarks** (`set-remarks`): MCA villagers make one oblique remark on a known set (like the grime remarks),
  never a tooltip paragraph.
- **Twinned** (`twinned`): paired harness sworn at a shrine; while both halves are worn within 16 blocks, 25% of a hit on
  one goes to the other.

### 3. Faith sets (Kingdoms)
- Barrow warden's mail: dropped by barrow knights and the revenant (rusted, rust stays); whole set = barrow knights stay
  peaceful as with a lantern; night vision in barrows.
- Dawnwatch plate: Crowned Sun; Sun-Marshal's guard; sold to the Sun's sworn at high favour; small heal at sunrise,
  night vision.
- Headsman's plate: Crowned Sun's headsmen at Sun-realm gallows; executions fill faster, the set dulls (loses its bonus)
  after 3 days without one.

### 4. Sky sets (Powers, aether)
- Giant-slayer harness: from a fallen Giant's plates, one colouring per highland; resists Giant stomps and knockback.
- Wind-faith robes: sky folk elders; slow falling, longer glide; sold in sky villages.
- Well-warden plate: sky folk sentries by the Cloud Sea wells; slow falling over cloud, harder to see in fog.

### 5. Dark sets (Kingdoms)
- Ashen plate: dropped by demon captains in the Ash; fire resistance, fear on near foes while whole.
- Warden hunter's coat: Lantern Warden's hunters; silvered trim hurts vampires and werewolves; pairs with Exposure.

### 6. Upgrades of Armory sets (Kingdoms)
- Folded lamellar: Ronin remade in folded steel (Mohknee only); light, fast, jump bonus; blossom guards wear it.
- Pit harness: Gladiator remade with pit trophies; taken from a fighting pit's warlord.

### 7. Fantasy Armor itself (Powers build.gradle + Kingdoms compat)
- `ship-ref`: packMods entry (CurseForge reference); its tooltip descriptions turned off in the shipped config.
- `ship-trim`: datapack removing recipes for the sets named after other games' characters (Malenia, Ornstein, Lady Maria,
  Crucible Knight, Silver Knight, Old Knight).
  Also the Grave Sentinel helmet (Andy, 2026-11-15: "the grave sentinel helmet should be removed"): its recipe goes and
  `FantasyArmorCompat` never puts it on anyone; the rest of the Grave Sentinel set stays.
- `ship-dress`: `FantasyArmorCompat` (like `KnightsCompat`, by registry id, no dependency): monsters, undead and kings
  only, so royal tiers still hold.
- `ship-repaint`: waits for Andy's look at the model sheet.

## Open questions
1. (Answered: armour, tools and weapons all go.)
2. Repaint Fantasy Armor's textures in our style, or leave its art as it is?
3. Approve the lore paragraphs.
