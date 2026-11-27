# Hunter's Road 2 - monster kinds and the bestiary
- **Kinds**: entity tags `jaymoskingdoms:kind/dead`, `corpse_eaters`, `brutes`, `beasts`, `spirits`, `cursed`, `people`, filled with vanilla, our mobs and Alex's Mobs (optional entries); other mods join by tag. `MonsterKinds.is(entity, kind)` reads the tag and the states (a vampire that is not a dhampir, a werewolf in beast form are cursed).
- **Bestiary**: after the goblin and orc pages, one smudged page per kind for members you have not met, then a page for each member you have met (it hurt you or you hurt it): a hint, a "how it fights" line after a kill, a "what it fears" line after a sage's lesson (Research creature fears), the herbalist's new "what frightens the things on the road" talk choice, or `Bestiary.learnFear` (for the oils). At most 60 member pages. Goblin and orc pages unchanged.
- **Hooks**: `Bestiary.learnFear(player, type)`, `told`, `toldOfKind`, `toldOfAny`.
- Loading hint 209.
- Classes: MonsterKinds, Bestiary, HerbalistWares, Research, StoryHoldScreen.
- Built; not played.
