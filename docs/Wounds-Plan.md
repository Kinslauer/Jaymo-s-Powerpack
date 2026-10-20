# Wounds: locational health for every living thing (replaces Downed)

Andy (2026-10-19): "I NEED this worked in with our corpses and cripplings, remove our downed system for a universal one
written like this https://modrinth.com/mod/first-aid-new".

First Aid (ichttt's, and the "first-aid-new" port) is **GPL-3.0**: we write our own, after its design, and copy none of
its code or art. Choices Andy made:

| Question | Answer |
|---|---|
| Who has body parts | **Every living thing** (players, people, animals, monsters) |
| Head or body at zero | **Downed, then death** (unconscious, crawl, carry, revive with medicine; bleeds out) |
| Medicine | **Full set**: bandage, plaster, painkillers, morphine (addiction and withdrawal), adrenaline injector, defibrillator |
| Limbs | **Zero = crippled, overkill = severed** (our Dismember, carried onto Kitchen's corpse) |

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
  - Player give-up: hold a key to die now.
  - Monsters and animals: downed briefly, then die unless treated (a tamed wolf or horse can be saved).
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
- **Bandage:** a part, heal over time, stops bleeding. Wool and string. Crafts 4.
- **Plaster (splint):** sets a broken arm or leg, which lets it heal past the cap and lifts most of its debuff while
  worn. Paper, string, sticks.
- **Painkillers:** hide the pain effects (slowness and weakness from broken parts) for a few minutes. Poppy and
  sugar.
- **Morphine:** a strong painkiller that also brings a downed one round to crawling. Adds to a hidden **addiction**
  value: high addiction brings withdrawal (nausea, weakness, cravings).
- **Adrenaline injector:** revives the downed on the spot (half a heart in head and body), with a burst of speed and
  strength, and then a crash. 4 uses.
- **Defibrillator:** revives a downed one who has just bled out (a short window after "death" for people: their
  corpse is brought back) with a charged use. Redstone, copper, iron.
- **Treatment takes time:** a channel (hold use) of so many ticks, cancelled by moving or being hit, as First Aid's
  "realistic" mode.
- **Kingdoms:** villagers bandage and splint the downed and wounded (the old tending), and food still revives (the old
  Kingdoms revive).
- **Using it:** right-click your own body (yourself), or another creature, opens a small body screen to pick a part.
  Sneak + right-click treats the worst part.

## Client

- **HUD:** a body silhouette (bottom-right, as Andy likes his HUDs) with each part coloured by health, flashing when
  hit. The vanilla heart row stays; the total doesn't change.
- **Effects:** pain blur when a part is low, a red edge pulse on hits, grey while downed, heartbeat when critical,
  morphine colour grade, adrenaline rush.
- **Poses:**
  - Broken leg: a limp, using the model's walk.
  - Broken arm: hanging.
  - Downed: the old `DownedPose`, moved to Powers and generalised to quadrupeds (lying on the side).
- **Body screen** for treatment (key: H menu tab, or right-click with medicine).

## Phases (each its own release, built and tested before the next)

1. **Core.** `BodyPlan`, the `Wounds` attachment, damage routing (hits, falls, fire, explosions), overflow, natural
   healing, a debug command (`/wounds part <part> <amount>`, `/wounds show`), and the HUD silhouette. No debuffs or
   downed yet.
2. **Downed.** Move Downed from Kingdoms into Powers, universal. Kingdoms reads the shared tag (tending, food revive,
   guards). Bleed out, give up, carry.
3. **Cripplings.** Broken arm, leg, foot and wing debuffs. Existing cripplings rerouted. Sever on overkill (Dismember).
   Corpses show the breaks.
4. **Medicine.** All six items, treatment channel, body screen, painkillers, morphine addiction, adrenaline,
   defibrillator, Kingdoms villagers using bandages.
5. **Polish.** Client effects, limps and hanging arms, quadruped downed pose, tuning, docs (`Abilities.md`), JEI pages.

Per CLAUDE.md: big patches run as parallel tracks, 2 at a time. Phases 1 and 2 are sequential (2 needs 1). 3 and 4
can run side by side after 2. Each phase bumps the version and gets a server test on the Deck.

## Open questions for Andy

- Is the vanilla heart row still shown, or replaced by the silhouette?
- Do monsters get downed at all, or just die when the head or body is gone (simpler, faster fights)?
- Tamed animals: downed and savable like people?
- Should players respawn as now when they bleed out, or stay as a corpse until revived (defibrillator) or they give up?
