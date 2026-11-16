# Wardrobe - clothes, costume and weapons in one screen
- **Wardrobe** (Andy: "a closet or wardrobe block. two blocks tall, like the fridge, and it should allow you to use the
  /mca editor window, costumes and also the YDM weapon master config. combined into the same screen"). A two-blocks-tall
  spruce wardrobe, set like a door (it faces you; either half opens it, breaking either drops it once). Crafted from
  spruce planks round a glass pane and a chest (`PGP / P P / PCP`). Opening it opens one screen with up to three tabs:
  - **Clothes**: MCA's editor held to its clothing page, so a wardrobe changes clothes and nothing else (Andy: "they
    should only ever change clothes not the looks or anything else from the editor"). It follows MCA's setting for
    `/mca editor`: operators, or any player when MCA's config allows the full or limited player editor.
  - **Costume**: the Costume Table's designer, only once a Costume Table has gone into the wardrobe (Andy: "combine the
    table with the wardrobe either by craft or right clicking the wardrobe with the table in hand"): use the table on a
    set wardrobe, or craft a wardrobe with a table (shapeless). The wardrobe keeps it when broken and set again. Only a
    wardrobe with a table counts as a table for sewing and taking off.
  - **Weapons**: YDM's Weapon Master settings (where each carried item hangs on you); left out if Weapon Master isn't
    installed.
  Each tab is that mod's own screen, whole, with the same tab row over it, so nothing is squeezed; switching tabs keeps
  what was set (the clothes are sent as MCA's Done does, Weapon Master writes its config). Door sounds on open and close.
  The door fronts and item sprite are new art (`tools/gen_wardrobe.py`); sides and top are vanilla spruce planks.
- Classes: `costume/WardrobeBlock` + `WardrobeClothesRule`, `client/wardrobe/WardrobeClient` + `WardrobeClothes`;
  `CostumeNet` accepts a wardrobe with a table as a table, `CostumeScreen` shows the tabs in its header when opened from one.
- Compiled; not played. The tab rows over MCA's clothing page and Weapon Master's panel are placed from their code, not seen.
