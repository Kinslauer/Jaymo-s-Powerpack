# Boxing: the cestus and the bracers
- **Cestus** (leather, iron, steel): fist gear that makes a styled punch hit harder, 2, 2.5 and 3 against 1.5 in Hand
  Wraps. **Bracers** (leather, iron, steel): fist gear for blocking; they punch like wraps, and their block comes with
  the blocks patch. Like Hand Wraps, all of it lets a boxer use their style.
- **Making them:** leather ones in the crafting grid round Hand Wraps (2 leather for the cestus, 4 for the bracers).
  Iron and steel ones are forged like other metal weapons: Cestus Studs or Bracer Plates and a Tool Binding at the
  Tool Station. No gold or diamond.
- Classes: `boxing/BoxingGear` (the items and `punchBonus`), `boxing/Boxing` (the bonus in the punch),
  `tinkering/WeaponParts` (four new kinds). Art from `tools/boxing_assets.py` (placeholder, drawn for this).
- **Not built locally, not booted with `runServer`, not played.** It adds ten items (six gear, four heads), so it
  needs a server boot before merging. Untested in particular: the whole forging route for the new heads.
