# 0.10.0: Vampires and Werewolves (plan)

All of it is our own work inside Jaymo's Kingdoms (no other mod's code or art). It ships as pack 0.10.3 / Kingdoms 0.7.0;
each stage lands on the same draft PR, and Andy can merge after any stage.

## Stage 1: the Elder and the spread of vampirism (building now)
- **One Elder Vampire per world.** From day 6, an ordinary grown villager in a settlement near a player quietly becomes
  the Elder. By day he is just another villager with his own name, job and family. At nightfall he hunts: the nearest
  person he can reach (villagers, guards, lords, kings: he stops at nothing; never children), holds them and drinks
  until they're dead, then looks for the next one. He's tougher than any villager (40 health, heals as he feeds).
  If he's slain, another Elder may come, but not for 30 days.
- **The Elder's bat form** (Andy): prey more than 12 blocks off (he looks up to 48), he bursts into a bat in a puff of
  smoke, flies over the roofs and drops onto them; badly hurt, he flies off 32 blocks to hide. He can't be hurt while
  he's a bat, but a blow to the bat knocks him out of it and he takes it twice over. 30 s between changes; he lands at
  dawn.
- **The bite spreads it.** Anyone bitten who lives through the night (they broke free, the guards drove him off, the
  vampire let go) turns at the next nightfall. Killed outright, they stay dead.
- **What they become** is rolled when they turn:
  - *Feeders* (half): hunt every night until they've fed, and feed to the kill.
  - *Abstainers* (three in ten): fight the hunger. They sleep the night through like anyone, but every night unfed
    makes it worse; after three they snap, bite someone once and let go (and so it spreads).
  - *Hunters* (one in five): hunt their own kind. At night they go after any vampire showing itself and fight it.
- **Being seen.** A vampire out hunting at night shows itself (red eyes, and the blood when it feeds). Guards attack
  one, villagers run from it, the victim's friends fight back. Fire hurts vampires twice over; by day they're weak
  and hide among the living.
- Kingdom folk turn too: a turned guard, lord or king hunts with the same hunger.
- Ops: `/kingdom vampire elder` (the nearest grown villager becomes the Elder), `/kingdom vampire turn <feeder|abstainer|hunter>`
  (the nearest person turns), `/kingdom vampire cure` (the nearest is human again), `/kingdom vampire where` (the Elder's last place).

## Stage 2: lycanthropy and the full moon (built; see the Kingdoms MODLOG for what landed)
- Werewolves among villagers and folk: each its own beast built from the person (big or small, lean or heavy, fur
  from their hair and the climate they're from), a new model and textures of our own.
- They change under every full moon (hooking into Moons: the moon phase, and the Super Blood Moon changes every one
  of them at once), with a grotesque change animation: hunched, bones cracking, limbs lengthening, the face pushing out
  into a muzzle, over a few seconds, with sounds. At dawn they change back, naked and shaken.
- As beasts they hunt anything warm; survivors of a bite carry the curse (rarer than vampirism).
- Werewolves and vampires hate each other. Silver (our silver ore and Tinkering's silver) hurts both.
- The vampires' night look gets the same treatment here: pale skin, red eyes and fangs drawn on the model, not only
  particles (the client render work is shared with the werewolves).

## Stage 3: the player paths (built; what landed is in the Kingdoms MODLOG)
- **Vampire by bite.** A player bitten by any vampire turns at the next nightfall: stronger, faster and seeing in the
  dark at night, a blood bar instead of hunger (feed with a bite key on a held victim), weak in sunlight (burning
  without a helmet at noon), no natural healing by day. Villagers and guards who see you feed know you.
- **Werewolf by ritual.** A ritual of our own under a full moon (default: a wolf pelt, a ring of candles or
  campfires and the blood of a werewolf, standing in the moonlight at midnight). The first moons force the change;
  mastery lets you change at will. Claws, a pounce, a howl that frightens villagers, huge jumps, more health.
- Both work alongside powers, classes and the K tree. A cure for each (a circumstance in the world, as Andy likes).

## Open questions (defaults in brackets, used until Andy says otherwise)
1. Do NPC vampires burn in sunlight? [No: weak by day, so they can hide among the living]
2. Do people a vampire kills rise as vampires? [No: only the bitten who live turn]
3. Does a werewolf's bite spread lycanthropy? [Yes, to survivors, less often than vampirism]
4. ~~Cures?~~ Andy: a rare quest from a priest for both (built: a quarter of clerics know the rite; slay 2 vampires
   or 1 cursed werewolf, then the rite). Andy also: the change tears 30% of each armour piece's durability, then it
   breaks or falls off (built).
5. ~~Can the Elder turn into a bat?~~ Andy: yes (built in stage 1).

## Losing yourself (Kingdoms 0.7.185)
Andy: "I want the vampire and werewolf to have their own system similar to the symbiote takeovers, slowly losing
yourself to the blood or the curse." Built in `Seizures` (the shared hold, client `SeizureClient` + `SeizeInputMixin`),
`BloodFever` and `FeralCurse`. While a hold has you it walks you at its prey; each new movement key press is a heave
against it (fill the bar to break free). Tuning numbers are at the top of each class.

- **Blood fever (vampire players, not dhampirs).** Fever 0-100 climbs unfed (x2 at night, x1.5 starving, faster the
  more you've surrendered; a person hurt within 10 blocks spikes it), each bite takes 22 off. Past the urge line (70 at
  no surrender, 40 at full) with a person in sight, the blood takes you to them and drinks. Fight it (eases the fever,
  -3 surrender, shaking, 2 min reprieve) or give in with U (feeds till the fever breaks, +6 surrender, a minute of
  frenzy). Losing the fight: +4. Surrender stages: 25 thirsting, 50 bloodbound (night Strength II), 75 ravenous
  (players are prey, bites heal double), 100 lost (48 presses to break free).
- **Feral curse (full werewolves, not wolfbloods).** Wildness 0-100: +10 a full-moon change, +6 a change that came on
  its own, +2 at will, +0.6 a minute as the beast (x2 under the full moon), +0.5/+3 a kill (beast/person), ebbs ~2 a
  day as yourself and when you fight the wolf off. Stages of 20: restless (as the beast the wolf hunts beasts and
  monsters on a whim), wild (villagers are prey; badly hurt at night it may tear out, which you can fight down;
  changing back at will fails 30%), savage (players are prey; 30% of nights the change comes on its own; refuses 60%),
  feral (every night; hunts chain kill to kill; no changing back at night), lost (the night's change can't be fought).
- Commands (op): `/kingdom vampire fever [n]`, `/kingdom vampire surrender <n>`, `/kingdom vampire urge`,
  `/kingdom werewolf feral [n]`, `/kingdom werewolf whim`.
