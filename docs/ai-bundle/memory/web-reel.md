---
name: web-reel
description: Web: both swing webs on small mobs/items + Shift reels them in (Andy 2026-10-15); PR #237, pack 0.11.42 MERGED
metadata:
  type: project
---
Andy 2026-10-15: webbing an entity with both arms and pressing Shift should pull small mobs and items.
PR #237 MERGED 2026-10-15 10:05Z as pack 0.11.42 (branch claude/project-thread-gpv20t), not playtested. Jar /mnt/project-files/web-reel/.
Swing webs now stick to ItemEntity (ITEM_MAGNET 0.6 so floor loot doesn't steal swings). WebAnchors.reelable = item, or non-player <= REEL_MAX_SIZE 1.0 block² and not heavy; bigger catches keep the two-web slingshot.
**How to apply:** tune REEL_* in WebPower; client skip is WebSwing.reelingIn. Related: [[web-lines-weight]], [[web-swing-cling]].
Same PR: Andy "wall jump should only activate when his hands are webbed": WebParkour.canWallJump = webbed fists; kicking off from a cling (incl. suit cling) still allowed.
Same PR: double-tap Shift while reeling = hard yank (REEL_YANK_* in WebPower, server detects the taps in tickReelTaps); CombatRollTaps skips its roll while WebSwing.reelReadyTick is current.
