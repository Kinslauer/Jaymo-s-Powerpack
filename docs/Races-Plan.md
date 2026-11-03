# Runic Races into heritages (plan)

Andy (2026-11-03): "adding whichever of the races we dont have as heritages, and using the weakness/abilities".
Source: Runic Races by Otectus (MIT, Forge 1.20.1 Origins add-on, github.com/otectus/runic-races). We may port its ideas
and art with the MIT notice; we write our own 26.3 code. Each race has one active ability, one passive and one weakness.

## Races we already have (fold their kit into our heritage)
| Runic race | Our heritage | Their kit we borrow |
|---|---|---|
| Primian, Celeron, Valen, Magi (Human) | Heartlander (variants) | Stroke of Fortune / Messenger's Dash / Unbreakable Stand / Arcane Overflow |
| High Elf, Moon Elf | Sylvan | Arcane Reflex, Moonlit Veil; weak by day / frail |
| Dark Elf | Nightborn | Shadowmeld; sunlight weakness |
| Ice Elf, Frost One, Ice Drake | Frostborn | Frostbind / Glacial Resolve; fire weakness |
| Deep One | Deepborn | Tremorsense; sunlight |
| Forge One | Emberborn | Forge Blessing; heavy in water |
| Iron One, Sky One, Runic One | Stoneborn (variants) | Shield Wall / Mountain Leap / Rune of Warding |
| Dryad | Verdant | Verdant Bloom; triple fire damage |
| Nymph, Sea Serpen | Tideborn | Siren's Charm / Tidal Breath; bound to water |
| Fire/Terra/Volt Drake, Wind Wyrm | Dragonblood (variants) | breaths; matching weaknesses |
| Volt Drake | Stormborn | storm speed; grounded and wet |
| Demon | Powers' demon folk / Nether areas | Infernal Wrath; holy and water |

## Races we don't have (new)
- **Bestial:** Arachnid (Web Snare; fire), Avian (Wind Burst flight; hollow bones), Canine (Howl of the Pack; ravenous),
  Feline (Pounce, nine lives; hates water), Kitsune (Foxfire Illusion; spirit-frail), Serpen (Shed Skin; cold-blooded).
- **Faeborne:** Changeling (Mirror Shift; hollow identity), Sprite (Phase Shift, gossamer flight; glass cannon),
  Faerie (Faerie Bargain; cold iron).
- **Undead:** Zombie (Undying Hunger; sun decay), Skeleton (Conscript the Dead; brittle), Wraith (Spectral Phase; sun
  and holy), Reaper (Soul Harvest; half healing).
- **Elven:** Blood Elf (Blood Frenzy lifesteal; reduced healing), fits Sylvan as a variant or Death/Blood magic.

## Every heritage gets a kit
One active ability (a left hold, like Powers' `HeritageMoves`), one passive and one weakness, shown as Gift / Flaw on the
heritage card. Numbers are tuning constants.

## Andy's pick (2026-11-03)
By family, races as variants: three new heritages, **Beastfolk** (Arachnid, Avian, Canine, Feline, Kitsune, Serpen),
**Fae-touched** (Changeling, Sprite, Faerie) and **Hollow** (Zombie, Skeleton, Wraith, Reaper). Each race is a variant with
its own ability, passive and weakness. Blood Elf becomes a Sylvan variant. Races we already have fold in as kits.

## Checklist
- [x] Andy picks the shape (by family, races as variants)
- [ ] New heritages + variants: looks, body sizes, stats, skin ranges, life-story births, tongue lines
- [ ] Kits (active, passive, weakness) for every heritage
- [ ] MIT notice for Runic Races in the credits
