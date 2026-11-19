# camps sound the alarm with a horn

- **The runner**: when anyone of a barbarian, goblin or orc camp (raiders, chief, casters, builders) is struck on the camp's ground (its palisade, or a grown greenskin camp's outer wall, plus 24 blocks) by someone not of the camp, the one struck drops the fight and runs for the fire, shouting. If it's already down, the nearest of its own who saw it runs instead. If the runner is cut down on the way, the next one struck runs.
- **The horn**: at the fire (or after 20 s of running, wherever it got to) the runner raises a goat horn and blows the call (heard about 96 blocks off) with a "to arms" shout. Struck at the fire, the horn goes at once.
- **On alert**: every fighter of the camp (and its brave) goes for the nearest foe on the camp's ground: anyone who struck one of the camp, and any monster that walks in. With no foe in sight they patrol, each walking to a fresh spot round the palisade every 8 s. Builders and the timid stop work and keep to the fire.
- **Standing down**: once no foe has been on the ground for 30 s (and the alert has lasted at least 45 s), someone says it's quiet and the camp goes back to its day. Fights inside a camp (orc brawls) never sound it; illager camps and pirate coves keep their own ways.
- New class `CampAlarm` (tuning at its top), lang part `camp-alarm.json`. Compiled with Gradle; not played in game.
