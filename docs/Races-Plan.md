# Runic Races into heritages (plan)

Andy (2026-11-03): "adding whichever of the races we dont have as heritages, and using the weakness/abilities".
Source: Runic Races by Otectus (MIT, Forge 1.20.1 Origins add-on, github.com/otectus/runic-races). We may port its ideas
and art with the MIT notice; we write our own 26.3 code. Each race has one active ability, one passive and one weakness.

## Races we already have (fold their kit into our heritage)
| Runic race | Our heritage | Their kit we borrow |
|---|---|---|
| Primian, Celeron, Valen (Human) | Heartlander (variants) | Stroke of Fortune / Messenger's Dash / Unbreakable Stand |
| Magi (Human) | Earthling (Inkmarked look) | Arcane Overflow; warded / frail. Folded into the Earthling's Inkmarked rather than a fourth Heartlander look (0.7.490) |
| High Elf, Moon Elf | Sylvan | Arcane Reflex, Moonlit Veil; weak by day / frail |
| Dark Elf | Nightborn | Shadowmeld; sunlight weakness |
| Ice Elf, Frost One, Ice Drake | Frostborn | Frostbind / Glacial Resolve; fire weakness |
| Deep One | Deepborn | Tremorsense; sunlight |
| Forge One | Emberborn | Forge Blessing; heavy in water |
| Iron One, Sky One, Runic One | Stoneborn (variants) | Shield Wall / Mountain Leap / Rune of Warding |
| Sea Serpen | Tideborn | Tidal Breath; landbound |
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
By family, races as variants: two new heritages, **Beastfolk** (Arachnid, Avian, Canine, Feline, Kitsune, Serpen) and
**Hollow** (Zombie, Skeleton, Wraith, Reaper). Each race is a variant with its own ability, passive and weakness.
Andy (17:30): "Dryad and all the fae are kind of what i intended from sylvan, so wrap all them up into one." So **Sylvan**
takes every Faeborne race (Dryad, Changeling, Sprite, Nymph, Faerie) as variants, alongside its elves (High, Moon, Blood).
Races we already have fold in as kits.

## Built: heritages and variants (Kingdoms 0.7.490)
The Magi kit (Arcane Overflow) went on the Earthling's Inkmarked look, since Heartlander has only three looks.
- **Beastfolk** (`BEASTFOLK`, **pickable**, stats Agility + Endurance): base look "Tawny" plus the variants `arachnid`, `avian`,
  `canine`, `feline`, `kitsune`, `serpen`. Life story: born in a den (`born.den`), raised by the pack (`raised.pack`).
  Villagers: weight 18, leaning to savanna, taiga and jungle. Shunned degree 2 (own `shunned.beastfolk` line). Lean: zfighter.
- **Hollow** (`HOLLOW`, **earned**, stats Endurance + Grit): base look "Pallid" plus `zombie`, `skeleton`, `wraith`, `reaper`.
  It wakes in a player the undead killed who then got up again: the killing blow from an undead mob marks them (in memory), and
  the next unlock check (every 5 s) after they respawn alive wakes it (`Heritage.hollowCheck`; hint text stays oblique).
  Villagers are never rolled Hollow, only inherited from a Hollow parent. No birthplace answer (earned), no life-story birth.
  Shunned degree 4 (own `shunned.hollow` line). Lean: darktide. Not on the lab road or the greenskin road.
  The mark is cleared when the server stops (so singleplayer never carries it into another world). Hollow does not count
  as an earned heritage for `DragonRecords.trusted()`: dying to the undead is too easy to unveil disguised dragons
  (**Andy to confirm**). The Wraith's mist marks draw translucent (`HeritageLooks.marksType`); the body under them stays
  opaque, so it reads as a haze, not a see-through body.
- **Sylvan** now has ten looks: the old `thornrose` and `blossomkin` keep their ids (saves and `LifeStory`), and `dryad`,
  `changeling`, `sprite`, `nymph`, `faerie`, `highelf`, `moonelf`, `bloodelf` follow. `HeritageVariants` has no cap: the
  screen lists every look and the first look's birth weight grows with the count (`BASE_SHARE_VARIANTS`).
- Variant ids are the lowercase enum names and are stable. The Deepborn's own `hollow` variant is unrelated to the Hollow
  heritage (different enums, different texture folders).
- Looks: `tools/gen_heritage_looks.py` paints beastfolk and hollow bases and all 18 variants on the flat 64x64 skin (ears and
  tails are painted on the crown and back, wings on the back; no new model geometry). **Andy to confirm** whether he wants
  real ear and tail model parts like the Tideborn's fins and the Warrior-born's tail.
- Z Fighter lineage: none for either (falls back to Earthling, `ZFighterLineage.fromHeritage`). Variants carry no attribute
  traits yet: the kit track adds them.

## Checklist
- [x] Andy picks the shape (by family, races as variants)
- [x] New heritages + variants: looks, body sizes, stats, skin ranges, life-story births (tongues: none exist per heritage)
- [x] Kits (active, passive, weakness) for every heritage and look: `HeritageKits` (Kingdoms 0.7.490 marks). Ability on the heritage key `;`
  (existing left-mouse holds kept: Frostbind, Thunderclap, Sunflare, Stretch; the Bio-built's and Rubber-folk's engulfing), gift and flaw
  as plain attributes or hooks; villagers get the gifts and flaws. Card rows are coloured by kind (gift green, flaw red, ability gold).
- [x] MIT notice for Runic Races in the credits (`kingdoms/src/main/resources/licenses/runic-races`; **Andy to confirm the copyright line**)
- [ ] Voice the Beastfolk life-story tales (`born.den`, `raised.pack`): run `tools/voices/make_voices.py narrator` on a machine
  with the VoxCPM2 model (makes the clips and the `sounds.json` entries). Until then both are read in silence (`Voices.register`
  skips an answer tale with no clip).
