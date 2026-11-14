# Butcher's Hand - a Survival perk for skinning and rough kills
- Andy: "This should be a skill in the survivalist tree" (Kitchen's clean kills and skinning in strokes).
- **Butcher's Hand** (`Skills.Perk.BUTCHERS_HAND`): Survival, Foraging branch, after Forager, 3 ranks, 1 point each, no
  level needed. Each rank takes one knife pull off skinning a carcass (3 pulls with no ranks). Rank 3: a mauled or
  battered kill loses nothing (a burnt hide stays lost).
- `KitchenCompat.skills` hands the ranks to Kitchen's `Hanging.setButcherSkill` by reflection; with an older Kitchen it
  logs a warning and the perk does nothing.
- Last in the enum, so no saved perk moves. The skill web places it by itself (page 0, grows from Forager).
- Compiled with Gradle; not played.
