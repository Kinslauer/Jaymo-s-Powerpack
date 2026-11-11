# Boxing: Hand Wraps and the punch
- **Boxing, first part (Jung's plan, `docs/Boxing-Plan.md`).** Fist gear and the punch are in; nothing teaches a
  style yet, so for now only gamemasters can box (`/powerdebug boxing style|level|status`).
- **Hand Wraps:** two rags or cloths (Clean Rag, Torn Cloth, Hemp Cloth) in the crafting grid. A weak weapon by
  themselves; with a style they are what lets you box.
- **The punch:** with fist gear and a style, a left click inside 2 blocks is a punch for 1.5 at every level, with little
  knockback. Arrow Slit throws a one-two then hooks, Long Guard jab-jab-cross, Bastion the right jab.
- **Levels 1 to 10** make punches quicker (one every 0.8 s down to 0.35 s; the attack indicator shows it) and cheaper
  on feathers (one icon a punch down to four punches an icon), never stronger. From level 8 a boxer's punches land
  faster than the usual hit immunity allows.
- **Gassed:** out of feathers, a boxer punches once every 1.6 s for 45% less (2 at most) until the row is 30% back.
- **Names:** Arrow Slit, Long Guard and Bastion (Jung's names, kept by Andy).
- Classes: `boxing/Boxing` (styles, levels, the punch), `boxing/BoxingGear` (Hand Wraps, the `boxing_gear` tag),
  `boxing/BoxingDebug`. `docs/boxing.md` has the numbers and test steps.
- Not in it: evades, strong attacks, blocks, blur, the combo breaker, looks, cestus and bracers, teachers, HUD, hints.
- **Not built locally, not booted with `runServer`, not played.** The session could not reach the build's download
  hosts; the PR checks are its compile. It adds an item, so it needs a server boot before merging. The Hand Wraps
  texture is a placeholder.
- Review fix: the punch takes the left click only after every other rule (a downed or armless player, a finisher,
  Kingdoms' tag, the fight pit, captives), in a later event phase. Before, a boxer's punch skipped them all.
