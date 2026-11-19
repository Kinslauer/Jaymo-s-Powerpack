---
status: open
for: claude
from: claude-cloud-medieval-music-thread
asked: 2026-11-19
claimed_by: 
pr: 
thread: cmsg_01638ouFWZKwEZ1ET74kBk9BDvxv3Xuvj7a4gmHTqtzmQ6
---

# Publish the medieval music release (medieval-music-v1)

**Andy (2026-11-19 07:52 UTC):** "what music zip? can you help resolve this?" then "can you put it in the vault?". Patch 1 (medieval-deco-1) makes the client fetch `Jaymo.Medieval.Music.zip` and `.sha1` from the GitHub release tagged `medieval-music-v1`; that release doesn't exist yet. The zip (117 MB) is too big for the vault branch (GitHub refuses files over 100 MB), so it's built here instead.

## Do
1. On the PC, in the repo: `git fetch origin claude/medieval-deco-1` and `git show origin/claude/medieval-deco-1:tools/music_trim.py > %TEMP%\music_trim.py` (or use master if patch 1 is merged).
2. `python %TEMP%\music_trim.py --out dist/music` (needs ffmpeg with libvorbis; takes ~5 min). Expected SHA-1 of the zip from the cloud build: `bcb3baf10634cc26c5081624c83a6100c67dabd6` (ffmpeg versions may differ; any result that loads is fine).
3. `gh release create medieval-music-v1 dist/music/Jaymo.Medieval.Music.zip dist/music/Jaymo.Medieval.Music.zip.sha1 --repo Kinslauer/Jaymo-s-Powerpack --title "Jaymo Medieval Music" --notes "Alexander Nakarada's medieval music (CC BY 4.0), Sarisan's 26.3 pack port (CC BY 4.0), re-encoded at ~80 kbps. Asset for the pack's music; not a mod release."`
   Mark it not-latest if gh asks (`--latest=false`) so it doesn't pose as the pack's release.
4. Check `https://github.com/Kinslauer/Jaymo-s-Powerpack/releases/download/medieval-music-v1/Jaymo.Medieval.Music.zip` downloads.
5. Tell Andy the CurseForge credit line (he adds it): "Music: Alexander Nakarada (creatorchords.com), CC BY 4.0, via Sarisan's Medieval Music pack (CC BY 4.0), re-encoded."

Andy already has the built zip in the project Library if gh isn't available: then he uploads both files at https://github.com/Kinslauer/Jaymo-s-Powerpack/releases/new?tag=medieval-music-v1 by hand.
