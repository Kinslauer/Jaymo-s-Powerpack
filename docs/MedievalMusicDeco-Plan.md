# Medieval music, deco and Let's Do cross-reference: the plan (2026-11-19)

Andy picked 41 of the 49 rows on the pick page (https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv, db `picks/andy`).
The sources are Medieval Music (Globox1997's mod, MIT, <=1.20.1; Sarisan's 26.3 resource-pack port, CC BY 4.0, tracks
by Alexander Nakarada, CC BY 4.0), Global Wind (Depixelation, MIT, Fabric 26.3), Realm RPG: Fallen Adventurers (NoCube,
ARR, bars using any contents), Medieval Deco (ARR), Medieval Buildings (page says ARR, 26.1.2 only), MmmMmmMmmMmm (CC0)
and Satisfyu's Let's Do mods (code MIT, art and data ARR, <=1.21.1) plus Sleepy Hollows (Cursee, custom licence).
Everything is our own code, names, models and textures, except the music, which ships as a re-encoded copy with credit,
and Global Wind, whose MIT code is ported with its notice kept.

**Not picked:** shipping the music pack at full size (mm_ship; the lighter copy was picked instead), shipping Global
Wind unchanged (gw_ship; the port was picked), and the whole building and furniture section (ld_timber, ld_supports,
ld_chairs, ld_curtains, ld_glaze, ld_clay). No row notes were left.

Standing rules that bind every patch:
- No screen text that states lore or explains a mechanic; no discovery chat text. Hints only in talk or books.
- No new dimensions or portals. Our own names. Vanilla-style art of our own.
- Tuning numbers are `static final` constants at the top of each class; everything starts gentle.
- Magic effects go through `MagicFx` (Powers) or `Fx` (Kingdoms).
- Living things follow the three rules: sympathy, feedback loops, stewardship (vines, tea, troughs, fish traps).
- Optional mods only through a compat class (Kitchen from Kingdoms by reflection, as `Wind` already does).
- PRs never touch `version=`, the MODLOGs or `docs/codemap/`; each mod changed gets `changes/<mod>/<branch>.md`.
- Kingdoms lang keys go in `lang/parts/<feature>.json`.
- Every new world thing is placed in Lore.md as a `(new)` paragraph for Andy's OK (see Lore placements below).

## The patches

Andy sent all seven to the PC reviewer's queue (2026-11-19): one vault Inbox task per patch.
Seven patches, one system each, so two can run at once without touching the same files. Suggested pairs:
1 + 4, 2 + 5, 3 + 7, then 6.

