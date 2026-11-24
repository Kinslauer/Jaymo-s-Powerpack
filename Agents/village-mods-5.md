---
status: running
task: Inbox/village-mods-5-town-life-hats.md
branch: claude/village-mods-5
with: 
pr: 
mods: kingdoms, powers
versions: 
started: 2026-11-24
owns:
  - src/main/java/com/jaymospowers/baubles/TradeHats.java (new), Baubles.java (one init line)
  - src/client/java/com/jaymospowers/client/baubles/TradeHatLayer.java (new), BaublesClient.java, mixin/BackpackAvatarRendererMixin.java (one line each)
  - Powers hat assets, assets/minecraft/atlases/items.json, tags/item/baubles/head.json, lang/parts/trade_hats.json, docs/licenses/Villager-Hats-MIT.txt
  - kingdoms/src/main/java/com/jaymoskingdoms/ new classes for patch 5 (gift house, town doings page, signature goods, pilgrim, trade hats)
  - small edits in Commissions, Homesteads, TownTrades, Careers, Tailors, Tale (as needed; listed in the PR)
  - lang parts for patch 5 (new files)
  - changes/kingdoms/claude-village-mods-5.md, changes/powers/claude-village-mods-5.md
---
# Village mods 5: town life and trade hats

**Task:** Patch 5 of the village mods plan: a gift house at top reputation, a "what the town is doing" page on the
town board, goods each people is known for, the wandering pilgrim, trade hats (port of Villager Hats, MIT), where hats
come from, and a small friendliness bonus for wearing your trade's hat.

## Plan
- Map the classes each row touches, then build row by row in small commits.
- Stay clear of PR #2113's files (BoardWork, TownNotices, Economy, Upkeep, Bounties, BountyBoard, Quests).

## Notes
- 

## Done
