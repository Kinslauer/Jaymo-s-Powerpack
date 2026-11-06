---
name: roost-tidings
description: Tidings letters only reach players with a Pigeon Roost, only great news, at most every 2 days (PR #892 MERGED 2026-11-04 01:4xZ, Kingdoms 0.7.494)
metadata:
  type: project
---
Andy 2026-11-04: "tidings should now only come to you if you have a roost built for recieving messenger pigeons, and even then it will be rare, and of large happeneings, nothing small and insignificant." PR #892 (Kingdoms 0.7.494).
- `Roost` block (jaymoskingdoms:pigeon_roost, open sky needed), owners kept on Overworld attachment.
- `Post.news` still feeds town boards; only keys in `Post.GREAT` go to Tidings (50% reach unless it names you), letter at most every 2 days, 5 lines.
- `Post.tidings` = great personal line (roost-gated); `Post.report` = word sent on purpose (ungated "Word for You" letter); `Post.aside` = small line on the action bar.
**How to apply:** new world announcements still use KingdomEvents.announce; add the key to Post.GREAT only if it's a big happening. Builds on [[messenger-post]].
Same PR: loading-screen hints (StoryHoldScreen, `gui.jaymoskingdoms.story.hint.N`, HINTS constant) went 44 -> 81 for recent features; Andy asked for loading messages covering new features ("write more for them"). Bump HINTS when adding.
