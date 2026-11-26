# Night's Blood: the vampire plan (2026-11-26)

Andy picked 35 of 36 rows of the Nycto pick page (https://claude.ai/artifact/1yC3txi32bzFkidV9j3W5Q, db `picks/andy`,
source `/mnt/project-files/nycto/nycto-picks.html`). Only "Every vampire plays the same" wasn't ticked, and patch 2
covers it anyway. Plan thread: cmsg_017LSt6rWz6CzxSGvzDd41XWR3z7FXFhj6dAh8vLpJSC85. Source mod: Nycto (MoriyaShiine).

## Andy's words (2026-11-26)
- On what felt wrong in play: "it doesnt feel as coherent or fluid as any of the other powers"
- On days: "you should be able to hypnotize an mca who will act as your thrall and you can control their actions, to be
  able to move during the day unhindered."
- On choosing gifts: "these options only appear for vampires"
- On Blood Flechettes (left out on the page): "blood flechettes can be added, but draw from vampire hunger (blood
  measured in Mb"

## Licence (checked 2026-11-26)
- Nycto is All Rights Reserved. Its GitHub LICENSE reads "Copyright (c) MoriyaShiine. All Rights Reserved." Nothing of
  it may be copied: no code, textures, models, sounds or names (Batstep, Haemogenesis, Bloodrush, Carnage, Vampiric
  Thrall, Rich Tastes, Vile Presence and the rest). Ideas only, our own code, names and vanilla-style art. Don't ship
  the jar.

## Rules every patch keeps
- Lore first: the (new) paragraph "Where the blood began" in Lore.md is the home for all of this. Each patch adds its
  own (new) lines there before code if it brings something the paragraph doesn't cover.
- No screen text that states lore or announces discovery. Hints go in villager gossip, loose pages and the loading hints.
- No slash commands for player features (op debug commands are fine). UIs only.
- Blood stays toned down: Powers' blood flecks, no pixel blood.
- No free projectiles: Blood Flechettes are allowed only because they spend the vampire's own blood (Andy, above).
- Dhampirs keep what they have now: gifts without the weaknesses, no night strength. New gifts and pacts are for full
  vampires unless a row says otherwise.
- New features must work with old ones: blood fever and seizures, the Mask, holy dread, old rules, VampireAlarm and
  Suspicion, Exposure, downed and carrying, MCA talk, Shoulder Surfing, the Spell Engine bar, a Jaymo's Powers power held
  at the same time, werewolves (their O, P, U and pack page don't change).
- Tuning numbers are `static final` constants at the top of each class. Every player-visible word goes through Quill.
- Old worlds don't matter (Andy 2026-11-24).

## Patches (one PR each; Andy merges)

### 1. One body of blood (Kingdoms) rows gap-played, gap-keys, Andy's "blood measured in Mb"
The base everything else stands on: one blood measure and one place for the moves, so the vampire feels as whole as a
Power.
- **The blood reserve, in mB.** A full vampire's hunger becomes blood measured in millibuckets: 0 to 2000 mB, with
  100 mB for each food point so every current cost converts cleanly (the Kitchen's bottle and grate level are already
  250 mB). Kept as an attachment (`VampireBlood`), synced, shown in place of the food bar as a row of dark vials, with
  the exact mB on hover in the inventory. Bites, the throat grab, lapping, bottles and sleeping prey all add mB. Shapes,
  the stare, the mask and every new gift cost mB. Starving is below 200 mB. The vanilla food bar is kept full and
  ignored for vampires (VampireFoodMixin already stops food feeding them). Dhampirs keep the food bar.
- **Every move on the bar.** All vampire moves live on the Spell Engine bar (Feed, Dash, Pound and Gaze are already
  there as FormSkills `night_*`). Add the shapes (bat, colony, mist, stare), Rake, Rend, Pounce and the frenzy roar.
  For vampires, remove the duplicate inputs: O (dash), P (pound), sneak+O (shape menu) and the numpad Claw keys. Keep
  right-click leap when locked on, U to give in, sneak+use lapping and sneak+scroll for stakes (struggle inputs).
  Werewolves keep O, P and U as they are. A player who also holds a Power keeps R/G/V/B/C for the Power.
- **Moves that flow.** Chains like the Powers' combos: a dash can be cancelled into Pounce or Rake, a right-click leap
  that lands on prey goes straight into Feed, a Groundpound's throw can be followed by a Rend, mist can end in a leap.
  Each chain has its own window constant.
- A loading hint for the bar, and the H menu's controls page lists the vampire's moves.

### 2. The Blood page, the shrine and the pacts (Kingdoms) rows ny-pick, ny-weak, ny-altar, new-age, gap-growth
Needs patch 1.
- **The Blood page.** Stealth's Night page becomes its own page shown only to vampires (and dhampirs, who see only the
  nodes a dhampir can take). Werewolves and wolfbloods keep their pack nodes where they are. Nodes: Lifeleech, Night
  Sense, Groundpound, Bat Wings (moved), the four shapes, the claw moves, and patch 4's gifts.
