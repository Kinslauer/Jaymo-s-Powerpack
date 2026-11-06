# Rivals, your Bane, and combat: the plan (2026-11-06)

Andy asked whether REA (https://github.com/JoasASantos/REA-Prompts) could go past models: Shadow of War's captain
system, Warframe's personal adversaries, and combat. Pick page: https://claude.ai/artifact/9iSWYZCi2TKDJRJW4vBxda
(db `picks/andy`). **Andy picked all 34 rows.** His notes:
- "Chrono and Web both have arkham and shadow of war combat inspirations, can anything be added to those that isn't
  already?" (answered in patch 9).
- On "Vault over a shield": "add armored ogres with shields and different skin colors matching orcs" (patch 8).

## How this follows REA

REA's section 4 (clean-room cloning): write down observed behaviour, cite where each fact came from, mark guesses
UNKNOWN, build from this spec only, and finish by listing what still differs. We don't use REA's binary tools on either
game. Warframe is online with anti-cheat, so reversing its client breaks its terms. Shadow of War's behaviour can be
seen in play, so its executable stays closed.

Both games are commercial and all rights reserved: ideas only. Our own code, names, lines and art. Never use the words
Nemesis, Lich, Sister, Requiem, Parazon or Murmur. Warner Bros. holds a 2021 US patent on its Nemesis system; our version
is built from our own pieces and works differently (see "Differences" at the end). Not legal advice.

## The reference behaviour (what we take, and from where)

| # | Behaviour | Source | Our piece |
|---|---|---|---|
| R1 | Named enemy captains sit in a ranked ladder under leaders; empty places fill over time | Shadow of War, played | `Captains` (tribes) |
| R2 | Captains remember earlier meetings and say so; scars from how they nearly died show on them | Shadow of War, played | Recollections, wound/limb marks |
| R3 | A captain who kills you is promoted; captains fight each other off-screen | Shadow of War, played | `Captains` climb, duels/feasts/ambushes |
| R4 | Strengths, weaknesses and fears, learned from intel | Shadow of War, played | 15 traits, Questioning |
| R5 | Defeated captains can return, sometimes changed by how they lost | Shadow of War, played | returns, Deathless |
| R6 | Captains can be turned; turned ones can betray | Shadow of War, played | `SwornCaptains` |
| R7 | A personal adversary is born from a marked foe you finish | Warframe, public wiki and footage | finishers, Deathless |
| R8 | It takes a share of your rewards while it lives and spreads over territory | Warframe, public wiki and footage | none yet |
| R9 | It can't be killed until you find a three-part key, in order, learned from its followers | Warframe, public wiki and footage | Questioning, Gem Sockets |
| R10 | A wrong key: it survives, gets stronger, and you learn which parts were right | Warframe, public wiki and footage | none yet |
| R11 | Beaten with the key, you kill it or convert it to an ally; its weapon is yours | Warframe, public wiki and footage | kings' weapon heads, SwornCaptains |
| R12 | Freeflow melee: counter prompts, a combo count, takedowns at combo thresholds, gadgets mid-combo | Arkham games and Shadow of War, played | Guard, AttackTells, Chrono Time Flow, Web counters |

UNKNOWN: every exact number in those games (promotion odds, share stolen, combo thresholds, window lengths). We don't
need them. Our numbers are our own, set at the top of each class, and the combat spec (patch 7) measures our pack, not
theirs.

## Patches

Kingdoms unless marked. Each patch is its own PR, merged in order, versions set to master + 1 right before merging.

### Patch 0, rivals for every side (Kingdoms)
- **A shared core:** lift the ladder, traits, memory, returns and climb out of `Captains` into a `Rivals` core that any
  side can use. Tribes keep working exactly as now.
- **New ladders:** barbarian camps, bandit gangs, pirate ships (the captain heads the ladder, mates are his captains),
  illager camps, the necromancers' raised dead, and the demon warlords in the Nether biomes. Each gets bynames, gear,
  ranks and lines of its own.
- **Grow with the world tier:** health, trait count and top rank rise with the Aether Giants' world floor. Early worlds
  keep small ladders.
- **Temperament drives them:** each captain picks lines and choices from his own temperament (proud challenges you,
  greedy sells out his boss, timid sends others first, spiteful goes after your friends).

### Patch 1, more to be (Kingdoms + art)
- **About 30 traits** (15 new), tied to our systems: reads your parries (your Guard windows shrink against him),
  follows blood trails, runs a wolf pack, senses ki, hunts casters, fears guns, executes the downed, sworn to a patron,
  wears bog iron, never sleeps, hates fire mages, a coward who bargains, and more.
- **They learn from how they lost:** on return, the trait that failed him is swapped for its answer (burned: fire-born;
  shot: carries a shield; fled from beasts: stops fearing them). Written in his memory and in what you know of him.
- **Scars you can see:** burned face, eye patch, iron jaw, stitched cheeks, hook hand, wooden leg, picked from the cause of
  his last defeat. Vanilla-style layers over the MCA body, painted from nothing by a `tools/gen_scars.py` script.
- **Signature moves:** rank 4 and up get one move of their own (shield charge, ground slam, thrown axe, grab), each with a
  red glint (AttackTells). Moves come from the Weapon Forms and Better Combat sets we already ship.

### Patch 2, their schemes and yours (Kingdoms)
- **Bodyguards:** high ranks keep one or two lesser captains posted beside them, fighting with them every time. Turn a
  bodyguard and he stabs his boss when the fight starts.
- **Spies in towns:** send a sworn captain to live in a town or camp as a plain villager (no nametag until asked). Enemy
  leaders plant theirs in your realm; a Will staredown unmasks one; a betrayal plays a short film.
- **More ways to fight them:** poison a feast, rig a duel so the loser lives and owes you, frame one captain for
  another's death.
- **Avenge a friend:** a captain who kills a friend on your server gets a notice on the boards; his killer is rewarded and
  gets the friend's head back.

### Patch 3, ground and the ladder screen (Kingdoms)
- **Captains hold ground:** a stretch around each captain's camp. Tolls on its roads, caravans robbed, his men on
  patrol. Kill or turn him and it frees up. A map mark shows a claim once you've learned of it.
- **A ladder screen:** every side's leader, his captains, what you know of each (blanks until learned), who you've sworn,
  and each one's history with you. Drawn with our UiStyle.

### Patch 4, your Bane is born (Kingdoms + Powers)
- **Birth:** some foes carry a faint brand only you can see. Execute one and it rises a day or two later as your Bane.
  One Bane per player at a time. You choose whether to wake one by choosing the kill.
- **Three kinds:** a raised corpse-knight (necromancy), an outlaw warlord who climbed on your name, a demon-sworn one
  from the Nether biomes. Each has its own followers, look and lines. No new realm.
- **Ranks:** it gains a rank, a trait and a better weapon each time it kills or downs you, and leaves a mocking note
  (bird or board). Top rank five.
- **It learns how you fight:** resistant to whatever damage you use most on it.
- **It turns up mid-fight** in its ground, with a speaker-shot film and a line about last time. Never during another film.
- **Friends can help:** anyone on the server can fight it and question its followers; only its owner picks kill or spare.

### Patch 5, it takes from you (Kingdoms)
- **The cut:** while it lives, a share of your job wages, bounty rewards and merchant sales goes to it, and now and then a
  coin from your purse. Beat it and you get it all back with interest.
- **It claims towns and roads:** one more place near you every few days. The more it holds, the bigger its cut. Free a
  place by killing its men there. Each change goes on the town boards.
- **Its hounds:** two or three branded beasts that track you by your blood trail and come when you're alone (wolf
  stalkers' code). Each killed delays its next claim.

### Patch 6, ending it (Kingdoms + Powers)
- **Its followers carry the key:** branded followers turn up in your fights. Questioning one gives up a mark or its
  place in the order. Rough Questions, Quiet Word and Interrogation each get something different.
- **Three marks of eight:** carve the right three, in order, into your weapon's sockets (Gem Sockets). Then your usual
  finisher ends it.
- **Wrong marks:** it shrugs off the blow, laughs, ranks up and is gone. You learn which marks were right.
- **Its weapon:** each Bane carries an element weapon; finish it and its head is yours (found only, Tinkers routes, like
  the kings' heads). Elements from the Sorcerer's list.
- **Kill it or swear it:** a sworn Bane follows you, keeps its traits and temper, and can guard your realm.
- **Its stronghold:** at rank five it builds a hold (overworld or Nether). Storm it with its captains and hounds around it,
  using the kings' seat siege rules.

### Patch 7, combat feel (Powers + Kingdoms)
- **A combat-feel spec from Andy's clips:** `docs/Combat-Feel-Spec.md`. Frames pulled from the Medal clips in project
  files, measuring our windups, glint-to-blow time, parry windows, hit pauses and recovery, each with the clip and time it
  came from. Where it feels off, the spec says what to change and why, and ends with what still differs.
- **Crowds take turns:** only two or three come at you at once; the rest circle, shout and wait. First check how much
  `RangedTactics` and Better Combat already do (UNKNOWN).
- **Counter one, turn to the next:** a Guard parry while others glint turns you toward the next, with a short chain bonus.
- **Brutal kills scare the rest:** an execution in view makes nearby weaker foes run, timid and gloomy first (Frights).

### Patch 8, shields and streaks (Powers + Kingdoms)
- **Hit streak quick kills:** land blows without being hit; at eight, a quick kill works on a foe still standing (needs
  the Executioner perk).
- **Vault over a shield:** jump at a foe behind a raised shield to vault over and land behind him, stunned for a beat.
  Blocked if he's much bigger.
- **(Andy) Armoured ogres with shields:** a new ogre kind in orc camps and ogre holds, in plate with a big shield, skin
  from the same tones as orcs (`GreenskinLife.TONE`). Can't be vaulted (too big); shields come off by Yank, a heavy blow,
  or a stun.

### Patch 9, more for Chrono and Web (Powers), Andy's question

What they already have from Arkham and Shadow of War: Chrono's Time Flow has a combo count that raises damage, six
single counters plus a crowd counter, chained cone parries, vault and throw counters, the barrage, flinch and ragdoll, and
corruption at 50. Web has 18 Spider-Sense counters (9 web, 9 symbiote) with their own animations, the zip kick, Yank and
the taser web, tendril combos and wall slams.

What's missing, and what this patch adds:
- **Takedowns at combo thresholds (both):** at 8 a move that drops one foe straight to downed; at 16, two at once. Spends
  the combo. Chrono: a frozen-time beatdown; Web: a wrap-and-slam.
- **Ground takedown mid-flow (both):** a quick finisher on a downed foe that keeps the combo going.
- **Gadgets mid-combo (Web):** a quick web shot or Yank without breaking the combo. Yank pulls a shield out of a foe's
  hands (useful on the armoured ogres).
- **Shadow-strike blink (Chrono):** mark a foe with a thrown or shot blow, then blink to him and strike, spending combo.
- **Shield stun (Chrono):** a time-slowing flourish that stuns a shield-bearer so you can vault or beat him down.
- **Wall and drop slams (Web):** a counter that throws a foe into a wall pins him there in webbing; one from a perch
  grabs a foe below and strings him up. Check `WebHeldGrab` first: it may already cover part of the perch grab.
- **The combo survives a dodge (both):** a roll or vault over a foe keeps the count.

## Differences from the reference games (REA's last step)
- Captains live across several sides and all their camps, not one region's army; no online vendettas, only your own
  server's friends.
- The Bane's key is carved into sockets on your own forged weapon, and learned by our questioning screen, not from
  pickups.
- The Bane's cut comes from wages, bounties and sales, not mission loot, and its ground is towns and roads on the map.
- Wrong keys teach which marks were right (Warframe teaches its own way; not copied).
- Combat stays Better Combat + Guard + our powers; the new pieces add to it and never replace it.

## Decided
Andy picked all 34 rows (2026-11-06).

## Done
- Patch 0 (Kingdoms 0.7.614): `Rivals` core; ladders for barbarian warbands, illager hosts and pirate coves (camps), the
  risen dead and the Hornkin (rising from the land); world-tier growth; temperament lines and choices.
- Patch 1 (Kingdoms 0.7.616): 15 new traits and 4 signature moves (`CaptainWays`), learning from how they lost, six
  visible marks (`CaptainMarks`, `tools/gen_scars.py`). Still to do: hook hand and wooden leg; marks on illagers; senses
  ki, sworn to a patron, bog iron, never sleeps, hates fire mages (folded into watchful and witch hunter for now).
- Patch 2 (Kingdoms 0.7.617): bodyguards (turned ones stab their boss), plants both ways (yours via "Plant in Camp",
  theirs hidden as town villagers, unmasked by a Will staredown), poisoned feasts, rigged duels, quick kills framed on
  another captain, and prices on the heads of captains who kill players (`CaptainSchemes`).
- Patch 3 (Kingdoms 0.7.618): claims round each camp (tolls you can sneak past, caravans robbed), ground in journal
  entries on the map, and the ladder screen from the journal's Captains button (`CaptainGround`, `Ladders`).
