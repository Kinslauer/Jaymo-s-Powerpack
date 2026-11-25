---
status: in-review
task: Inbox/village-mods-5-town-life-hats.md
branch: claude/village-mods-5
with: 
pr: 2122
mods: kingdoms, powers
versions: 
changes: changes/kingdoms/claude-village-mods-5.md, changes/powers/claude-village-mods-5.md
started: 2026-11-24
owns:
  - kingdoms: GiftHouses, TownDoings, KnownGoods, TailorHats, Pilgrims (new); client TownDoingsClient (new); edits in Commissions, TalkErrands, TownLedger, Careers, Reputation, KitchenCompat, Tales, VillagerTradeMixin, VillagerPriceMixin, McaCommandMixin, McaScreensMixin, McaTailorButtonMixin, JaymosKingdoms/JaymosKingdomsClient (register lines)
  - tale the_pilgrims_road.json; lang parts trade-hats, known-goods, town-doings, pilgrims
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
PR #2122, CI green (Powers, Kingdoms, Kitchen builds, pack start test). All seven rows built: gift house at reputation 800 (Town business), "What the town is doing" page (sneak-use the board with an item, or Town business), each land's known dish and good for regulars (10 / 30 trades, Kitchen dishes via KitchenCompat), the wandering pilgrim and tale "The Pilgrim's Road" (hints only, four old altars), seven trade hats ported from Villager Hats (MIT) in the head bauble slot, zombie villager drops and a tailor's Sew a Hat button, and +10% town goodwill for wearing your job's hat. Lore: one (new) paragraph, "The pilgrims of the old road". Hat previews: Assets/village-mods-5/. Not played: nothing checked in game.
