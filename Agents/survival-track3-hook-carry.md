---
status: in-review
task: Survival picks track 3: meat hook click, two-arm lifting, carried block drawn; butchery picks
branch: claude/survival-hook-carry, claude/survival-butchery
with: wounds-lso (track 1, Powers core/wounds + Kingdoms Rangers; not touched here)
pr: 1989, 1993
mods: kitchen, powers
versions: 
started: 2026-11-22
owns:
  - kitchen/src/client/java/com/jaymoskitchen/client/mixin/MinecraftUseMixin.java
  - kitchen/src/main/java/com/jaymoskitchen/net/Payloads.java (HangOn payload)
  - kitchen/src/main/java/com/jaymoskitchen/JaymosKitchen.java (HangOn receiver, ButcherShop.register)
  - kitchen/src/main/java/com/jaymoskitchen/corpse/Hanging.java
  - kitchen/src/main/java/com/jaymoskitchen/butchery/ (ButcherShop, MincerBlock, HeadMountBlock, ButcherDisplay*)
  - kitchen/src/main/java/com/jaymoskitchen/tanning/DryingRack*
  - src/client/java/com/jaymospowers/client/mixin/carryon/
  - src/main/java/com/jaymospowers/core/ArmsFull.java
  - web wings / glider / Z Fighter two-arm call sites
---
# Survival track 3: hook, two arms, carried block; butcher's shop

**Task:** Andy: right-clicking the hook while dragging or lifting a body hangs it; lifting anything bigger than a small
animal takes both arms, so two-handed abilities (web glide) can't be used; a lifted block shows in your hands, first
and third person. Then the Butchery picks (table, skin rack, rotisserie, mincer, decor). Later: people's bodies hang
too (vampires drain them, a deterrent); spit/campfire clicks and Z Fighter two-arm moves fixed.

## Notes
- Hook: Kitchen's client use mixin sent "let go" for every click but a butcher block's, so the server's hook code never
  ran (PR #1910 fixed only the server). Client now walks the view ray for a hook cell and sends HangOn.
- Carried block: Carry On's CarriedObjectRender.draw bails out whenever First-person Model ("firstperson") is loaded.
- Two-arm reading (unconfirmed): a person over the shoulder (FIREMAN) or at a web slinger's side (SHOULDER) gets the
  left arm too; small = non-person no bigger than 0.6 x 0.8 (Kitchen's one-hand size).
- Rotisserie = the existing great spit. Minced beef (2 per beef) kept out of the meat groups so mince can't double meat.

## Done
- PR #1989 (Kitchen + Powers): hook clicks, people's bodies hang and bleed, spit/campfire clicks, both arms
  (ArmsFull: web wings, gliders, Z Fighter kicks-only and no Hammerlock), Carry On blocks drawn. CI green.
- PR #1993 (Kitchen + Powers): butcher block step-by-step, skin rack, mincer + mince, display, head mount, barrel of
  bones. Lore (new) "The butcher's shop". CI running at hand-off.
- Not played: any of it.
