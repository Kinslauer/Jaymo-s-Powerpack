# Giants running into trees knock them flying
- **Trees go flying**: when a giant runs into a tree, the tree doesn't just topple. It's knocked off its roots whole and flies on ahead, tumbling end over end, faster the bigger the giant is. With Jaymo's Kingdoms this uses the same uprooting as a tornado (`Trees.uproot`). A tree too big for the giant to send flying topples as usual. Without Kingdoms the trunk breaks and its logs fly ahead (`GiantTrample.ram`, tuning in the `RAM_*` constants).
- **Fix**: the check now looks just ahead of the giant's body, from its feet up. Before, it checked only around eye height and along its gaze, so a tall giant missed trunks at its feet.
- **Mob giants too**: a mob giant (a villager's wild Great Ape) counts as running when it moves fast enough over the ground (`RAM_SPEED`). Mobs almost never sprint, so before this only player giants threw trees.
- Not tested in game.
