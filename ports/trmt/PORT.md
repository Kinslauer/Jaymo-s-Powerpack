# The Roads More Travelled, ported to 26.3 for Jaymo's Powers

**The Roads More Travelled [TRMT]** is by **milkucha** (https://github.com/milkucha/trmt,
https://modrinth.com/mod/the-roads-more-travelled), licensed **CC BY-NC 4.0** (see `LICENSE`). This folder is a port
of its `26.1.x-fabric` branch at commit `02ce79f` (TRMT 0.5.1) to Minecraft 26.3, made because there is no official
26.3 build yet. It ships in Jaymo's Powers' free modpack, unchanged in what it does, with this credit; nothing here
is for sale. Once milkucha releases for 26.3, the pack switches to the official jar and this folder goes.

## Changes from the original
- Build: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.162.0+26.3, Loom 1.18-SNAPSHOT; version
  `0.5.1-26.3-jaymo.1`; build output on E:\ on the home PC; publishing block and the repository's stray Loom class
  files left out; Gradle wrapper from Jaymo's Kitchen.
- `fabric.mod.json`: depends on `minecraft ~26.3` and `fabricloader >=0.19.5`.
- Source: any API changes from 26.1 to 26.3 are listed below as they're made.
  - `HoeItemMixin`, `ShovelItemMixin`: 26.3 has no `HoeItem`/`ShovelItem` (tools are plain `Item`s with a block
    transformer), so both hook `Item.useOn` and check `ItemTags.HOES` / `ItemTags.SHOVELS`; sounds take `.value()`.
  - `TRMTPotions`: Fabric API's `FabricPotionBrewingBuilder` is gone (brewing is data-driven in 26.3), so the Potion of
    Lightness recipes (awkward + feather, + redstone for long) are JSON in `data/trmt/recipe/brewing/`, for potions,
    splash and lingering potions.
  - `DisconnectedScreenMixin`: the "get the matching version" button opens its link with `Blaze3D.openUri` (moved
    off `Util.OS` in 26.3).
