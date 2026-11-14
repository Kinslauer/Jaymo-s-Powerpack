# Survival level overflow on the skill meter
- **Fixed "Survival LV. -35791396"**: a downed player's finishing blow (endless damage) gave Survival experience for the damage taken, which overflowed and saved the pillar's experience far below nothing; the meter then crawled up from there one level a frame. Damage experience now counts only up to your max health, grants add in longs and fill the pillar at most, and a save already below nothing is mended on load (the wrapped amount is added back, so the experience you had is restored).
- Classes: Skills (Progress mend, grant), SurvivalSkill.
- Built; not played.
