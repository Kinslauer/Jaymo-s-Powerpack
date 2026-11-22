---
status: stalled
task: Survival picks track 3, part 1: meat hook click, two-arm lifting, carried block drawn
branch: claude/survival-hook-carry
with: wounds-lso (track 1, Powers core/wounds + Kingdoms Rangers; not touched here)
pr: 1989
mods: kitchen, powers
versions: 
started: 2026-11-22
owns:
  - kitchen/src/client/java/com/jaymoskitchen/client/mixin/MinecraftUseMixin.java
  - kitchen/src/main/java/com/jaymoskitchen/net/Payloads.java (HangOn payload)
  - kitchen/src/main/java/com/jaymoskitchen/JaymosKitchen.java (HangOn receiver)
  - src/client/java/com/jaymospowers/client/mixin/carryon/
  - src/main/java/com/jaymospowers/core/ArmsFull.java
  - web wings / glider BOTH_ARMS call sites (WebPower.onWings, WebSwing, AercloudGliderItem, ParagliderItem)
---
# Survival track 3, part 1: hook, two arms, carried block

**Task:** Andy: right-clicking the hook while dragging or lifting a body hangs it; lifting anything bigger than a small
animal takes both arms, so two-handed abilities (web glide) can't be used; a lifted block shows in your hands, first
and third person. (Plan: `docs/Survival-Picks-Plan.md` track 3, in the minecraft-mod-research worktree.)

## Plan
- Hook: Kitchen's client use mixin sent "let go" for every click but a butcher block's, so the server's hook code never
  ran (PR #1910 fixed only the server). Client now walks the view ray for a hook cell and sends HangOn.
- Carried block: Carry On's CarriedObjectRender.draw bails out whenever First-person Model ("firstperson") is loaded.
  A Powers client mixin lifts that gate (keeps it only for the hands path while First-person Model draws the body).
- Two arms: one "both arms busy" check (Powers lift other than a part/drag, a Kitchen corpse not held in one hand, a
  Carry On block) gates web wings, aercloud glider, paraglider.
- Butchery picks (table, skin rack, rotisserie, mincer, decor): second PR.

## Notes
- Hooks still take only animals' carcasses (Hanging.hangable); a person's body isn't hung.
- Great spit / campfire clicks while carrying are still swallowed by the same client mixin (server branches in
  JaymosKitchen's UseBlockCallback look unreachable); not changed here, reported.
- Two-arm reading (unconfirmed): a person over the shoulder (FIREMAN) or at a web slinger's side (SHOULDER) gets the
  left arm too; small = non-person no bigger than 0.6 x 0.8 (Kitchen's one-hand size). ZFighter's two-arm moves untouched.

## Done
PR #1989 (Kitchen + Powers), Auto-fix on, built by CI only. Not played: the hook click, the left-arm angles, the
carried block under First-person Model. Butchery picks (bu-table, bu-skin-rack, bu-rotisserie, bu-mincer, bu-decor)
not started: second PR.
- 2026-11-22 10:08 No sign of work for 30 min: marked stalled (whoever ran it stopped).
