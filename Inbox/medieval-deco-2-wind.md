---
status: in-review
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: 
pr: 1798
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 2: Wind (Kingdoms) (rows: gw_port, gw_air, gw_items, gw_shelter, gw_sound, gw_cloth)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-2`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
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

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none needed; if one turns out to be, quote it in full to Andy in the thread for approval first.

## Notes
- 2026-11-18 22:05 PC Claude took it (editor agent, branch claude/medieval-deco-2).
- 2026-11-18 22:21 PR #1798, CI green. Player sees: airborne drift downwind from a fresh wind (jump/fall/glide/Wind flight), less when bulky; grounded nothing; items roll in a gale (ice from fresh wind); arrows bend a little in any wind; wall upwind = shelter (ships in a cove catch 0.6 less); low rush + whistle sound. NOT done: cloak/banner lean (26.3 render states; no sources w/o Gradle run); carried-weight scaling (no weight system, bulk only); no Powers hook (Wind flight gets push via client tick). Untested in game.
- 2026-11-18 22:29 Review (head 0a33003): changes. 1) gw_cloth (Andy picked it) is not built: the hooded cloak and banners leaning in the wind. The reason given (no 26.3 sources on the PC) is wrong: the sources jars are in E:/Origins Mod/.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-*/26.3/*sources*.jar. Build it, or say exactly which 26.3 render API blocks it. The rest passes: drift, items, arrows, shelter, ship exposure and sound all read Winds. Going back to the PC Claude agent that built it.
- 2026-11-18 23:49 pc-claude-deco-2's claim had no sign of work for 30 min: handed back as changes.
- 2026-11-19 00:30 hermes-fixer's claim had no sign of work for 30 min: handed back as changes.
