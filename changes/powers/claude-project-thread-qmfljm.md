# Reason look pack replaces Excalibur; art check on PRs
- **Reason look**: the built-in pack that holds the redrawn art (the old "vanilla-style items" pack) is now the pack's own art style, "Reason look", on by default. Repaints of our mods' and other mods' script-made textures go into it, in clean vanilla-plus (Andy, 2026-11-26). Its id stays `vanilla_style`, so a player's choice carries over.
- **Excalibur retired**: `/excalibur`, the Excalibur and Excalibur Fresh Animations downloads, and the graded `excalibur_style` pack (about 43 MB) are gone. Installs that had them on get them switched off once (`PackResourcePacks.retireExcalibur`); the zips stay in resourcepacks/ for the player to delete.
- **Style guide**: docs/Vanilla-Style.md now covers blocks, mobs, worn armour and finish variants (no per-pixel noise, ramps, light from above, variants as palette swaps).
- **Art check**: `tools/art_audit.py`, run by `tools/pr_checks.py`, fails a PR whose added or changed item or block textures look script-made (far more colours than vanilla, soft gradients, half-transparent pixels). Every Reason look redraw passes it.
- Not played; built by CI only.
