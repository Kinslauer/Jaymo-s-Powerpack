# Plan: class levels and class trees (2026-11-17)

Source: the vault's `Inbox/class-trees-plan.md` (the full design, kept as written). Pick page:
https://claude.ai/artifact/2SkYD55StgJfJik4QdXBSL (db doc `picks/andy`). Andy ticked 92 of 93 rows; the one row not
picked is **Clockwork Scope** (`ct-bench-scope`), so the Workshop bench has no scope.

## Andy's notes
- Page note: "There should be an option to spawn without a class and learn one later." A **No class yet** choice on the
  class step of the life story; a classless player has no class key, no class tree tab (it shows how to find a teacher
  instead), and learns a class later from that class's teacher (`ClassTeachers`) at level 0. Once learnt it is the same
  as having picked it at the start.
- Speed Load (`ct-mm-load`): "They should animate a spin or flourish." The instant reload plays a gun spin.
- Last Rites (`ct-cle-rites`): "Villagers can do this too." Village priests (and clerics among MCA folk) read rites over
  the village's dead before burial, so those dead never rise as Unburied.
- Fox and Stag (`ct-dru-foxstag`): "Use the vanilla fox and existing deer." The shapes reuse the vanilla fox model and
  the deer already in the pack, no new beast models.
- Pack Leader (`ct-dru-pack`): "You should grow larger than their model as well." In a beast shape with Pack Leader you
  are drawn and sized a little larger than the beasts that follow you.

## Patches (one branch, a commit per patch)
1. **Class levels** (framework): `ct-level`, `ct-points`, `ct-forks`, `ct-practice`, `ct-second`, `ct-respec`, `ct-tab`,
   `ct-move`, `ct-xp`, and the No-class-yet start (page note). Level and XP per class beside the pillars, class points,
   crossroads, the K screen's class tab, the old class pages (Rogue's Arts, Spellcraft, Sorcery and Forbidden Arts,
   Archery, Warfare) moved into the trees with points refunded and existing characters migrated.
2. **Sorcerer** (`ct-sor-*`) and the **Thief, Mage and Ranger forks** (`ct-thief-fork`, `ct-mage-fork`, `ct-ranger-fork`).
3. **The Workshop**: Marksman (`ct-mm-*`, Speed Load's flourish), the bench (`ct-bench-smith`, `-upgrades`, `-trick`,
   `-kit`; no scope), Tinkerer (`ct-tink-*`, including the tier eye).
4. **Warlord and Brute** (`ct-wb-*`): Composure and Rage gauges and the mirrored trees.
5. **Rider** (`ct-rid-*`).
6. **Monk and Paladin** (`ct-monk-*`, `ct-pal-*`): Catch an Arrow and Lay on Hands as the starting moves.
7. **Cleric, Druid and Warlock** (`ct-cle-*` with villagers' last rites, `ct-dru-*` with vanilla fox, pack deer and the
   larger Pack Leader, `ct-war-*`).

## As built
### Patch 1: class levels (framework)
- `ClassTrees` holds level and XP per class in the pillar progress map (`class_<key>`), level cap 25, a level costs
  30 + 15 x level XP, one class point per level, a milestone fanfare every 5 levels. Second class: XP at
  `Classes.SECOND_SHARE`, ranks work at that share (`ClassTrees.strength`).
- Trees are laid out in `trees/` (one file per class, `TreeLayout` calls them); this patch fills the moved pages:
  Thief (Rogue's Arts), Mage (Spellcraft, Arcane/Fire/Frost columns), Sorcerer (Sorcery, Forbidden Arts), Ranger
  (Archery), Warlord and Brute (Warfare). My calls: Mortal Strike is in both the Warlord and Brute trees; Throw Net
  (Warlord) and Bear Trap (Thief) are shared with the Ranger. `RpgSeriesGate` follows the same owners.
- Migration on join: a moved rank owned by a held class is paid from that class and the class's XP is raised to cover
  it; ranks no held class owns are removed and their pillar points come back; taught ranks stay free.
- Respec gives class points back and keeps the level (ranks are counted, not stored as spent); the Writ wipes both.
- XP sources wired now, every class: see `changes/kingdoms/claude-project-thread-kb4272.md`. Practice (`ct-practice`)
  has its hook (`ClassTrees.practice`); each class's starting move takes it in that class's patch. Cleric rites XP
  comes with Last Rites (patch 7). Mage "spells that land" is counted as magic damage dealt.
- No class yet: youth answer "Drifted" and a "No class yet" button on the class screen. Classless players learn a first
  class from **any villager of that class's trade** (the teachers' trades list, `SecondClass.offers`) for 10 silver,
  rather than only from a named master, so it can be found in any village.

