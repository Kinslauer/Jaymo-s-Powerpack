# Undead seek real shelter from the sun

<<<<<<< HEAD
- **Deadlier sunlight**: zombies, skeletons, strays and other daylight-burning undead now burn whenever any body point is exposed to the Sun's real position - testing feet, chest, head, and hitbox corners. Burn if any is exposed. Sun rays test multiple points toward the Sun's true position (Kingdoms' SunMoon when loaded, game's sky angle otherwise). Nothing burns in the future's ashen sun (FutureSunMixin untouched).
=======
- **Deadlier sunlight**: zombies, skeletons, strays and other daylight-burning undead now burn whenever any part of them is in the Sun - feet, chest or head, not just the eyes. A roof, a cave wall or a solid cliff shades them; leaves, glass, torches and fences let the rays through, so hiding under a tree no longer works. When Kingdoms is loaded the test follows the Sun's real orbit; otherwise it uses the sky's angle. Nothing burns under the future's ashen sun.
>>>>>>> 84fc2fb96f05d55e7ae60ce68c2d6c2879a02c1d

- **Shelter search**: burning undead now look for real shelter - a cave, anywhere underground, or under a solid roof - farther out than before, and they keep walking and searching until they find it. They don't dig.

- Not tested in game.
