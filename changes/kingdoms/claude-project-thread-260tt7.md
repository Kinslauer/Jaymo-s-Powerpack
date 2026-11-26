# fire engulfs faster, burns longer, buildings crumble
- **Fire's pace** (Andy, from a clip of a burning house): flames leap to the air beside anything that burns 2.5x as often, so a house is wrapped in fire quickly, and a burning block lasts 3x as long before the fire eats it (`FireBlockBurnMixin`, numbers at its top). Rain and the fire-spread gamerule work as before.
- **Burnt buildings come down**: when a fire burns away a built block (planks, thatch, wool, placed logs), whatever it was holding up comes down as falling blocks, still on fire, the same hold rules as blasts use (`Collapse#burnt`; land and living trees never, natural wood stays with Loose Wood). At most 12 burnt spots wait to be looked at, so blasts keep their share.
- **Smoke where a block goes**: a grey campfire-smoke puff and black soot rise from each block the fire burns away.
- Compiled; not played. The numbers are first guesses.
