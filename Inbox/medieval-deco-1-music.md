---
status: in-review
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: pc-claude-deco-1
pr: 1795
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Medieval music/deco patch 1: Music (Powers pack build + Kingdoms client) (rows: mm_trim, mm_places, mm_festival, mm_lute, mm_gaps, mm_end)

**Andy (2026-11-19):** cross-referenced Medieval Music, Medieval Buildings, Fallen Adventurers, Medieval Deco, MmmMmmMmmMmm, Satisfyu's Let's Do mods and Global Wind; picked 41 of 49 rows on https://claude.ai/artifact/77CpKPurzSqn4YWJe7PJTv (db `picks/andy`), then chose "Send to the PC reviewer". He OK'd hosting the trimmed music zip as a GitHub release asset.

**Full plan:** `docs/MedievalMusicDeco-Plan.md` on branch `claude/project-thread-ttl8xk` (read its header rules first). Row ids in brackets are pick-list rows. Not built yet. One PR per patch, branch `claude/medieval-deco-1`, change note `changes/<mod>/<branch>.md`, never `version=`/MODLOG/codemap. Ideas only from the ARR mods (our own code, names, art); Global Wind's MIT code may be ported with its notice; the music is CC BY (credit Nakarada and Sarisan).

## Picked
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

## Checks
- Compile check against the 26.3 jars; every Minecraft API checked against 26.3 sources.
- `python3 tools/pr_checks.py` passes.
- Lore: none needed; if one turns out to be, quote it in full to Andy in the thread for approval first.

## Notes
- 2026-11-18 21:50 PC Claude took it (editor agent, branch claude/medieval-deco-1), paired with patch 4.
- 2026-11-18 22:14 PC Claude: PR #1795, CI green. Player sees: Medieval Music pack fetched and on (needs release medieval-music-v1 with Jaymo.Medieval.Music.zip + .sha1 uploaded by hand, built by tools/music_trim.py); town/tavern/camp/road/war/festival tracks; a tavern villager with a lute plays positional music (16 blocks); sneak-use with a coin = another song (not the MCA menu); warped forest Nether tracks. Untested in game: all of it. mm_end needed no code (Enderscape uses its own sound events; unverified). CurseForge pack-page credit to Nakarada and Sarisan still to write.
- 2026-11-18 22:19 Review (head b13de48): changes. 1) kingdoms/.../client/music/PlaceMusic.java:95 and :104-106: a track that ends on its own is never seen as ended (26.3 SoundEngine.tick drops the channel and the ticking entry but never calls the instance's stop(), so isStopped() stays false and Track.tick() stops, so faded() never turns true). current is never cleared: no more place music that session, and vanilla music is stopped every 20 ticks. Fix: also treat !minecraft.getSoundManager().isActive(current) as ended (it is how MusicManager does it). Verified by a second agent. Not blocking: tipping is sneak-use with a coin, not the MCA menu; mm_end has no code (Enderscape not confirmed). Fix goes back to the PC Claude agent that built it.
