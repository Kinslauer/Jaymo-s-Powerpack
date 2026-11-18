# Light armour plan: mail and leather worth wearing

Andy's picks (2026-11-18) from S&C Leather Armors (Chained Leather Armor, ALEXYSSSJ4): **All Rights Reserved**, Forge/NeoForge
only. Ideas only. The code, names, numbers and art are our own. Pick page: https://claude.ai/artifact/LKxbqFDrg4EXXM586zkK98
(db doc `picks/andy`). He picked all 16 rows.

Andy's note on `fs-backstep`: "the backward slip should be for everyone, this and other light armor sets keep it enabled
unlike copper tier or higher." Reading taken: the Dodge's backward hop keeps its evade (the first `Dodge.EVADE_TICKS`) for
anyone whose worn pieces are all padding or mail (`ArmourKind` CLOTH/MAIL, or bare). One plate piece (copper, iron, gold,
diamond, netherite, anything `ArmourKind.PLATE`) turns the evade off for a backward hop. Sideways and forward hops are
unchanged. No set bonus is needed for it.

"Light" below means every worn piece is `ArmourKind.CLOTH`, `MAIL` or `NONE`.

Already ours, not rebuilt: the chainmail recipe (`MailRings`: nuggets + hammer -> 3 Mail Rings, 9 rings -> Mail Sheet,
5/8/7/4 sheets per piece); `Blows` blow-by-armour shares (cut vs mail 0.6, blunt vs padding 0.75); harness layers
(`Harness`, `ArmourKind.harness`); `Exertion` load (mail 0.6, padding 0.2); mail/plate stop cuts bleeding (`Bleeding`);
`Rust`; `HideTraits`/`HideOrigin`; `ArmourWear`; `Dodge`; vanilla dye and leather boots on powder snow; Armored Foes
chainmail spawns.

Lore: the new `(new)` paragraph "Jacks and studded hides" in Lore.md (Andy to approve). The smithing table is locked
until an Ogre King falls, so neither set uses it: both are made at the crafting table.

---

## Patch 1: the two sets (Powers; small Kitchen compat)
Rows `cl-jack`, `cl-studded`, `cl-keep`, `cl-repair`, `fs-snow`. Package `com.jaymospowers.tinkering.smithing` beside
`MailRings` (new class `LightArmour`), lang part `lang/parts/light_armour.json`.

- **Mailed leather** ("Mailed Cap / Mailed Jack / Mailed Chausses / Mailed Boots"): shapeless at the crafting table,
  one leather piece + Mail Sheets (2/4/3/2) + string. Armour between iron and diamond in points (helmet 2, jack 7,
  chausses 5, boots 2, toughness 1), durability between iron and diamond. Dyeable like leather (two-layer texture: dyed
  leather + undyed mail overlay). Tags: `jaymospowers:mail_armor` and `cloth_armor` both; `ArmourKind.of` picks MAIL
  (tag order), and `Blows.meetArmour` takes the better of the cloth and mail shares for a piece in both tags (one new
  helper in `ArmourKind`, `padded(stack)`).
- **Studded leather** ("Studded Cap / Brigandine / Studded Leggings / Studded Boots"): `jaymospowers:hammer_crafting`,
  one leather piece + iron nuggets (3/6/5/2) + any `#jaymospowers:hammers`. Armour a step above leather, below iron
  (1/4/3/1, toughness 0.5), cheaper than mail. Dyeable, two-layer (dyed leather + iron studs). Tag `cloth_armor` only;
  against a cut it does 0.95 instead of padding's 1.15 (`Blows`, read by the new `studded` tag).
- **Keeps the leather** (`cl-keep`): the recipe copies the leather piece's components (dye, `HideOrigin` stamp, custom
  name, enchantments) onto the result, and its damage carries over as a share of the new max. Kitchen's `HideTraits`
  must recognise the new pieces as leather (`HideTraits.isLeather` reads a tag `jaymoskitchen:hide_armour`, which these
  join through an optional tag entry; no Kitchen code change if that tag already exists, else one line).
- **Repair** (`cl-repair`): anvil repair items: mailed = Mail Rings (and leather), studded = iron nuggets (and leather).
  Inside `ArmourWear`'s repair rules (a broken piece still refuses).
- **Powder snow** (`fs-snow`): both new boots walk on powder snow like leather boots. Check
  `PowderSnowBlock.canEntityWalkOnPowderSnow` in the 26.3 source: if it reads a tag, add both boots to it; if it checks
  the leather boots item, add one mixin beside `ArtifactPowderSnowMixin`. They do not join
  `freeze_immune_wearables` (Weathered handles freezing).
