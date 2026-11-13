# Blossom Lands Plan: Mohknee, the far continent, and everything on it

Andy (2026-11-12) linked Sengoku Jidai, Japanese Overhaul (QwikSand; All Rights Reserved, Fabric 1.21.1 only) to aid
the blossom villages, and ticked 59 of the 63 rows of the pick list (https://claude.ai/artifact/GEmrtBD44ntMuLdmnj3Rz6,
db `picks/andy`). Left out: throwing blades ("we have kunai") and the three rule-breaking rows. Ideas only: our own
code, our own names in the Blossom tongue, our own vanilla-style art, clean-room (behaviour spec first).

**Andy's notes on the picks.**
- Rework vanilla for the whole pack: "yeah absolutely not, but i would like all this to be on a seperate continent
  spoken about by villagers, and reached by LONG sailing, if that is at all achievable then we add all of this. if not,
  it can be sidelined."
- Replace the Nether with an underworld: "can we add it as a biome instead?" Yes: that is the picked row
  `ow_yomi_biome`, patch 15.
- Folded steel from iron sand: "their smiths will teach you about the process". Patch 8.

**Is the continent achievable? Yes, in the Overworld, with no new dimension or portal.** Kingdoms already owns the
Overworld's land shape (`data/minecraft/worldgen/density_function/overworld/continents.json` and its neighbours), and
26.3's own density functions can measure the distance to a point. Ships top out at 0.8 blocks a tick
(`Ship.FASTEST`), so the width of the sea sets the length of the voyage. The land lies far outside anything generated
so far (the server is pre-generated to 2,500 blocks), so it appears in the worlds we have: no reset.

**What it builds on.** `Blossom`, `BlossomVillages`, `JobHalls`, `WalledTowns`, `Buildings`, `Climate`/`Languages`
(the Blossom tongue), `Careers`, `Cafe`, `Carousing`, `Festivals`/`Bouts`, `Shrine`, `McaGraves`, `Uniforms`,
`Rulers`, `MobCombat`/`Duellists`, `BossPosture`, the stealth overhaul, `Poisons`, `sailing/*` (`Ship`, `Fleet`,
`SeaRoutes`, `Navies`, `Corsairs`, `Berths`), `SeaEvents`, `Offshore`, `HighSea`, `Milestones`, `ShipTalk`/`WorkTalk`/
`TableTalk`, `astronomy/Measurements`, `Moons`, Powers' `tinkering` (`WeaponParts`, materials), `HotSprings`/`Resting`,
Weapon Forms, `ArsenalRelics`, Kitchen's rice, tatami and brewing through `KitchenCompat`, the seasons.

One PR per patch, each with its change notes. Two tracks at a time, each with its own verifier. Every creature, people
and place waits on its Lore.md `(new)` paragraph. Patches 1 and 2 come first; the rest need the land to stand on.

## Patch 1: the far land (built)

No code shapes the land: 26.3 has `minecraft:distance_to_point`, `slice`, `lerp` and `clamp` density functions, so
`tools/gen_mohknee.py` writes it all as data and `seas/Mohknee` holds the same numbers for the game's rules.

- **Mohknee**: middle at x 28,000, z 0; 3,000 blocks in radius with a shore that strays up to 450 from the circle.
- **The crossing**: 16,000 blocks of deep sea on every side (Andy: "Two days and nights"), then 1,500 where the home
  world's shape fades back in. The sea begins 7,500 blocks east of the world's origin. Eight islands stand in it,
  three on the way from the west (Andy: "you can encounter tideborn islands on the way"), each with one village at
  its middle (`crossing_isle_village`, the island structure's `only_at`); `Havens` makes an island's settlement
  Tideborn.
- **Its country**: the heart is held far inland and high, mild and dry, with its own finer climate noise, which is
  where the game puts cherry groves. A test world gave cherry groves 13% of the land, spread all over it, with
  meadows, plains, flower forests, forests and rivers between; no desert, jungle or badlands.
- `BlossomVillages` rises only on Mohknee. `Palette.at` makes every settlement there Blossom. `MohkneeStructureMixin`
  keeps vanilla villages and other mods' surface structures off it.
- `/kingdom mohknee` (bearing and distance) and `/kingdom mohknee map [scale] [reach]` (paints `mohknee.png` from the
  generator itself, no chunks loaded): how the shape was checked, in a Kingdoms dev server.
- Not checked: the full pack's worldgen mods (William Wythers' Overhauled Overworld, Lithostitched, Geophilic) over
  it, and the land walked in game.

## Patch 2: word of it, and the way there

*Built (2026-11-13, `Helmsmen`, `MohkneeTalk`, `BlossomTraders`): the gossip, the hired sea captain, a companion at
the helm, the course to Mohknee from a sailor who has made the crossing (one in three), blossom traders calling at
realm harbours, and bought passage both ways. Not built: the rutter and latitude (the sailor who has crossed stands in
for it), the navy patrol, keeping coaches and fares off the land, and a harbour town on Mohknee (patch 4).*

- **Spoken of.** Harbour folk, sailors, merchants and old soldiers on the home continent bring it up in talk
  (`WorkTalk`, `ShipTalk`, `TableTalk`): a land east over the water, what comes from it, who went and did not come
  back. Never as screen text; never a map marker.
- **Blossom merchantmen.** Now and then a ship of blossom build (`Fleet` blueprint, blossom crew through `Crewmen`)
  calls at an eastern harbour with tea, rice wine, lacquer, paper and steel, and sails home east. Follow her, or buy
  a rutter from her captain: a bearing and a count of days, no coordinates.
- **Finding it.** The rutter gives a latitude; `astronomy/Measurements` already lets a player take theirs from the
  pole star. Sail east along it.
