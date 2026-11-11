# Giants throw trees they run into, mob giants too
- **Fix**: a giant's sprint now checks for trees just ahead of its body from its feet up, so a tall giant no longer misses trunks at its feet. Before, it checked only around eye height and along its gaze.
- **Mob giants throw trees too**: a mob giant (a villager's wild Great Ape) counts as running when it moves fast enough over the ground (`GiantTrample.RAM_SPEED`). Mobs almost never sprint, so before this only player giants threw trees.
- A tree too big to lift is pushed over, as walking into it does. Without Kingdoms the trunk breaks and only its logs fly ahead (`GiantTrample.ram`, `GiantTrees.ram`, tuning in the `RAM_*` constants).
- Not tested in game.
