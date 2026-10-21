# Wounds: locational health for every living thing (replaces Downed)

Andy (2026-10-19): "I NEED this worked in with our corpses and cripplings, remove our downed system for a universal one
written like this https://modrinth.com/mod/first-aid-new".

First Aid (ichttt's, and the "first-aid-new" port) is **GPL-3.0**: we write our own, after its design, and copy none of
its code or art. Choices Andy made:

| Question | Answer |
|---|---|
| Who has body parts | **Every living thing** (players, people, animals, monsters) |
| Head or body at zero | **Downed, then death** (unconscious, crawl, carry, revive with medicine; bleeds out) |
| Medicine | **Medieval only** (2026-10-20: "I dont want any modern medicine"): linen bandage, rag, splint, willow-bark tea, poppy syrup (addiction and withdrawal), strong spirits, smelling salts, needle and thread, cautery, leeches |
| Limbs | **Zero = crippled, overkill = severed** (our Dismember, carried onto Kitchen's corpse) |
| Heart row | **Kept**: it works as an approximation; the body silhouette sits beside it |
| Monsters downed? | **Most just die** when head or body hits zero. Zombies and skeletons have a high chance (`UNDEAD_FIGHT_ON`) to keep fighting instead |
| Tamed animals | **Downed and savable**, revived with their own taming item (bone for wolves, fish for cats, golden apple or carrot for horses...) |
| Player bleeds out | **Lies as a corpse** until roused with smelling salts or they give up (hold the key); no respawn before that |
| Spirits | **Brewed in Kitchen**: a new brewing system and still block (`KitchenCompat`; without Kitchen, spirits can't be made but loot and traders still carry them) |

`Fractures` (blunt blows break bones, a priest sets them) folds into this plan's broken limbs in phase 3.

## Where it lives

- **Jaymo's Powers, `core/wounds/`** (new package). Powers already holds `core/Dismember` and talks to Kitchen
  (`KitchenCompat`), and depends on Kitchen; Kingdoms stands alone.
- **Kingdoms' `Downed`** is removed as a system. What only Kingdoms knows stays in Kingdoms and reads the shared state:
  villagers tending the downed, reviving with food, guards dropping their weapons, routines and workers skipping the
  downed, `Rearm`, `Pickpocket`, `ShutIn`. They read an **entity tag** (`jaymospowers_downed`) and the synced
  attachment by id, so Kingdoms still runs without Powers.
- **Kitchen's corpses** take the missing parts as they do now (`Dismember.MISSING` → `jaymoskitchen.missing.*`), and
  also note broken parts (a splinted leg shows on the body).

## Bodies

`BodyPlan`: which parts a kind of creature has, each part's share of its max health, and how hits land on it.

| Plan | Parts (share of max health) | Who |
|---|---|---|
| Humanoid | head 0.15, body 0.30, two arms 0.10 each, two legs 0.12 each, two feet 0.055 each | players, villagers, folk, zombies, skeletons, piglins, illagers, endermen |
| Quadruped | head 0.2, body 0.4, front legs 0.2, hind legs 0.2 | cows, horses, wolves, pigs, sheep, cats, foxes... |
| Arthropod | head 0.25, body 0.45, legs 0.3 | spiders, silverfish, endermites, bees |
| Flyer | head 0.25, body 0.45, wings 0.3 | birds, bats, phantoms, vexes, allays |
| Simple | body 1.0 (no locational damage, but can still be downed and treated) | slimes, fish, squid, ghasts, blazes, golems, bosses |

The plan is chosen by entity type tags (`jaymospowers:body/humanoid` and so on), with a fallback by dimensions
(standing taller than wide → humanoid, wider → quadruped). Bosses (`c:bosses`) and the Ender Dragon or Wither keep
vanilla health.

**Health stays real.** Vanilla `getHealth()` is still the sum of the parts, so every mod that reads or heals health
keeps working. The parts are a `Wounds` attachment: one float per part, synced to the entity's own player
(full detail) and to everyone tracking it (part states only, for limps and poses).

## Where hits land

- **Melee and projectiles:** by where the hit lands on the box. Height share, plus side by facing (left or right arm,
  leg, foot). Dismember's `aimedPart` already does this for melee; it's generalised.
- **Falls:** feet first, overflowing into the legs, then the body.
- **Explosions:** spread across every part, weighted by the side facing the blast.
- **Fire, lava, magic, poison, wither, starving, drowning, freezing:** the body, overflowing to the head, never killing
  outright through the limbs.
- **Suffocation and drowning:** the head.
- **Overflow:** damage past a part's zero spills into the body (and from the body into the head), so a limb alone never
  kills.

## When a part reaches zero

- **Head or body: downed** (the old Downed, generalised).
  - Unconscious: vision greyed, can crawl slowly or lie still (people), held by the AI hold (`DownedMixin` moves to
    Powers).
  - Can be carried (Carry), dragged, stabilised and revived.
  - Bleeds out after `BLEED_OUT_TICKS`, or after `MAX_WOUNDS` more hits, then dies (its death counts then, for wars,
    quests and crimes).
  - Players who bleed out lie as a corpse until roused (smelling salts) or they give up: hold a key to die and respawn.
  - Monsters and wild animals: no downed state, they die. Zombies and skeletons (and their kin) have a
    `UNDEAD_FIGHT_ON` chance to keep fighting on a zeroed body instead, until the head is cut off or crushed.
  - Tamed animals: downed like people, and revived with their own taming item.
- **Arm: broken.** Weaker and slower attacks, slower mining, no shield or bow on that arm, and a held item may drop.
  Both arms broken: can't use items at all.
- **Leg: broken.** Slowed, no sprinting, a lower jump. Both legs: crawl only (our `Crawl`).
- **Foot: sprained.** Slowed a little, louder footsteps.
- **Wing (flyers): can't fly.**
- **Overkill on a broken limb or head:** a hit for more than `SEVER_OVERKILL` (a share of that part's max) with a
  severing weapon (Dismember's `SEVERING` tag) cuts it off. This replaces Dismember's flat random chance; its blade
  chances become multipliers. A severed head is death (decapitation), as now. A severed limb stays gone: Dismember's
  `MISSING` (and regrowth by Verdant, Z Fighter and the symbiote) is unchanged.
- **Existing cripplings feed in:** Darktide's leg strikes, the Great Ape, ledge falls, Smoke's ash, Wind's crushes and
  the like call `Wounds.hurtPart(entity, part, amount)` or `Wounds.breakPart(...)`, instead of their own slowness.

## Healing

- **Natural:** vanilla regeneration (food, Regeneration) goes to the most hurt part first, never above a broken part's
  `BROKEN_CAP` (0.25) until it's treated.
- **Sleeping** heals every part a share.
All medicine is medieval: nothing modern (no pills, injections or machines).

- **Linen bandage:** a part, heal over time, stops bleeding. Wool and string. Crafts 4.
- **Rag:** a weaker bandage. Any wool or leather, crafts 2. Dirty: the wound may fester.
- **Splint:** sets a broken arm or leg, which lets it heal past the cap and lifts most of its debuff while worn (a
  little slower while worn). Sticks and string, or leather.
- **Willow-bark tea:** hides the pain effects (slowness and weakness from broken parts) for a few minutes, after a
  slow start. Stripped log, water bottle, bowl.
- **Poppy syrup (dwale):** strong pain relief that also brings a downed one round to crawling. Adds to a hidden
  **addiction** value: high addiction brings withdrawal (shakes, nausea, weakness, cravings). Too much at once knocks
  you out. Poppies, sugar, strong spirits.
- **Strong spirits (aqua vitae):** drink: dulls pain and a short burst of strength ("Dutch courage"), but drunkenness
  (swaying view, nausea at high doses, a hangover crash). Pour on a wound: cleans it (stops or prevents festering).
  Made in Kitchen's new still.
- **Smelling salts:** one item, two uses. On the downed: wakes them on the spot (half a heart in head and body, a
  short jolt of speed, a daze after); 1 of 4 uses. On one who has just bled out (a short window, `SWOON_TICKS`, they
  were only in a deep swoon; for players, their corpse): a chance to rouse them, using the whole vial. Glass bottle,
  bone meal, gunpowder.
- **Needle and gut thread:** stitches a deep cut: stops heavy bleeding faster than a bandage and lets that part heal
  past half. Hurts the part a little. Iron nugget and string.
- **Cautery:** a blade heated over a campfire or lava, or a fire-aspect blade, held to a wound: stops all bleeding at
  once but burns the part. The only quick way to close a severed stump, which otherwise keeps bleeding.
- **Festering:** an open wound left unclean (a rag, a zombie's hit, a dirty blade) can fester: fever, slow damage,
  weakness, and the part can't heal. Spirits or honey poured on it cures it. Left too long, the limb must come off.
- **Leeches:** a priest or quack applies them. They cost a little health, but dull pain for a while (Andy: "give them
  some interaction for pain removal").
- **Treatment takes time:** a channel (hold use) of so many ticks, cancelled by moving or being hit, as First Aid's
  "realistic" mode.
- **Kingdoms:** villagers bandage and splint the downed and wounded (the old tending), and food still revives (the old
  Kingdoms revive). Clerics are barber-surgeons: set bones, stitch, splint, apply leeches, and sell bandages, salts
  and spirits. Taverns and festivals sell spirits.
- **Using it:** right-click your own body (yourself), or another creature, opens a small body screen to pick a part.
  Sneak + right-click treats the worst part.

## Client

- **HUD:** a body silhouette (bottom-right, as Andy likes his HUDs) with each part coloured by health, flashing when
  hit. The vanilla heart row stays; the total doesn't change.
- **Effects:** pain blur when a part is low, a red edge pulse on hits, grey while downed, heartbeat when critical,
  a warm poppy haze, a smelling-salt jolt (white flash and a gasp), a drunk sway, and a feverish shimmer when
  festering.
- **Poses:**
  - Broken leg: a limp, using the model's walk.
  - Broken arm: hanging.
  - Downed: the old `DownedPose`, moved to Powers and generalised to quadrupeds (lying on the side).
- **Body screen** for treatment (key: H menu tab, or right-click with medicine).

## Phases (each its own release, built and tested before the next)
(All five phases ship together as Powers 0.15.1 and Kingdoms 0.7.104; the phase specs keep the working numbers 0.14.0-0.14.5.)

1. **Core. Built in 0.15.1 (written without a compiler; not yet built or tested on the Deck).** `BodyPlan`, the `Wounds` attachment, damage routing (hits, falls, fire, explosions), overflow, natural
   healing, a debug command (`/wounds part <part> <amount>`, `/wounds show`), and the HUD silhouette. No debuffs or
   downed yet.
2. **Downed. Built in 0.15.1 with Kingdoms 0.7.104 (written without a compiler; not yet built or tested on the Deck;
   spec `docs/Wounds-Phase2.md`).** Move Downed from Kingdoms into Powers, universal. Kingdoms reads the shared tag
   (tending, food revive, guards). Bleed out, give up, carry.
3. **Cripplings. Built in 0.15.1 (written without a compiler; not yet built or tested on the Deck; spec
   `docs/Wounds-Phase3.md`).** Broken arm, leg, foot and wing debuffs. Existing cripplings rerouted. Sever on overkill (Dismember).
   Corpses show the breaks.
4. **Medicine. Built in 0.15.1 (written without a compiler; not yet built or tested on the Deck; spec
   `docs/Wounds-Phase4.md`, Kingdoms' clerics in its track B).** The medieval kit, treatment channel, body screen, poppy addiction, smelling salts, needle, cautery,
   festering, leeches, Kingdoms clerics as surgeons. Kitchen's brewing system and still (its own Kitchen release).
5. **Polish. Built in 0.15.1 (written without a compiler; not yet built or tested on the Deck; spec
   `docs/Wounds-Phase5.md`: track A the client feel, track B the rules, tuning, docs and JEI).** Client effects, limps
   and hanging arms, quadruped downed pose, tuning, docs (`Abilities.md`), JEI pages. Freerunning respects breaks;
   a broken leg tags its owner `jaymospowers_crippled` for a later Kingdoms patch.

Per CLAUDE.md: big patches run as parallel tracks, 2 at a time. Phases 1 and 2 are sequential (2 needs 1). 3 and 4
can run side by side after 2. Each phase bumps the version and gets a server test on the Deck.

## Open questions for Andy

All answered 2026-10-20 (see the table at the top).
