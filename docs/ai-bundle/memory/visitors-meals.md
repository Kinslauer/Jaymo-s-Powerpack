---
name: visitors-meals
description: Kingdoms Visitors (day visitors, Visiting Villagers remade) + Meals (home cooking / cookhouse / cooks / food prices, Chef's Delight remade), PR #625, Kingdoms 0.7.299 / pack 0.17.112
metadata:
  type: project
---
2026-10-28 Andy asked for Visiting Villagers (ARR) + Chef's Delight (MIT but 26.2-only, needs real Farmer's Delight) ideas: visitors to kingdoms, and every villager cooks or buys food, tied into the economy.
PR #625 MERGED 2026-10-28 13:26Z as Kingdoms 0.7.299 / pack 0.17.112; dev-server checked, not client-tested.
- `Meals`: lunch 5000-7000, supper 10000-11800; home cook if a stove is within 6 of bed (raw item = 2 meals), else buys at cookhouse/tavern/bell stalls from the storehouse-fed pot. Price index = want (2/head, min 16) / food stock; realm tax 25% (visitors 100%); daily surplus sale / bread import. Routines' cosmetic LUNCH removed for townsfolk.
- `Professions.COOK` (IS_IMPORTANT in MCA, trades via setOffers each dawn; VillagerTradeMixin moves traded food in/out of storage). New `cookhouse` blueprint for realms (12) and villages (8).
- `Visitors`: attachment jaymoskingdoms:visit; McaVisitorResidencyMixin cancels MCA Residency.tick for them; excluded from Jobs, Economy.recruit, McaBridge.candidate, Production.survey, Emigrants. Commands `/kingdom larder`, `/kingdom visitors now` (op).
Tuning constants top of Meals/Visitors. Related: [[kingdoms-living-economy]], [[farmers-delight-cooking]], [[village-life]].
