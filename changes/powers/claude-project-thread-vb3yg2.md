# Tinkering - a part-built fishing rod, the melter, tank, channels, alloyer and foundry, and JEI fills the Crafting Station
- **Fishing Rod** at the Tool Station (after Tinkers' Construct's): a **Bow Limb** for the rod, a **Bowstring** for the line and
  an **Arrow Head** for the hook. Its materials set its wear and it takes modifiers like any Tinkering tool. It casts, charges
  and reels like our other rods. **A metal hook makes it a sea rod** (the stronger line that can land big fish, like the forged
  Metal Fishing Rod); a flint, bone, stone or wooden hook fishes like a plain rod. The tooltip says which. The line and hook
  show cast while the hook is out, and a broken rod cannot be cast until it is mended.
- **JEI and the Crafting Station.** The station is listed under crafting recipes, and a recipe's "+" fills its grid from your
  inventory, as at a crafting table.
- **Seared Melter** (after Tinkers' Construct 3): a one-block smeltery for a small start. Set it over a campfire, fire, lava,
  magma or a lit forge hearth, right-click up to three things that melt into it, and hang a faucet on its side. Hotter fire
  melts faster. It holds twelve ingots and does not alloy. An empty hand takes back what has not melted, then reads the pool.
- **Seared Tank**: holds four blocks of one metal. Pour into it from a faucet or channel above, and out through a faucet on
  its side. An empty hand reads it.
- **Seared Channel**: a trough that joins to the channels beside it and opens over any casting table, basin or tank under
  it. Pour into one end from a faucet and the metal fills each cast along the run in turn.
- **Nether Grout** (magma cream, soul sand or soul soil, gravel) smelts into **Scorched Bricks**, for two more pieces:
  - **Scorched Alloyer**: set over a fire with seared tanks or melters against its sides, it draws an alloy's metals out of
    them and mixes them into its own pool, one alloy at a time. A faucet on a free side pours it.
  - **Foundry** (Foundry Controller, Scorched Bricks, Scorched Glass, Scorched Drain): built and fuelled exactly like the
    smeltery, but it never alloys what it melts, so different metals can share one pool.
- Faucets now pour from a melter or tank as well as a drain, and into a tank or channel as well as a cast. JEI lists the
  melter and foundry under Melting and the alloyer under Alloying.
- Code: `tinkering/smeltery/{SmelteryExtras,MoltenHolder,MoltenEntity,MelterEntity,SearedTankEntity,ChannelEntity,AlloyerEntity}` and
  their blocks, `client/tinkering/MoltenRenderer`; models and textures by `tools/tinkering_assets.py` (`smeltery_extras`).
- Code: `tinkering/ToolType` (FISHING_ROD, Style.ROD), `tinkering/ranged/TinkerRodItem`, `fishing/{FishingReal,HookBait}`,
  `client/jei/TinkeringJei`; sprites by `tools/tinkering_assets.py` (`fishing_rod`), from Tinkers' Construct's (MIT).
- Compiles. Not played: the rod's look in hand, the "+" transfer with items in the station's side inventory, and the
  melter, tank, channels, alloyer and foundry in the world (the metal's level behind the glass, a long channel run).