### Patch 1: Music (Powers pack build + Kingdoms client) — mm_trim, mm_places, mm_festival, mm_lute, mm_gaps, mm_end
- **mm_trim**: `tools/music_trim.py` downloads Sarisan's pack (Modrinth version vz46Jajc), re-encodes the 61 oggs at
  about 80 kbps (ffmpeg, Vorbis), keeps its `sounds.json`, `pack.mcmeta` and `pack.png`, adds a `CREDITS.txt`
  (Alexander Nakarada, creatorchords.com, CC BY 4.0; Sarisan's port, CC BY 4.0; "re-encoded at a lower bitrate"), and
  writes `Jaymo Medieval Music.zip` (~120 MB). It ships through `client/PackResourcePacks` and is on by default.
  **Hosting (Andy, 2026-11-19: yes):** the zip is a GitHub release asset on this repo, and `PackResourcePacks` fetches
  it from there. Pack-page credit to Nakarada and Sarisan.
- **mm_places**: Kingdoms `client/music/PlaceMusic`, beside `FoeMusicClient`: inside a realm's town Townsong /
  Greenlands; taverns and the cafe Now We Feast / Celebration; illager and barbarian camps (`Camps`) Marked / Grundar;
  riding a King's Road (`Roads`) Now We Ride / The Road Home; a war or siege nearby (`Wars`) Behind the Sword / Might &
  Magic. New sound events in Kingdoms' `sounds.json` point at `minecraft:music/<track>`, the pack's own files; when the
  pack is off they resolve to nothing and the vanilla situational music plays. Great-foe themes and scene music win.
- **mm_festival**: on a `Festivals` day, the square plays Celebration and Bonfire, louder near the festival fire.
- **mm_lute**: a villager holding the lute (Nightfall's `Relics.Lute`) in a tavern plays one track as a positional,
  streamed sound that fades with distance; tipping (MCA menu, a coin) makes them play another. One bard per tavern.
- **mm_gaps**: an override in our own small resource pack, loaded above the music pack: warped forest gets three of
  the pack's Nether tracks (the pack empties that list). Aether, Cloud Sea and the far worlds keep their own music.
- **mm_end**: `PackResourcePacks` order puts Enderscape's music above the pack for its own biomes; the pack's End list
  plays only on the main island (vanilla `music.end`).
- Check: the zip loads on 26.3 (pack format 69 to 97), `/playsound` each place event, tavern bard audible at 16 blocks
  and silent at 40.

### Patch 2: Wind (Kingdoms) — gw_port, gw_air, gw_items, gw_shelter, gw_sound, gw_cloth
- **gw_port**: `sailing/WindPush` (or `disasters/WindPush`), ported from Global Wind's `WindCalculator`,
  `EntityMixin` and `LivingEntityMixin` (MIT, notice kept in a comment and in `ports/`), but reading `Storms`' wind
  through `Winds.strength(level, x, z)` and `Winds.way()`, so the push, ships' sails, Kitchen's windmills (already on
  `Storms`), smoke and `FieldWeather` all agree. Overworld only, as now. Creative players aren't pushed.
- **gw_air**: grounded entities feel nothing below a storm; airborne ones (jumping, falling, gliding) drift with the
  wind, scaled down by bulk (`BodySizes`, carried weight). The glide harness and the Wind power's flight (`WindWeather`
  in Powers, through `KingdomsCompat`) read the same push.
- **gw_items**: item entities roll downwind; arrows and thrown spears bend a little in any wind (`FieldWeather`'s
  gale rule made gradual, keeping its gale strength at the top); leaves and smoke particles follow it on the client.
- **gw_shelter**: a cached short ray (8 blocks) upwind at head height: a solid block, glass included, means shelter.
  Global Wind's skylight test is dropped. Ships use the same test, so a sail in a cove catches less.
- **gw_sound**: a `Soundscape` layer whose volume follows strength and shelter: a low rush, a whistle round corners in
  strong wind, quiet indoors. Kept under AmbientSounds' own weather.
- **gw_cloth**: client side, the hooded cloak, banners and ships' sail models lean and flap with the wind's way and
  strength (the client already gets the wind for sails through `Winds.clientOffWind` and its kin).
- Check: a jump in a storm drifts downwind; standing behind a wall, no drift; dropped items roll; arrow lands offset.

### Patch 3: The dead in the wild (Kingdoms worldgen + Kitchen bones) — fa_remains, fa_gear, fa_surprise, fa_page, fa_chamber, fa_rites, fa_far, mb_traps, ld_rider
- **fa_remains**: a `RemainsBlock` (a skeleton lying in one of several poses, our own model from Kitchen's bone art)
  and a worldgen feature placing it, one kind per place: frozen in snow, vine-tangled in jungle, drowned in rivers,
  pinned under dripstone, fallen at cliff feet, sun-dried in desert and badlands, webbed in caves. Rare: about one per
  six chunks in the wild, none inside settlements. Breaking it with an empty hand searches it; silk touch takes it.
- **fa_gear**: a loot table per kind: a miner's pick and ore, a hunter's bow and arrows, a sailor's rope; armour comes
  worn or broken (`ArmourWear`), tools as Tinkers parts where they fit.
- **fa_surprise**: about one in eight is a trap: a tripwire and an arrow from the ground, gravel overhead, a spider
  nest in the ribs (the medieval build's nests). At night a few rise as our old dead.
- **fa_page**: about one in five holds a written scrap (a book, 2 to 4 lines in the dead one's voice) that points at
  a real place nearby: a cave, a buried room, a monolith, a ruin. It joins the Lore Codex. Hints only, no lore stated.
- **fa_chamber**: rare sealed rooms in stone and deepslate (2 templates, built in code like our other buildings):
  a party's last camp, three to five remains around a dead fire.
- **fa_rites**: bury a body with `Graves.bury`, or have a priest give it rites (`DeathRites`). Some carry a token (a
  ring, a carved spoon) naming their home village; bring it there and their kin thank you (a gift and a little
  standing). Leaving them costs nothing.
- **fa_far**: Aether kinds under the islands' edges (let fall by the cloud), Nether kinds in soul sand valleys, End
  kinds on the main island.
- **mb_traps**: a trap pass over ruined towers and forts (ours and the Grim, ATi and Structory ones): an arrow slit on
  a pressure plate, a weak floor over a pit, a door that bars behind you. Never in a building a settlement has cleared
  (`Clearing`).
- **ld_rider**: the headless rider, an undead knight on an undead horse (`BreedHorseModel` skeleton coat), out on
  foggy nights on `Roads` that pass The Graveyard's sites and our graveyards. It chases and tries to behead
  (downed-only beheading still holds for players). It drops its own head; carried to its grave by day, it lays the
  rider down for that road for good, with a reward. A boss bar while it rides.
- Check: `/locate` style test command for op only, each kind generates in its biome, a page points at a real place.

### Patch 4: Drinks and kitchen (Kitchen) — ld_vines, ld_press, ld_vintage, ld_teas, ld_smoke, ld_nether
- **ld_vines**: a grape vine crop on a trellis block in warm lands (wild vines generate on the edges of plains,
  savanna and flower forests). Sympathy: vines fruit on last year's wood, so a vine pruned in winter (shears, once a
  season) bears well, an unpruned one bears less each year. Feedback: ripe bunches left out draw birds that strip them.
  Stewardship: pruned, netted vines give the most. Our grapes feed the existing `GRAPE_WINE` brew alongside Pantry's.
- **ld_press**: a treading tub (jump in it with grapes) and an apple press; their juice goes into `CaskBlock`.
- **ld_vintage**: wine bottled from a cask carries its year; in a cool cellar (`Freshness#cellar`) it improves each
  in-game year up to five, and lords and merchants pay more for older vintages (Kitchen's `KingdomsMoney`).
- **ld_teas**: tea bushes in a few lands (jungle edges, cherry groves, misty hills). Leaves dry on the
  `DryingRackBlock` in sun; the tea kettle brews them. A pot set on a table helps everyone seated there (with
  Kingdoms: `Cushions` seats) with a short calm and warmth.
- **ld_smoke**: the smoker minigame (`SmokerView`) takes a wood chip: oak, birch, cherry, apple (from Pantry) or
  dark oak, each giving a different keep time and fill to smoked meat and fish.
- **ld_nether**: crimson and warped vines on lava-sea shores in the Nether; their dark wine is prized by the demon
  folk (Kingdoms trades) and sells high above.
- Check: JEI shows every route (trellis, press, tub, wood chips, nether wine); a pruned vine out-yields an unpruned one.

### Patch 5: Farm, field and water (Kitchen + Powers fishing) — md_water, md_fish, ld_trough, ld_watering, ld_debris, ld_traps, dm_scare
- **md_water**: a water barrel: fill bottles and buckets from it, rain refills it slowly, clean for `Thirst`.
- **md_fish**: a fish barrel: salted fish keep longer in it (`Freshness`) and the top shows the catch; fishers' huts
  and market stalls get them.
- **ld_trough**: a feeding trough (hay, grain): animals in its pen stay near and breed when fed; when it empties they
  wander to graze (Kitchen `Herds`). Feedback both ways: a fed herd crops the ground less.
- **ld_watering**: a watering can: water a crop by hand on a dry day for a small growth step; never needed.
- **ld_debris**: drifting flotsam and rippling spots on rivers and the sea; a cast into them (Powers `FishCast`) pulls
  an odd catch: a purse, a bottle, old gear, a remains' token (patch 3).
- **ld_traps**: a wicker fish trap, baited, fills over a day; each stretch of water has a stock that traps draw down
  and that recovers over days (feedback, stewardship). Tideborn see a trap in their havens as fishing (`TideFishing`).
- **dm_scare**: the scarecrow prop (and the Practice Dummy) with a carved pumpkin keeps crop-eating animals and the
  vine birds off crops within 8 blocks.
- Check: JEI routes; a trap in an empty stretch comes up near empty.

### Patch 6: Towns and the sea (Kingdoms) — md_figurehead, md_signs, ld_journey, ld_firsts
- **md_figurehead**: a figurehead block in each wood (our own carvings); realm ships wear their realm's (navy,
  merchant, whaler), pirates a carving of their own choosing. Kingdoms' ship builders place them.
- **md_signs**: a hanging trade sign with a mark (anvil, loaf, mug, needle, scales...) set over each workplace by the
  town builders.
- **ld_journey**: a journey book (a private book like the Lore Codex): your firsts with the day each happened, sorted
  by theme. No reward and no toast.
- **ld_firsts**: a stone in each capital where firsts are cut (first to slay each great foe, found a realm, reach a far
  world); bards and villagers mention them in talk.

### Patch 7: Practice Dummy (Powers) — dm_kinds, dm_colours, plus mb_ship
- **dm_kinds**: `PracticeDummy` reads its head and hand slots: a skeleton or zombie skull makes it undead (Smite), a
  turtle shell an aquatic (Impaling), an ominous banner an illager. MmmMmmMmmMmm is CC0, so its approach may be reused.
- **dm_colours**: floating numbers coloured by damage type: crits red, fire orange, lightning yellow, magic and ki in
  their `MagicFx` colours, on top of the size colours.
- **mb_ship**: no build. Medieval Buildings only loads on 26.1.x; add it to packMods once a 26.3 build exists.

## Lore placements (for Andy's OK)

Added to Lore.md as `(new)` paragraphs: the gone-before (patch 3, under *The dead*), the headless rider (patch 3,
under *The dead*), the vines by the lava seas (patch 4, under *The Nether and the demons*), figureheads (patch 6, under
*The sea*) and the song stones (patch 6, under *The ages of the world*). The music, the wind push, the dummy, barrels,
troughs and traps need no lore.
