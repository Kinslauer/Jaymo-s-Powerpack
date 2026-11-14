# Longsword and claymore take up less of the first-person view
- **Armory**: the longsword and claymore (every material) are drawn at 0.7x their old size in first person, both
  hands. They shrink toward the cross-guard, so the grip stays where the hand is. The longsword's tip now stops a
  little above the middle of the screen instead of near the top. The claymore still looks the bigger of the two and no
  longer reaches the top edge. Third person, the ground and the inventory icon are unchanged.
- `tools/armory_assets.py` keeps the new poses (`FIRST_PERSON`) when the models are regenerated.
- Built; checked in a projection of the first-person view, not played.
