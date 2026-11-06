---
name: character-names
description: Players go by MCA character names, never usernames (except commands); #394 + #410 (Kingdoms 0.7.160 / pack 0.15.80), 2026-10-22
metadata:
  type: project
  modified: 2026-10-22T03:23:50.519Z
---
Andy 2026-10-22: "usernames should only show up for commands, it still appears in scoreboard and over characters and it shouldnt. especially when playing duplex".
- #394 (0.7.149): Kingdoms `CharacterNames` swaps the MCA family-tree name in for display name, tab list, Jade, Xaero's.
- #410 (0.7.160, merged 03:2xZ): MCA's creator defaults to the username, so players who kept it got a random MCA name (`McaBridge.randomName`), written into the family tree and saved as `CharacterNames.GIVEN`. A player can't keep their username as their character name.
- "Duplex" = the Xeno-Band copper-golem form that splits off doubles (`XenoDouble`); doubles now follow the owner's current display name.
- Not playtested. If Andy still sees usernames, next places to check: the vanilla sidebar objective (score holder names) and MCA's own player renderer name tag.