- **Only by sea.** `Milestones` coaches and teleport fares never cross. A harbour town on the land's west coast is
  where ships make landfall. Flight and pods: open question 2.
- **Someone else at the helm** (Andy, 2026-11-12: "can there be an MCA captain or companion that can pilot the ship
  for you? thats just too long to be sailing manually"). Crewed ships already steer themselves (`Ship.Captaincy.helm`,
  `SeaRoutes`, `Corsairs`; the sky ferries are sailed by two villagers). Three ways, all on that: (1) hire an MCA sea
  captain at a harbour for coin, who takes the wheel of your own ship and holds a course you give him (a harbour you
  know, a rutter's bearing, "follow that ship"); (2) a companion, hired hand or family member with sea experience
  can be told to take the helm the same way, slower and less sure in a blow than a captain; (3) buy passage on a
  blossom merchantman and ride as a passenger. With someone else steering you are free to walk the deck, fish, cook,
  fight what comes, or sleep in a berth, and sleeping aboard passes the night as it does ashore.
- **The crossing.** `SeaEvents` run the whole way; `Berths` keeps your spawn aboard; stores matter (the sea is
  empty of land). A blossom navy patrols the last stretch and hails strangers.

## Patch 3: building blocks (bl_shoji, bl_tatami, bl_plaster, bl_lacquer, bl_paperlantern, bl_stonelantern, bl_thatch, bl_tansu, bl_futon, bl_hearth, bl_ward)

Our own blocks in Kingdoms, then `Blossom`'s palette swapped onto them (paper for glass panes, plaster, lacquer for
red concrete, the one-block stone lantern, tatami through `KitchenCompat`). Sliding doors open for villagers through
`Doorways`. Sap is tapped from trees for lacquer. Ward ropes stop night spawns close by. Thatch is for the whole pack.

## Patch 4: kinds of village (bv_kinds, bv_paddy, bv_thatch, bv_graves, bv_farmstead)

`BlossomVillages` picks hamlet, market, temple, stilt or snow layout by the ground. Paddies replace wheat plots
(Kitchen's rice through `KitchenCompat`). Thatched minka for poor villages. A Blossom graveyard for `McaGraves`.
Outlying farmsteads, and a lighthouse where a village meets the sea.

## Patch 5: halls (bv_teahouse, bv_brewery, bv_bath, bv_dojo, bv_inn, bv_pagoda, bv_wayside)

`JobHalls`: tea house (on `Cafe`), rice wine brewery (brewer career, `Carousing`), bath house round a hot spring
(`Resting`), dojo (guards drill, `Bouts`, a master teaches Weapon Forms). Roadside inns. The pagoda as a
`WalledTowns` tier building. Wayside shrines, each kept to one of the eight faiths.

## Patch 6: folk and dress (fk_hats, fk_dress, fk_fibre, fk_houses)

Straw hats, basket hat and rain cape. Blossom outfits in `Uniforms`. A fibre crop for string and cloth. Each blossom
kingdom gets a house crest and colours.

## Patch 7: soldiers (fk_levy, fk_monks, fk_riders, bv_outpost, ar_matchlock)

A levy rank with spears and matchlocks under the ronin-armoured sworn men. Shrine and pagoda guards with glaives.
Mounted road patrols. Border watch forts. A matchlock look for our musket.

## Patch 8: the forge (ar_heads, ar_steel, ar_heirloom, ar_suit)

New `WeaponParts` kinds (glaive, great blade, studded club) with Weapon Forms moves. Iron sand in the land's
riverbeds and folded steel as a Tinkers material: **blossom smiths teach the process** (Andy), step by step in talk
and at their forge, and it cannot be made before one has shown you. One heirloom per house. The shadow suit and the
lord's armour.

## Patch 9: shadows, the warlord and ruins (fk_shadow, fk_warlord, bv_ruins)

Hired shadows sent by rival lords. A rare outlaw castle held by a warlord (`BossPosture`, `BossTurns`). Blossom ruins
as quest sites.

## Patch 10: the land (wl_autumn, wl_flowers, wl_bamboo, wl_koi)

Maple and ginkgo that turn with the seasons, flower fields, wisteria, bamboo stands villagers cut, koi ponds in towns.

## Patch 11: animals I (wl_monkey, wl_crane, wl_deer)

## Patch 12: animals II (wl_raccoon, wl_serow, wl_hornet, wl_salamander)

Each by the three rules (sympathy, feedback loops, stewardship), as written in the pick list rows.

## Patch 13: night things I (ng_river, ng_mountain, ng_moon)

## Patch 14: night things II (ng_snow, ng_bones, ng_crow, ng_sea)

Our own names; told of by blossom folk, never explained on screen. The sea giant belongs to the crossing.

## Patch 15: the pale grove in the Nether (ow_yomi_biome)

A Nether biome, not a replacement: pale wisteria, a slow river, an ogre keep as a demon stronghold. It is the
blossom folk's telling of the demons' country.

## Patch 16: the sunken palace (ow_palace)

A rare drowned palace in the deep sea off the far land, guarded by sea folk.

## Open questions

1. **How long is the voyage?** Answered: two days and nights, 16,000 blocks of open sea.
2. **Flight and pods.** Proposed: nothing stops a flier (16,000 blocks is long for them too), but pods cannot be set
   down there and no coach or teleport fare reaches it.
3. **Blossom villages already standing on the home continent** in the worlds we have. Proposed: they stay, as
   settlers from over the sea; no new ones rise there.
4. **The land's name**: answered, **Mohknee** (Andy, 2026-11-12). The houses' names are still open.
5. **Starts.** A blossom-born life story start on the far land: wanted, or is everyone a stranger there?
