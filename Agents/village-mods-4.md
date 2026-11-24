---
status: running
task: Inbox/village-mods-4-board-work.md
branch: claude/village-mods-4
with: claude (worker for the PC coordinator thread)
pr: https://github.com/Kinslauer/Jaymo-s-Powerpack/pull/2113
mods: kingdoms
versions: 
started: 2026-11-24
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/BoardWork.java
  - kingdoms/src/main/java/com/jaymoskingdoms/TownNotices.java
  - kingdoms/src/main/java/com/jaymoskingdoms/BoardTerms.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/Decrees.java (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/Economy.java (worth table)
  - kingdoms/src/main/java/com/jaymoskingdoms/Bounties.java (board pin/pull/post)
  - kingdoms/src/main/java/com/jaymoskingdoms/BountyBoard.java
  - kingdoms/src/main/java/com/jaymoskingdoms/Quests.java (ready/turn-in hooks)
  - kingdoms/src/main/java/com/jaymoskingdoms/Upkeep.java (waiting count)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/BoardTermsClient.java (new)
  - bounty board / town notice / decree assets, lang part board-terms.json
  - changes/kingdoms/claude-village-mods-4.md
---
# Village mods patch 4: Board work

**Task:** Andy's eight Board work picks from the village mods pick list (Bountiful ideas, our own code and art): fair pay,
decrees, deadlines, mixed asks, asks that fit the land, rarer paper, progress on the held notice, hand in at the board.

## Plan
- Economy worth table from recipes and rarity; errands and board work paid by what they ask.
- Decree item (smiths', hunters', farmers', war), up to 3 per board, steering posts; rulers post from shortfalls
  (food, stores, Upkeep's waiting repairs, war, attacks); players earn one from 4-5 star work.
- Notice terms per taken quest (player attachment): deadline + early bonus + missed rep loss, second ask + goods reward.
- Paper by stars: plain, sealed, vellum, gilt (board block and item); the held paper yellows past its early time.
- Held notice paper shows progress on the action bar; a toast when it's ready.
- Use the board with the notice or the goods to hand in when the poster is away.

## Notes
- No new lore paragraph: decrees are a ruler's ordinary order.

## Done
