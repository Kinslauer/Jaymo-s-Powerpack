# Blight and Horde Picks plan

Andy's picks (2026-11-15) from the pick list https://claude.ai/artifact/D7UfgwpeNYvrhHcnXXswnZ: ideas from Contagion
(PeteMC; LICENSE MIT but its mod file says All Rights Reserved: ideas only), Zombie Horde (NeoAorus, MIT: ideas only, its
spawner would fight ours), Tissou's Zombie Pack (ARR, "free to use" with credit: shipped by CurseForge id only) and
Entity Texture Features (LGPL, already in the pack). 29 of 31 rows ticked; "Plague-bearers" and "Trappers" left out.
All our own code. Branch `claude/project-thread-pcva66`, one PR, one commit per patch.

Andy's notes are quoted where they change a row.

## Patch 1: how the blight takes you (Kingdoms `HollowBlight`, `BlightSigns`)
- **Armour wards** (ct-armour): a turner's blow lands the blight at chance x (1 - ward); ward = armour points / 30 x
  `ARMOUR_WARD_MAX` (0.7). Broken armour gives no points (#979), so it stops warding.
- **Open wounds carry it** (own-wounds): a blow that leaves a part bleeding (Powers `cuts`) is `WOUND_TIMES` likelier.
  A freshly taken blight first *settles* for `SETTLE_TICKS`; binding the wound (any tend: bandage, rag) in that time
  clears it with `TEND_CLEARS` chance.
- **Tell a tainted wound** (own-tell; "Villagers can do this too"): a player with Medicine skill sees sickly motes on a
  blighted body (players and villagers) from the first hour (particles sent only to them); healers and clerics who see a
  blighted player say so in a speech bubble.
- **Wider symptoms** (ct-symptoms): slowness joins the stages, bouts rest between, a short withering in the last hour.
- **Signs others can see** (own-signs; "That's no regular cough"): the skin greys toward the Hollow's colour by stage
  (synced stage, client tint), a cough now and then from stage 1 (a sound), flies in the last stage. Villagers who hear it
  say "That's no regular cough." A full mask hides the face (not the cough).

## Patch 2: cures and holding it back
- **Tainted flesh** (ct-flesh; "it shouldn't look any different, it's just something that should happen from rotten
  flesh"): eating raw rotten flesh can give you the blight. Cooked rotten flesh (Kitchen) is safe.
- **Gilded flesh** (ct-gilded; "Old druid remedy, most don't trust it"): cooked rotten flesh and gold nuggets make gilded
  flesh; eating it sets the blight's clock back; villagers scoff at it in talk.
- **Brewed cure** (ct-cure; "Only druid apothecaries. Witches hide the cure as they command zombie thralls themselves"):
  the Hollow's Cure, brewed only by the grove druids (they trade it) and Druid-class players (Codex research gated to the
  class). Witches keep one hidden in their huts' chests, and zombie thralls rise by their huts and guard them (like the
  mud golems).
- **Shield after a cure** (ct-immune; "You gain immunity forever after a cure"): every cure leaves you unable to take
  the blight again, for good (kept through deaths; Reincarnate wipes it like everything else).
- **Totem** (ct-totem): a held totem spends itself when the blight would turn you, and that counts as a cure.
- **Temple healers** (own-healer): the town healer and temple priests cure it for coin; righteous ones turn away the Hollow.

## Patch 3: how it spreads
- **Carriers cough it on** (ct-carrier; "This happening is why people avoid those that cough") and **your blows pass it**
  (ct-blows): in the last stage a cough can pass it to anyone close in front of you; your melee hits can too.
- **Zombie blood** (from the unticked Plague-bearers row's note: "Rotten zombie blood should spread infection as well, you
  must clean yourself and armor, soaking can clear it"): zombie blood on your skin or armour can give it to you (more with
  an open wound); scrubbing it off, or being Soaked, clears the danger.
- **Villagers catch it** (ct-villagers): zombie blows, carriers and zombie blood blight villagers too; they sicken for a
  day and rise as one of the dead if nobody cures them; family grieves, a cured one remembers who cured them.
- **Villagers shy from the sick** (ct-shy), **guards keep the sick out** (ct-guards: righteous guards order a coughing
  carrier out, then jail them until cured or turned; lawless ones attack).
- **Outbreaks** (own-outbreak): now and then a village's sick spread; the board posts work (cure the sick, burn the dead).
- **Burn the blighted dead** (own-burn): anyone who dies with the blight rises the next night unless the body is burned
  or buried.

## Patch 4: new horde kinds (Kingdoms `ZombieHordes`, `HordeMoves`)
Builders (carry a few blocks, pillar, bridge, stair; only what they carry), runners (sprint on sight, jump small gaps),
brutes (big, slow, tough, pound doors harder), scouts (range ahead, howl to call the horde, hang back), tool-bearers dig
the matching blocks faster, sieges on the dug-in (more waves through the night on a player who stays put), horde
animations (arms reaching, shoulder into doors, stagger, lunge).

## Patch 5: looks
- **Tissou's Zombie Pack** (ts-pack; "All can go into the future, is there a medieval tag for any of the skins?"): added
  by CurseForge id; its skins show only on the future's dead, mixed with our Warrior-born skins and tails. Medieval
  question waits on reading the pack.
- **Our own rotting skins** (own-skins), **looks by where they rose** (etf-place), **eyes that glow** (etf-eyes):
  generated original skins for the present dead (villager-like, by region and rot), dried/bloated/pale/frostbitten by
  biome, an emissive eye layer at night, brighter under a blood moon.