- **Art:** original vanilla-style 16x16 icons and equipment layers (`tools/gen_light_armour.py`), leather tones from
  vanilla's leather, mail links from our chainmail palette, studs from iron nugget.
- Mob and loot use is Patch 4.

## Patch 2: what mail is good at (Powers combat, freerun, stealth)
Rows `mb-arrows`, `mb-nimble`, `mb-slow`, `mb-quiet`. No new items; reads `ArmourKind`, so it can run beside Patch 1.

- **Arrows** (`mb-arrows`): `Blows.missile` gives a plain arrow (damage type `minecraft:arrow`) against MAIL
  `ARROW_VS_MAIL = 0.75`. An arrowhead with its own way with armour (bodkin, swallowtail) and the war dart keep their
  own share; tridents and thrown blades are unchanged. Mail wears less for it, as `Blows` already does.
- **Nimble** (`mb-nimble`): climbing grip, freerun vault speed and swim speed are slowed by plate only: scale by the plate
  share of `ArmourKind.load` (a full plate suit: climb grip -30%, vault -20%, swim -20%). Light armour: no change.
  Find the climb/vault/swim classes in `docs/codemap/powers.md` (freerun, climbing).
- **Slowness** (`mb-slow`): each MAIL piece shortens a new Slowness on its wearer by 10% (40% in a full set); in a full
  mail set you may sprint while slowed. One `MobEffectInstance` add hook.
- **Quiet** (`mb-quiet`): the distance mobs notice you is scaled by armour: padding x0.9, mail x1.0, plate x1.15, per
  piece share. Hook where `HideTraits` "Hushed" already scales it (Kitchen) or Powers' stealth equivalent; it multiplies.

## Patch 3: full-set boons (Powers stamina, frost; Kingdoms harness via ArmourKind)
Rows `fs-backstep`, `fs-weather`, `fs-mixed`. Runs after Patch 1 (needs the set tags).

- **Backstep** (`fs-backstep`, Andy's note above): in `Dodge.evades`, a backward hop (direction more than 120 degrees
  from facing) evades only when the dodger is light. Every other hop as now. In a full mailed-leather set the backward
  evade also knocks an arrow aside (reverses it at 0.4 speed, no damage), then rests 15 s; Projectile Protection takes
  0.5 s per level off the rest (5 s at least). Shown by the existing dodge sound plus a chain rattle, no text.
- **Weathered** (`fs-weather`): full studded set: burning lasts 40% shorter, freezing builds 40% slower (vanilla
  ticksFrozen and our `Freezing`), and counts as one warm piece for Kitchen's `BodyHeat` through its existing hook. Fire
  Protection and Frost Walker each add 10% on top (at most 60%). No immunity.
- **Mixed harness** (`fs-mixed`): a full-set check counts harness layers (`ArmourKind.harness(body, "all")`) as well as the
  four slots, so a gambeson and mail under a surcoat count as a mailed set. Kingdoms already hands the harness over; no
  Kingdoms change.

## Patch 4: the world wears it (Kingdoms + Powers data)
Rows `wd-mobs`, `wd-brutes`, `wd-folk`, `wd-smith`. Runs after Patch 1.

- **Mobs** (`wd-mobs`): add the sets to the Armored Foes equipment tables (`natural_armor`, `raider_armor`) at the
  weights chainmail has; a mob in a full mailed set runs (speed +15% while it has a target).
- **Brutes** (`wd-brutes`): piglin brutes in bastions sometimes wear studded leather, sometimes enchanted; one in a full
  studded set does not zombify in the Overworld.
- **Folk** (`wd-folk`): Huntsman's rangers, scouts, outlaw raiders and ship crews wear mailed or studded leather by rank
  (MCA armour routine / `Uniforms`); always below diamond (`royal-armour-tiers`).
- **Smiths** (`wd-smith`): armourers sell Mail Rings and Mail Sheets and buy worn mail; Kingdoms Careers smith orders
  ask for mailed pieces.

---

Order: Patches 1 and 2 together, then 3 and 4 together. One PR each; each gets an independent verifier. Each PR adds its
change notes (`changes/powers/<branch>.md`, plus kingdoms/kitchen where touched).

## Added to Patch 4 (Andy, 2026-11-18: "Yes do both")
- Fighting folk never sidestep while wearing plate (`MobCombat.light`: every armour piece in `#jaymoskingdoms:light_armor`).
- The Huntsman's rangers and ship crewmen (MCA villagers) sidestep a player's blow 12% of the time in leather or mail.
