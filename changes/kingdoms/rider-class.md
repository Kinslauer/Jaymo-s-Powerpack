# Rider class overhaul - a horse from day one, bareback, lance charge, races
Andy: "noone plays it as is", then "add everything you thought up, and everything in this wheel should be available to riders immediately, not exclusively".
- **The horse wheel, at once**: a Rider's own horses heed every wheel order (let be, follow, stay, wander, set home, go home), the roster's Call and the whistle from the day they're theirs, at any trust. Everyone else still earns the trust as before. (`Riders#heeds`, `Herding`, `Bonding`, `HorseWheelScreen`)
- **A horse of their own**: picking Rider gives a saddled courser, destrier or palfrey that already trusts you (once per player, so a reborn Rider doesn't get a second).
- **Breaking horses**: a Rider tames a wild horse or llama in a fraction of the tries (half its most temper counts on every roll), it starts at 50 trust (30 for others), and trust grows 2.5x as fast (was 1.5x).
- **Bareback**: a Rider rides and steers any tamed horse, donkey, mule, llama or camel without a saddle (`MobSaddleMixin`). Everyone else still needs one.
- **Lance charge**: a lance, spear, pike or trident from the saddle hits up to 80% harder the faster the mount is going (nothing under a third of its top pace), with a shove. A Rider's horse also builds its gallop charge 1.5x as fast and runs things down 25% harder.
- **Running mount**: jump at a sprint beside your own horse and you vault into the saddle. A Rider's whistle carries 256 blocks (was 96) and the horse comes faster.
- **Pillion rescue**: in the saddle, use a downed player to pull them up behind you.
- **No foot penalty**: the slower walk is gone; a Rider takes 20% less fall damage instead.
- **Courier pay**: delivery and escort quests and the Courier job's wages pay a Rider 1.5x.
- **Races** (anyone, Riders win more): a stablehand's screen has a Races section, a 160-block sprint and a 320-block long run. Pay 1 emerald's worth on your own horse and a finish post (a column of light) goes up across open land; the action bar shows how far, which way and the time. Gold, silver or just finishing each pay a prize (Riders 1.5x); too slow and it's lost. (`Races`)
- A downed horse is brought round by a Rider twice as fast and with no treat (Powers' side; the `jaymoskingdoms.rider` tag tells it).
- Built; not played. The two new mixins (`Mob.isSaddled`, `RunAroundLikeCrazyGoal.tick`) are only checked at launch.
