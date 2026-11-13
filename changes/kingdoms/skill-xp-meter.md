# experience meter - a bar that drops in when a skill or a job gains experience
- Andy: "the meter dropping in and then back out after filling with the earned xp ... it should animate as grinding or
  burning through whenever it increases, and play a low toast at the level up."
- **The meter** (`client/hud/SkillMeter`): whenever Survival, Stealth, Strength, Scholar or one of your jobs gains
  experience, a bar drops in at the top middle of the screen with its name and level ("Strength Lv.7", "Apprentice
  Butcher"), in the track's colour. The fill burns along to the new amount: a white-hot front a pixel proud of the bar,
  sparks thrown back off it, the fresh part glowing and cooling. It waits a moment, then slides back out. More
  experience while it's up keeps it there; another track waits its turn. It sits under the boss bars when any show
  and hides with the HUD and during films.
- **Level up**: the bar flashes and starts again from empty, and a small toast comes up under it ("Strength reaches
  level 8 / A point to spend [K]"; for a job, "Promoted: Journeyman Butcher" and the new perk) with vanilla's toast
  sound pitched low and quiet. This replaces the action-bar flash, level-up chime and gold chat line an ordinary skill
  level had, and the chime of a job promotion (its chat line stays). Every fifth level and the top of a pillar keep
  the big fanfare, and the meter only flashes for those.
- Nothing new is sent or saved: the meter watches the skill and career attachments the client already gets. Changes in
  the first 3 s after joining, respawning or changing world count as the save arriving, not as gains.
- Compiled; not played. Powers' mastery and Z Fighter training don't show on it yet (`SkillMeter.show` takes any track).
