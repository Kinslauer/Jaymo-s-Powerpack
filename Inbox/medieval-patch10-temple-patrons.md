---
status: in-review
for: hermes
from: Civilizations thread, 2026-11-15
claimed_by: hermes-b
pr: 1964
writer: hermes-writer
text_pass: none
---
# Patch 10: which patron each old-age temple calls

Andy's pairings (2026-11-15) for cv-avatars / cv-ruins. An offering at the temple calls that patron's champion (`PatronChampion`).

| Patron | Champion | Old-age site |
|---|---|---|
| Hearthkeeper | Hearth-Warden | columned temples on the plains |
| Anvil-Father | Forge-Thane | halls cut into mountains / down in mines (new site) |
| Deep Mother | Tide-Bride | sunken colonnades on the sea floor (drowned courts) |
| Green Huntsman | Antlered Hunter | wooded grove shrine, ring of stones (new site) |
| Lantern Warden | Grave-Lamp | stepped tombs in the sands |
| Crowned Sun | Sun-Marshal | long halls and burial mounds of the north (not the moor barrows) |
| Ash Whisperer | Cinder-Shade | walled palace in Blossom |
| The Truth | Inquisitor | a bare old house in the Aether (no new dimension) |

Lore: "Whose the old temples were" in Lore.md (Andy approved 2026-11-15).

## Notes
- 2026-11-19 20:22 Patch 10 merged without this: Temples still draws each site's patron at random. The branch thread is gone, so this is open for a bot: pair each old-age site with Andy's patron as in the table, on a fresh branch from master.
- 2026-11-19 21:58 hermes-b's claim had no sign of work for 30 min: handed back as open.
- 2026-11-19 21:58 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-20 21:29 hermes-b's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-21 00:13 Text pass: reviewed PR #1964 (origin/master..HEAD). Changed files: Temples.java (patron now fixed by site via TempleSites.fits/find instead of weighted random draw; javadoc only), OldAge.java, OldRuins.java, OldRuler.java, People.java (four new old-age peoples ANVIL/DEEP_FOLK/GROVE/TRUTH_FOLK with their patrons, blocks, and structure variants anvilHall/deepCourt/groveShrine/truthHouse), 8 loot tables, 4 biome/structure jsons, 1 structure_set json. No lang parts, dialogue, hints, item/ability text, chat lines or screens were added or changed, so there is no player-visible text to rewrite. text_pass=none.
