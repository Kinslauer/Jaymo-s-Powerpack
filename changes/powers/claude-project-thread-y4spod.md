# Wardrobe - looks, costume and weapons in one screen
- **Wardrobe** (Andy: "a closet or wardrobe block. two blocks tall, like the fridge, and it should allow you to use the
  /mca editor window, costumes and also the YDM weapon master config. combined into the same screen"). A two-blocks-tall
  spruce wardrobe, set like a door (it faces you; either half opens it, breaking either drops it once). Crafted from
  spruce planks round a glass pane, a Costume Table and a chest (`PGP / PTP / PCP`). Opening it opens one screen with
  three tabs:
  - **Looks**: MCA's character editor on yourself, the one `/mca editor` opens (General, Body, Traits), for anyone who
    has a wardrobe, whatever MCA's player-editor config says. Costume and Weapons are added to its page row.
  - **Costume**: the Costume Table's designer; the wardrobe counts as a table for sewing and taking off.
  - **Weapons**: YDM's Weapon Master settings (where each carried item hangs on you); left out if Weapon Master isn't
    installed, as Looks is without MCA.
  Each tab is that mod's own screen, whole, with the same tab row over it, so nothing is squeezed; switching tabs keeps
  what was set (the MCA editor saves as its Done button does, Weapon Master writes its config). Door sounds on open and
  close. The door fronts and item sprite are new art (`tools/gen_wardrobe.py`); sides and top are vanilla spruce planks.
- Classes: `costume/WardrobeBlock`, `client/wardrobe/WardrobeClient` + `WardrobeLooks`,
  `client/mixin/wardrobe/WardrobeEditorTabsMixin` (MCA page row); `CostumeNet` accepts the wardrobe as a table,
  `CostumeScreen` shows the tabs in its header when opened from one.
- Compiled; not played. The tab row's place over Weapon Master's panel is worked out from its code, not seen.
