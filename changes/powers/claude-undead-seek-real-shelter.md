# Undead seek real shelter from the sun

- **Deadlier sunlight**: zombies, skeletons, strays (and anything else that burns in daylight) now burn when any body point (feet, chest, head) is exposed to the Sun's real position - a ray from any body point toward the Sun must be unobstructed. A roof, a cave wall or a solid cliff shades them; leaves, glass, torches and fences let the rays through, so tree shade no longer counts. The ray test uses Kingdoms' SunMoon for the Sun's true position when that mod is loaded, and the game's own sky angle otherwise; nothing burns in the future's ashen sun (FutureSunMixin untouched). Sun rays test multiple points: eye, chest, head, and hitbox corners at feet and head. Burn if any is exposed.

- **Shelter search**: burning zombies, skeletons and strays now look for real shelter - a cave, somewhere underground, or under a solid roof - within a wide radius (wider than vanilla's 10 blocks) and path toward the nearest spot the rays can't reach; they stay put when nothing is reachable (no digging - Andy said to ask before that). Search runs once when mob starts burning, then backs off 200+ ticks after an empty search. Keeps a found spot until the sun ray reaches it.

- Built; not played in game. Not tested in game.
