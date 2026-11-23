---
status: in-review
task: weapons on the body in fixed places; four flintlocks (two hips, two chest); Kenway loadout
branch: claude/body-weapon-homes
with: 
pr: 2044
mods: powers
versions: 
started: 2026-11-23
owns:
  - src/main/java/com/jaymospowers/armory/BodyWeapons.java
  - src/client/java/com/jaymospowers/client/armory/BodyWeaponsLayer.java
  - src/client/java/com/jaymospowers/client/mixin/weaponmaster/WeaponMasterShownMixin.java
---
# Body weapon homes

**Task:** Andy: "weapons should always go to a specific place on the body unless the player toggles otherwise", and
four flintlocks shown (two on the hips, two on the chest), like Edward Kenway with his two swords and pistols.

## Plan
- Server works out each player's homes from the hotbar (by weapon kind) and syncs them to everyone.
- Our own layer draws them; Weapon Master's items are switched off for players in fixed mode.
- Toggle on the Character page (H): fixed places / free (Weapon Master as before).

## Notes
- Weapon Master places by hotbar slot index; other clients don't hold your hotbar, so we sync our own list.
- Hidden blade already exists in Kingdoms (worn on the wrist, drawn by HiddenBladeClient).

## Done
PR #2044. Swords at the hips, pistols 2 on the chest strap + 2 in the front sash, long guns/bows/spears crossed on the
back, shield on the back, tools at the small of the back; up to 6 shown (all with a tool belt). Toggle "Weapons: set
places / free" on the Character page. Built by CI only; not played, every position unseen (tune `HOMES`).
