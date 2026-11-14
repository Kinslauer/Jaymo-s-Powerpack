# Butcher's Hand - the Survival perk that opens the meat hook
- Andy: "This should be a skill in the survivalist tree" (Kitchen's clean kills and skinning in strokes).
- **Butcher's Hand** (`Skills.Perk.BUTCHERS_HAND`): Survival, Foraging branch, after Forager, 3 ranks, 1 point each, no
  level needed. Andy: "only the skilled can do it at all". Rank 1: you can work a carcass on a meat hook (bleed, head,
  skin, cuts), which gives more than a butcher block. Without it the hook can't be worked. Rank 2 and 3: one skinning
  pull fewer each (3, 2, 1). Rank 3: a mauled or battered kill loses nothing (a burnt hide stays lost).
- `KitchenCompat.skills` hands the ranks to Kitchen's `Hanging.setButcherSkill` by reflection; with an older Kitchen it
  logs a warning and the perk does nothing.
- Last in the enum, so no saved perk moves. The skill web places it by itself (page 0, grows from Forager).
- Compiled with Gradle; not played.