- **Gift slots.** Learned gifts must be slotted to use. Three slots free, up to six. Each slot past the third needs a
  pact. Only slotted gifts show on the bar.
- **Blood age** (points for the page, and a road to strength that isn't surrender). Fledgling, then grown, then old.
  It comes from nights lived as a vampire, new moons seen, and great ones fed upon (a lord, a king, a champion, a
  boss-tier creature). Each age gives page points and a little night strength. Surrender stays as the faster, darker
  road, and its stages keep what they give now.
- **Pacts** (extra weaknesses taken for gift slots, taken and given up only at the shrine):
  - Drowned blood: water and rain slow you.
  - Kindling: fire, torches and the noon sun hurt you more.
  - Fine palate: only people's blood feeds you, and bottles give half.
  - Unquiet: animals flee you, you can't tame or breed them, and traders charge you more (VampireTells).
  - Thin blood: your regeneration (ClawArts) stops whenever your reserve is below half.
- **The crypt shrine.** Candles, a skull and a blood bottle set at the head of a coffin (OldRules' coffin) make a
  shrine. Sneak-use it to open the Blood page with slots and pacts. Anywhere else the page only shows what you have.
  The turning crypt (Lairs) gets one already set.

### 3. Blood worth drinking (Kingdoms, Kitchen) rows gap-blood, ny-bottle, new-bloods, new-donors, ny-keen
Needs patch 1.
- **Fill your own bottles.** Holding someone by the throat (BloodGrab) with a glass bottle in the off hand fills it
  (250 mB from them, a Kitchen blood bottle remembering whose blood it is). A knife's bleeding wound over a bottle does
  the same, slower. Drinking a bottle gives its mB.
- **Every blood tastes of its people** (Heritage). Each heritage's blood gives a short after-taste: Frostborn, cold
  can't touch you; Emberborn, fire can't; Tideborn, you swim fast; Stoneborn, you're heavy and hard to knock back;
  Verdant, half the mB; Divine-born, it burns you (holy dread); Nightborn, a long, deep fill. A king's or lord's blood
  gives double mB. Bottles carry their blood's taste. A favourite blood can become one of BloodRequests' asks.
- **Asking and paying.** A new MCA talk option for vampires, "Ask to drink", with a villager who knows or half-knows
  what you are (Suspicion, Exposure). It's tied to what they want (OwnWants) and paid in coin or a favour. A willing drink raises no
  alarm and makes no struggle. A refusal is remembered (Recollections), and a timid or devout villager may report it.
- **Read your prey.** Blood vision's outline takes the colour of the creature's blood (as BleedOut picks it), and
  dims as its blood runs low.

### 4. New gifts (Kingdoms) rows ny-dark, ny-barrier, ny-swarm, ny-hypno, ny-mist, ny-batstep, Andy's flechettes
Needs patches 1 and 2 (each gift is a Blood page node, paid in mB).
- **A true shape of the night.** A fifth shape, a gaunt winged horror (our own model, hermes-modeller first; not a wolf, not a bat).
  It hits harder, takes more, jumps once in the air, and tears blocks softer than stone by hand. It can't use armour or
  tools (the werewolf beast's stow rules). Each pact you carry makes it a little stronger. It drains mB fast. Folk who
  see it scream and run (Bloodshed, Suspicion). Late on the page.
- **Blood barrier.** Three rings of blood circle you for a while, each soaking one blow, and a heavy blow breaks two.
  Costs mB.
- **A colony that drinks.** The colony's bats latch and drink like BloodBat from whatever you hit or whatever hits you,
  and their drink comes back to you as mB.
- **A longer hold.** The stare's hold lasts much longer, and the held follow you and stand with you in a fight. A calm
  drink from them makes no struggle, but a witness still sees it (VampireAlarm). A stronger will (Will, Saves) shortens it.
- **Mist that slips through.** Mist passes through doors, trapdoors, bars, fences, leaves and panes, takes no fall
  damage, and its first blow out of mist lands double and ends the mist.
- **Batstep.** A short blink (about 8 blocks) to where you look, in a burst of bats. Anything you pass through is hurt
  and staggered. The riposte stays as it is.
- **Blood flechettes** (Andy: "draw from vampire hunger"). Shards of your own blood, a fan of up to five, each costing
  mB (so the reserve is the ammunition, never free). A creature hit can't heal for a while, and your next blow on it
  gives you back a little blood. Players only where PvP is allowed.

### 5. Servants and your get (Kingdoms) rows gap-servants, ny-thrall, new-sire, gap-day (Andy's note)
Needs patch 1 (and 4's longer hold makes it smoother, not required).
- **Enthral a servant.** A villager (MCA) or a tamed horse or wolf held by the stare and drunk from on three separate
  nights becomes your thrall for good. A thrall villager keeps their name, home, job and family, and their family
  notices the nights away (Suspicion, gossip). Order them with the four orders players give their companions (the ones BandLeaders mirrors; Bonds for the bond).
  Feeding them a bottle keeps them sound. The priest's cure, your death or a long neglect frees them.
- **Ride your thrall by day** (Andy). From your coffin by day, sneak-use it to sleep and look out through a thrall
  villager's eyes. You walk them, use their hands, talk and trade as them, carry and fetch for you, and wake when you
  want. Uses the takeover rules: their body faces where it goes, and the camera is their eyes. Your own body lies in
  the coffin and can be staked (OldRules). If the thrall is hurt badly, you wake. Folk see only the villager. A thrall
  under holy dread, or in a temple, throws you out.
- **Your get.** Villagers your bite turns (Vampires' spread) are your fledglings. They feel where you are and you feel
  them (Kinship). They may come when you're hunted, can be ordered like a thrall at night, and a fledgling whose blood
  age outgrows yours may turn on you.

### 6. Days and nights (Kingdoms, Powers tailor) rows ny-coffin, ny-cloak, ny-newmoon, new-lair-hunt, new-willing, gap-way-in
Needs patch 1 for mB costs, otherwise independent.
- **Sleep the day away.** Lying in a coffin by day skips to dusk when every player is asleep or resting, as beds skip
  to dawn (BedRest). A stake into a sleeper still kills for good.
- **A night-cloak from the tailor.** A deep-hooded cloak, sold only by tailors (clothes rule). The noon sun stings
  instead of burning (Weakness, smoke, no fire), and folk see a hooded stranger instead of the ashen face. Holy dread
  still finds you.
- **The new moon is theirs.** On a new moon (Moons, SunMoon phases) vampires are bolder: the Elder hunts twice in the
  night, Night Bleeders come down, the fever climbs faster, and a vampire's leap and claws are a little stronger.
- **Hunters seek your lair by day.** Once a realm's suspicion of you is high, a party of hunters (guards with torches
  and stakes, a priest) goes looking for your crypt by day. You hear them coming through the heartbeat. You can move
  your coffin and shrine (they break into an item).
- **The willing turning.** Drink the Elder's blood from a bottle under a new moon and you turn at the next nightfall,
  as if bitten. Getting it means beating the Elder down (not killing him) or bargaining with him in talk. Hinted only
  in gossip, loose pages and a hunter's words, never in discovery text.

### 7. Hunters (Kingdoms, Kitchen, Powers baubles) rows gap-hunter, ny-garlic-plant, ny-wreath, ny-brew, ny-firebomb, ny-kit
Independent of patches 1 to 6. Shares parts with Hunter's Road patch 7 (oils and bombs), so check its state first.
- **Wild garlic you can grow.** A wild garlic plant in forests and swamps, plantable as a crop (Kitchen or Kingdoms crops),
  sold by farmers. Cut grass keeps its rare drop.
- **Garlic wreath.** Worn in a baubles slot (necklace or head): a vampire's bite slips off you and its grab can't hold
  you. Hung over a door (placed as a block), no vampire can come through, even invited (OldRules). It withers after a
  few days.
- **Garlic draught.** Brewed with our data brewing (like the Nightfall draughts). For a while your blood is sour: a
  vampire that bites you retches and lets go, and gets no mB.
- **Fire flask.** If Hunter's Road's fire-burst bomb (its patch 7, D5) is built, use that bomb and make sure vampires
  take their 4x fire from it. Otherwise make a crafted oil flask, one item per throw.
- **The hunter's kit.** A garlic-and-silver blade oil for the cursed (vampires, werewolves), added as a fifth kind to
  Hunter's Road's oils if they exist, else on its own (a coat lasts a few hits). A hunter's coat from the tailor
  (resists bites and the grab). The Lantern Warden's priests sell the kit to anyone who has killed a vampire.

## Order
Patch 1 first, alone (everything else spends mB and lives on the bar). Then 2 with 7, then 3 with 6, then 4 with 5.
The true shape's model (patch 4) goes to hermes-modeller as soon as patch 2 is up, so it's ready when the code lands.
