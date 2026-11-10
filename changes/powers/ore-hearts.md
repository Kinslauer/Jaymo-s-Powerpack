# ore hearts - permanent half-hearts mined from deep deepslate

- **Heart crystal ore**: a rare ore in deep deepslate drops a heart crystal; using one raises max health by one permanent half-heart, up to ten half-hearts (five full hearts above base), shown quietly as a faint extra run under the health bar.
- **A life in your pocket**: a found heart is lost on death, but each one burns to turn one death aside - your extra sure wake before the cause roll. With none left, the cause roll runs as today, and reincarnation clears the hearts and the extra health.
- **New**: `core.wounds.HeartCrystals` (the hearts attachment, the heart crystal item, both ore blocks, worldgen and loot tables), registered from `JaymosPowers` and listed in `registerAttachments()`. Hooks: `Downed`'s `AFTER_RESPAWN` re-applies the extra health on respawn, and `Mortality`'s death roll burns a heart via `tryBurnHeartOnDeath` to turn a death aside instead of spending one of the three sure wakes (the heart *is* the wake - only one increment).
- **Client**: `HeartHud` draws the faint extra run under the health bar showing the half-hearts held.
- **Compat**: stacks with the `crystal_heart` artifact (both raise max health) - say so in the PR body, and ask Andy if it reads as too much health.
- Not tested in game.
