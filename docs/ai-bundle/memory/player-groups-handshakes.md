---
name: player-groups-handshakes
description: Player groups via handshakes (#477, Kingdoms 0.7.184 / pack 0.15.147): both use on each other, cinematic, scoreboard team perks
metadata:
  type: project
  modified: 2026-10-24T15:20:58.163Z
---
Andy 2026-10-24: group system; see player tags after both right-click each other for a cinematic handshake + success sound; scoreboard team integration with reduced team damage and other perks.
Built in PR #477 (merged 2026-10-24 22:20Z, with the combined merge #490; not seen in game): Kingdoms `Handshakes`, `Groups`, client `HandshakePose`, `HandshakeCinematic`, `HandshakeCameraMixin`.
- Trigger: plain use, empty hand, NOT sneaking (sneak+use = vampire bite / downed pick-up / Carry). Offer lasts 10 s; the other uses back → 46-tick shake, chime.
- Handshake = names learned + taker ALWAYS joins the offerer's group (Andy 2026-10-24 18:01Z). A group is a vanilla scoreboard team (prefix `jk_group_`); an op's /team counts too.
- Cinematic only once per pair (saved `Handshakes.SHOOK`); later handshakes are a quick dap, no film (Andy).
- Perks: names always known (Introductions.knows + tab list), friendly blows 25%, 5% less damage taken per nearby groupmate (max 3), see invisible, no shoving, /teammsg. Commands /group, /group leave, /group name.
- Perk numbers are guesses, in `Groups` constants.
- Film order since #521 (merged 2026-10-25, Andy: hands coming together must be front and centre): close side shot of the hands meeting first (0-24), wide pull-back, over the shoulder. Taker waits 3 ticks before reaching; grip at tick 12.
Builds on [[character-names]] and the 0.7.180 introductions.
