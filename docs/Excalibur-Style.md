# Excalibur look (retired)

From 0.19.65 the pack could switch on Excalibur (by Maffhew, CC BY-NC-ND 3.0), its Fresh Animations patch (Ruseki and
GrandPappyJay) and our own textures graded toward its colours (the built-in `excalibur_style` pack, `/excalibur on`).
On 2026-11-26 Andy retired it for the pack's own **Reason look** ("make a resourcepack for use alongside the mod in
place of excalibur"), aiming for clean vanilla-plus: see `docs/Vanilla-Style.md`.

- Excalibur and its patch are no longer fetched; `/excalibur`, `ExcaliburLook`, the `excalibur_style` pack (about
  43 MB of graded copies) and `tools/excalibur_style` are gone.
- Installs that had the look on get Excalibur, the patch and `excalibur_style` switched off once
  (`PackResourcePacks.retireExcalibur`, marker `config/jaymospowers-excalibur-retired-v1`). The zip files stay in the
  player's resourcepacks/ folder for them to keep or delete.
- Recrafted (MR_CHOO), which `/excalibur on` used to switch on when a player had it, is the player's own pack and is
  left as they have it.