### Patch 2: Sorcerer, and the Thief, Mage and Ranger forks
- **Sorcerer** (`Sigils`, `Spellcraft`, `trees/SorcererTree`). A tap of J throws the **sigil** (`ct-sor-sigil`): a glyph flies
  off the blade (or the open hand) until it meets something and bursts with the element through `Spellcraft.strike` (fire
  alights, ice freezes, lightning chains, earth staggers, air launches, water douses, and puts out fire blocks in the burst).
  Damage 3 x practice (`ClassTrees.practice`, so it grows with the level), the old bolt's mana price, a 1.5 s wait. It
  replaces sneak+J's bolt, which is gone. **Sigilcraft** (3 ranks): +0.75 burst radius, +6 blocks of flight, -15% wait a
  rank. **Imbue** (level 5) is held now: holding J with a blade in hand and nothing of the element to draw soaks the blade
  for 2 minutes. **Ward Sigil / Twin Imbue** (10): sneak+J lays the sigil on the ground (two at once, a minute each; bursts
  at 1.5x under the first foe) / the off hand's blade takes the same imbue. **Elemental Burst / Lingering Imbue** (15): J
  held on a blade still burning ends the imbue in a blast round you (8 damage scaled by what was left) / imbues last twice
  as long. **Absorbing** is the old Sorcery perk renamed (absorbing, the barrage, and the element's own workings: Storm
  Ring, Bolt Step, Bulwark, Tide Pearl, Harrying Gale, death's and blood's keys); **Forbidden Arts** stays after it. **Etched
  Blade** (top, 20): while imbued, every 4th combo link throws a free sigil (hooked into `CombatArts`' chain).
- My calls: the old Sorcery perk stays the save key (renamed "Absorbing") so nobody loses ranks; a Sorcerer who had it before
  trees keeps the Imbue free (`ClassTrees.keptIf`, a kept rank survives a respec). Restoration's keys (Mend etc.) are its
  starting move instead of a damaging sigil, and stay free; death's tap still raises a body (Andy's earlier call), so a
  Death Sorcerer's sigil is on sneak+J. Wild bloodline sigils take a random element. Sorcerers who don't absorb (the wild
  bloodline, or Absorbing not learned) now send a tap/hold to the server too, so the hold can imbue.
- **Thief** (`ClassForks`): **Assassin** (sneak attack on the unaware: blow x2.5 + 4 instead of x1.5 + 2) or **Acrobat**
  (climbing 1.6x as fast by a mixin on the climb, +4 safe fall, fall damage halved, and +0.5 step height at a run, so a
  block is vaulted in stride).
- **Mage**: **Evoker** (magic, blasts and fiery non-arrow shots from the Mage hit 1.3x, and never hurt players, townsfolk,
  golems or their own beasts unless those are after them) or **Warder** (Warding, Sanctuary and Bulwark cast from the staff
  last 1.5x; Warding's absorption a step stronger).
- **Ranger**: **Hunter** (+25% on the favoured foe, its trail read 1.5x as far) or **Beast Master** (sneak+J looking at a beast
  you tamed bonds it; it gains health, bite and size with your Ranger level, at the second-class share for a second class).
- Gaps: the Acrobat's "vaulting" is a step up at a run, not a faster version of Jaymo's Powers' own vault (that lives in
  Powers' parkour; changing it would mean a Powers hook). The Warder's longer wards are our own wards; the RPG Series' Frost
  Shield is the Wizards mod's spell and isn't lengthened.
- Lore: the sigil is the canon's typed "sigil" (a working laid as a circle of marks); nothing new to place.
