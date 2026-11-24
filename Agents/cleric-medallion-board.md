---
status: running
task: cleric-medallion-board
branch: claude/cleric-medallion-board
with: local_29ede7b7-7e11-4e20-8506-df7674edc787
pr: 2060
mods: kingdoms
versions:
started: 2026-11-23
owns:
  - kingdoms/src/main/java/com/jaymoskingdoms/MedallionBoard.java (new)
  - kingdoms/src/client/java/com/jaymoskingdoms/client/MedallionClient.java (new)
  - kingdoms/src/main/resources/assets/jaymoskingdoms/lang/parts/medallion.json (new)
  - kingdoms/src/main/java/com/jaymoskingdoms/Spellcraft.java (castStaff takes the spell)
  - small guards in FormsClient.combatMode and ArtKeysClient (board up: number keys are the board's)
---
# Cleric's medallion board

**Task:** Andy: the Cleric raises the medallion (holy symbol) with a key, the number keys become a 3x3 board of sigils, a pattern casts a spell; otherwise the medallion in the offhand quick-casts the bound spell.

## Plan
- New key "Raise the medallion" (default Y, rebindable; Y is also the Z-Fighter wheel, only for that power). Needs the holy symbol in a hand, Cleric only.
- Board: keys 1-9 as a 3x3 grid read like the hotbar (1 2 3 top, 7 8 9 bottom). Each key lays a sigil; every spell is 4 different nodes, so a full match casts at once and a sequence that starts no known pattern fizzles.
- Patterns shown on the board HUD itself: the known spells listed beside it with their patterns, narrowing as you enter nodes.
- Bind: finish a pattern while sneaking = bind it to the medallion (the spell the symbol is turned to). Quick cast = the existing tap of the symbol, from either hand.
- Others see: arms raised (Casting RAISE gesture), a sigil circle at the feet, a small sigil (the medallion) before the chest, a rune at each node in the air and strokes linking them.
- Board-only "powerful" spells: proposal only, waiting for Andy.

## Notes
-

## Done
PR #2060 (Auto-fix on). Numpad board with nine sigils (pad layout), spells + Bless/Sanctify/Turn Undead + four great prayers (Hallowed Ground from the start, the rest by favour tier 2/3/4), sneak to bind spells, clash pop-up for the medallion keys. Not played.
