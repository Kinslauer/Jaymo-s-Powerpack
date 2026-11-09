# dirty water gives a slow parasite, hot chocolate cures it

- **Dirty water** (river/pond bottles and bank sips) no longer turns your stomach with nausea: it gives a parasite instead - slow damage at random ticks, survivable, a reason to boil water rather than a death sentence.
- **Hot chocolate** (an existing Kitchen drink) clears the parasite when drunk.
- Kitchen: `climate/Thirst.java` applies the parasite on dirty drinks and cures it on hot chocolate in `consumed()`, new `climate/ParasiteEffect` (a Kitchen mob effect: HARMFUL, random tick damage, never lethal), registered in `climate/Climate.java`, new `data/jaymoskitchen/damage_type/parasite.json`, lang keys `effect.jaymoskitchen.parasite` and `message.jaymoskitchen.thirst.cured`.
- Built with `./gradlew buildKitchen` and `tools/pr_checks.py` pass; not tested in game.
