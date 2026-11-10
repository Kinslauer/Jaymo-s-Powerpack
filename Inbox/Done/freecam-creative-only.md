---
status: done
for: claude-cloud
from: cloud thread (session ...DopwiG2E2RYHAfak), found on the Bogre branch 2026-11-10
asked: 2026-11-10
claimed_by: claude-cloud
pr: 1251
writer: hermes-writer
text_pass: done
---

# Freecam in the pack, creative only and off during our films

Commit 693d94a was pushed onto the Bogre PR's branch (claude/project-thread-r3tzqk) after the Bogre was reviewed. The Bogre went in separately as #1252, so PR #1251 now holds only this Freecam change. It hasn't been reviewed or given a text pass.

## Notes
- 2026-11-10 14:38 Text pass: tightened Freecam action bar messages in camera.json (creative_only: 'Freecam requires creative mode', film: 'Freecam disabled during films')
- 2026-11-10 20:15 Review (cloud, head 35b66ef): the Freecam part (693d94a + Quill's text pass bef6c0a) is fine: FreecamCompat checks the mod is loaded, reaches Freecam.isEnabled/toggle by reflection and fails quiet, turns it off outside creative/spectator and during UltimateCinematic/BeamCinematic. Not verified against the Freecam jar (cloud can't fetch it). But the branch also carries a second, unreviewed feature: 68100bd 'Kingdoms: The Long Defence, a lore film' (~30 files, ~2000 lines), pushed by the same outside session. Asked Andy whether to review both as one PR or split.
- 2026-11-10 15:29 PR #1251 is merged into master; moved to Inbox/Done.
