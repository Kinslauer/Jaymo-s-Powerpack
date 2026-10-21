# Wounds phase 2: Downed, universal (Powers 0.14.1 + Kingdoms)

Built on Wounds phase 1 (`core/wounds`, Powers 0.14.0). Plan: `docs/Wounds-Plan.md` ("When a part reaches zero" and the
answers table at the top). Andy (2026-10-19): "remove our downed system for a universal one".

## What moves where

- **Powers `core/wounds/Downed`** owns the downed state for everyone: going down, the AI hold, bleeding out, wounds
  from further hits, getting up, giving up, carrying (through Powers' own `core/Carry`), and the lying pose.
- **Kingdoms** keeps only what only it knows, reading Powers' state through the scoreboard tag `jaymospowers_downed`
  (Kingdoms never compiles against Powers): villagers tending the downed, disarming a downed guard, `Rearm`,
  `Pickpocket`, `ShutIn`, routines and workers skipping the downed, and *which* of its folk can go down (it marks
  them with the scoreboard tag `jaymospowers_downable` when they take damage, in its existing ALLOW_DAMAGE listener,
  using its existing `downable()` logic). Kingdoms' own going-down, AI-hold, bleed-out, revive and pose code is
  removed. Without Powers installed, Kingdoms' folk simply die (acceptable per the plan).

## Who goes down (Powers `Downed.downable`)

- Players (not creative or spectator).
- Villagers (any `Villager`), and anything carrying the scoreboard tag `jaymospowers_downable` (Kingdoms' folk).
- Tamed animals: a `TamableAnimal` that is tame, or a tamed horse-like animal (`AbstractHorse.isTamed()`).
- Never: monsters and wild animals (they die), bosses, anything whose head is gone (`jaymoskitchen.missing.head`
  tag, or the decapitation damage type), damage that bypasses invulnerability (void, /kill).
- **Zombies and skeletons fight on:** when Wounds' lethal rule fires on a zombie or skeleton
  (`EntityTypeTags.ZOMBIES` / `SKELETONS`) because its BODY reached zero (not its head), roll `UNDEAD_FIGHT_ON` (0.6):
  on success it doesn't die and fights on; its body part stays at 0 and only a head hit (or the head reaching 0) kills
  it. Implement in Wounds' lethal path.

## Going down

- Hooked on `ServerLivingEntityEvents.ALLOW_DEATH` (as Kingdoms' Downed does now): a downable entity that would die
  goes down instead (return false), at `DOWNED_HEALTH` (6, capped at max health); Wounds parts reconcile to it (head
  and body get at least 0.1 each). Its killer is remembered for the kill credit.
- A downed entity carries the attachment `jaymospowers:downed` (Long: game time << 1 | crawler bit; persistent;
  synced to all, as Kingdoms' was) and the scoreboard tag `jaymospowers_downed`.
- Mobs: AI held (a mixin on `Mob.serverAiStep` HEAD, cancellable, as Kingdoms' `DownedMobMixin`), target cleared,
  a share `CRAWLER_CHANCE` crawls away from whoever felled it (Kingdoms' `step` logic moves here).
- Players: forced into Powers' `Crawl` (crawl only, slowly), can't attack, use or place (cancel through the Fabric
  callbacks Powers already uses: AttackEntityCallback, UseItemCallback, UseBlockCallback, UseEntityCallback,
  AttackBlockCallback), drop nothing. Screen greyed (client).
- Tamed animals: AI held like mobs; they lie on their side (the client pose falls back to the generic lying pose).

## While down

- Fire, falls, mobs and the like hurt as usual, out of the downed health, and can kill (death then counts, no
  second downing).
- A player's blow is a wound instead of damage (Kingdoms' `wound`): the `MAX_WOUNDS`th (10) kills. Once a player has
  hit them, a mob no longer gets up by itself.
- **Bleeding out:** a downed mob or tamed animal no player has hit gets up after `RECOVER_TICKS` (1200) with
  `RECOVER_HEALTH` (0.3); one a player has hit bleeds out `BLEED_OUT_TICKS` (2400) after the last hit and dies.
- **Players don't respawn on bleeding out** (Andy: "Lie until roused or give up"): after `BLEED_OUT_TICKS` a downed
  player stops crawling and lies still, unconscious (screen dark grey, a slow heartbeat), until revived or they give
  up. **Give up:** hold sneak for `GIVE_UP_TICKS` (100) while down (an overlay bar, server-side from
  `isShiftKeyDown`, no new packet): they die, credited to whoever felled them.
- Disconnecting while down keeps the state (persistent attachment); a downed player who logs back in is still down.

## Getting them up (until phase 4's medicine)

- **Revive:** sneak + hold use on a downed one (Kingdoms' 0.7.93 revive moves here, generalised): a tap picks them
  up (Carry), a hold of `REVIVE_TICKS` (60) gets them up, costing the least filling food you carry; a tamed animal
  takes one of its own taming/breeding items instead (wolf: bone or meat, cat: fish, horse: golden carrot or apple or
  hay, parrot: seeds, and so on; use `Animal.isFood(stack)` if that is how the repo checks it, else a small map).
  Reviving a player costs food too. Up with `RECOVER_HEALTH`.
- Kingdoms' villagers still come and tend the downed (their tending reads the tag and calls Powers' get-up through a
  scoreboard tag request: Kingdoms adds `jaymospowers_revive` to the body when tending completes; Powers' downed tick
  sees it, removes it and gets them up).
- Respawn, `/kill`, the void: the state is cleared.

## Carrying

- Downed bodies are carryable through Powers' `Carry` (check what Carry allows; add the downed as carryable whatever
  their type). Thrown bodies take fall damage as anything does.

## Client

- `DownedPose` moves from Kingdoms' client to Powers' client (generalised: humanoids as now; quadrupeds and other
  bodies lie on their side, rolled 90 degrees). Reads Powers' synced attachment. Kingdoms' `DownedPose` is removed.
- Downed player's own screen: a grey overlay (stronger once unconscious), and an overlay text "Hold sneak to give
  up" with the bar.

## Kingdoms changes (kingdoms/)

- `Downed.java`: remove going-down, AI hold, bleed-out, wounds, revive, pickup/toss, pose sync; keep `isGuard`,
  `downable` (now only used to mark folk with `jaymospowers_downable` in an ALLOW_DAMAGE listener), tending
  (`tend`/`canHelp`/`untend`, ticked from its own server tick over downed villagers nearby players... or from the
  villagers' side: simplest is a server tick every 20 ticks over loaded entities with the tag in each level near
  players, as Kingdoms already scans), and `disarm`. `isDowned(entity)` becomes `entity.entityTags().contains(
  "jaymospowers_downed")`. Its `DOWNED`/`WOUNDS` attachments go (a one-time migration is not needed).
- `DownedMixin`/`DownedMobMixin`: removed (the carried-position and AI hold are Powers' now); keep the
  `dropAllDeathLoot` cancel only if still needed (Powers' downed never die without dropping, so likely remove).
- Lang keys moved to Powers' `lang/parts/wounds.json` where the text moves.
- Kingdoms version bump and its MODLOG entry.

## Release

- Powers 0.14.1 (`gradle.properties`), MODLOG entry, mark phase 2 in `docs/Wounds-Plan.md`.
- Kingdoms patch bump and `kingdoms/MODLOG.md`.
- All tuning numbers as `static final` constants at the top of `Downed`.
