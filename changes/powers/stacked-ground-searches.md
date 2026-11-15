# One Dimension worlds: Scout finds Overworld structures
- `core.Scout` (the Z Fighter chapel's pull) found nothing in a One Dimension world: the stacked level's structure state has no placements and its generator reads the clouds as ground. It now uses the Overworld band's own placements, ground and structure checks (`compat.Stacked.overworldGround`, `searchingOverworld`). An ordinary world is unchanged.
- Built; not played.
