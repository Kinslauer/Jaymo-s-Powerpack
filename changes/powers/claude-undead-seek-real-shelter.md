# Undead seek real shelter from the sun

- **Deadlier sunlight**: zombies, skeletons, strays and other daylight-burning undead now burn whenever any body point (feet, chest, head) is exposed to the Sun's real position - a ray from any body point toward the Sun must be unobstructed. A roof, a cave wall or a solid cliff shades them; leaves, glass, torches and fences let the rays through, so tree shade no longer counts. The ray test uses Kingdoms' SunMoon for the Sun's true position when that mod is loaded, and the game's own sky angle otherwise; nothing burns in the future's ashen sun (FutureSunMixin untouched). Sun rays test multiple points: eye, chest, head, and hitbox corners at feet and head. Burn if any is exposed.

- **Shelter search**: burning undead now actively seek real shelter - caves, underground spaces, or under solid roofs - within a wider radius than before. They path toward the nearest spot the Sun can't reach. If no shelter is in reach, they stay put (they don't dig).

- Not tested in game.
