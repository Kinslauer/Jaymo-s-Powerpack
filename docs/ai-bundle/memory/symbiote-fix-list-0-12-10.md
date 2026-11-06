---
name: symbiote-fix-list-0-12-10
description: Andy's 2026-10-16 symbiote fix list + heartier meals, PR #301 (pack 0.12.10 / Kitchen 0.4.46): rulings and where they live
metadata:
  type: project
---
2026-10-16 PR #301 MERGED 17:5xZ (pack 0.12.10 / Kitchen 0.4.46; branch claude/project-thread-qkbaht), not playtested. Hold-through rule covers R and G tendrils. Next free: 0.12.11 / Kitchen 0.4.47. Rulings from Andy:
- Sneak+R/G tear-out only if the key is held through the struggle (server checks PowerInput each tick); let go before follow-through = line drops, terrain stays; torn chunks stay on the ground until moved/broken (BlockCluster.keep).
- Wall Crawl tendrils only with Living Armor (WebSymbiote.crawlTendrils); without it, plain Web climb.
- Suit key in the full-bond big body sheds only the big body, Living Armor tendrils stay (Live.armorSlim).
- Tongue flicks after eating a corpse (3 flicks; on the plain suit too, only after a meal).
- Low health = share of max health (Warrior-born has 2 hearts): DEFEND 0.3, DESPERATE 0.5, INSTINCT 0.4.
- Takeover forces camera toward travel; mouse can fight it, it tears back past 40 degrees (wrenchView).
- Meals: everything a bit more, better meals much more (Stomach HEALTH_SCALE/RICH_*, feed() saturation).
