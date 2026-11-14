# Tinkering - a part-built fishing rod, and JEI fills the Crafting Station
- **Fishing Rod** at the Tool Station (after Tinkers' Construct's): a **Bow Limb** for the rod, a **Bowstring** for the line and
  an **Arrow Head** for the hook. Its materials set its wear and it takes modifiers like any Tinkering tool. It casts, charges
  and reels like our other rods. **A metal hook makes it a sea rod** (the stronger line that can land big fish, like the forged
  Metal Fishing Rod); a flint, bone, stone or wooden hook fishes like a plain rod. The tooltip says which. The line and hook
  show cast while the hook is out, and a broken rod cannot be cast until it is mended.
- **JEI and the Crafting Station.** The station is listed under crafting recipes, and a recipe's "+" fills its grid from your
  inventory, as at a crafting table.
- Code: `tinkering/ToolType` (FISHING_ROD, Style.ROD), `tinkering/ranged/TinkerRodItem`, `fishing/{FishingReal,HookBait}`,
  `client/jei/TinkeringJei`; sprites by `tools/tinkering_assets.py` (`fishing_rod`), from Tinkers' Construct's (MIT).
- Compiles. Not played: the rod's look in hand, and the "+" transfer with items in the station's side inventory.
