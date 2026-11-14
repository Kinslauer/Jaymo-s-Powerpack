# Frontier Armaments + Knights & Heraldry: plan

Andy's picks from the page "Frontier and Heraldry Picks" (2026-11-14): all 33 rows, eight with notes. All on one PR, in
six patches. Licences: Frontier Armaments (cleannrooster) is MIT by its LICENSE file, so its models and textures are
used with the notice kept (`tools/bbmodels/frontier-armaments/`, `licenses/`). Knights & Heraldry (B4nduty) is All
Rights Reserved: ideas only, our own code, names and art.

Andy's notes are binding as written:

| Row | Note |
|---|---|
| Templar plate | Sold by the realms whose guards wear it, schematics as well; for Truth and Sun followers; the Truth gets the Templar cross sigil for everything. |
| Frontier settler's gear | Only the guards wear the full helm. |
| Despot's tusked breastplate | Finish it for an orc warlord; only they and greenskins can wear it. |
| Dark-metal versions | Made from steel. |
| Frontier Flintlock long gun | This is the blunderbuss. The other gun (the Firecannon) is a musketoon for the caravan guards, firing cannonballs: long reload, high damage. |
| Forge Hammer | Also works for smithing. |
| Fire-shot hand cannon | The Tinkerer-class Flame Avatars developed it during their struggles in the Nether and are seen carrying it there; the avatars are all different classes. |
| Handgonne and arquebus | Each wealth tier of realm equips its guards with one; the richest is now 1000 gold. |

## Patches

1. **Guns** (Powers + Kingdoms): the long gun becomes the blunderbuss; the Firecannon becomes the musketoon
   (`Musketoon`, `Cannonball.shot`, `MobAmmo.Kind.BALLS`), carried by caravan guards.
2. **Frontier armour** (Kingdoms): Templar, Hedge Mage, Primalist, Mercenary-Ranger, settler's gear, Juggernaut,
   Scoundrel, Sanguine, Despot, Foxshade; set perks; steel (dark-metal) grades.
3. **Frontier gear** (Kingdoms): Forge Hammer (fights, mines, smiths), field mortar, the Flame Avatars' fire-shot hand
   cannon and their classes.
4. **Heraldry armour** (Kingdoms): layered harness, shoulder and elbow plates, late-medieval helms, visor key,
   finishes, arm and leg harness, crests, giornea, hoods and torn cloaks.
5. **Heraldry fighting** (Kingdoms): stamina, weapon sweet spot, cut/thrust/blunt against armour.
6. **Heraldry smithing and arms** (Kingdoms): handgonne and arquebus by realm wealth, arrowheads, war weapons, pattern
   scrolls, forging failure, relic armour in old sand.

Each patch: build both mods, add its lines to the change notes, place it in Lore.md as a `(new)` paragraph. At the end
every row is checked against this table before the PR is called ready.
