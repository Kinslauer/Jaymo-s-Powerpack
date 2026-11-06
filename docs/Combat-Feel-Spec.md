# Combat feel spec (Rivals patch 7)

What Andy's Medal clips show about how our fights feel, what our numbers are, and what patch 7 changed. Built 2026-11-06
(Kingdoms 0.7.622, Powers 0.21.20). Ideas from the Arkham and Shadow of War games only; their exact numbers are UNKNOWN and
not copied.

## The clips

Andy's clips in project files (`MedalTVMinecraft2026*.mp4`) are 20 clips of 30 s each, 1280x720 at 60 fps. Two are exact
copies (`... 2.mp4`). I pulled a frame a second from every clip, then four a second from the ones with fighting in them.
Only four have melee in view:

| Clip | Time | What it shows |
|---|---|---|
| `1105062959871` | 0:00 to 0:20 | Third person in a goblin war camp. Six or more goblins are on the player at once, and the screen edge stays red (hurt) for most of it. Nobody waits their turn. |
| `1105061735294` | 0:00 to 0:05, 0:13 | First person against a skeleton at arm's length, then the Executioner advancement as it's finished. |
| `1026192525937` | 0:11 to 0:12, 0:18 to 0:19 | A Guard of Dunorest coming at the player on a ship's deck. Most of the fight is hidden behind barrels. |
| `1021083205171` | 0:12 to 0:22 | Downed in the snow ("hold sneak to give up"), then dead. |

The rest are travel, ships, whaling, menus, rides, carcass dragging and night scenes with no melee.

**What the clips can't measure:** glint-to-blow time, parry windows, hit pauses and recovery. At 720p the glint star
can't be read. Most swings happen off screen or while the camera spins, and no clip shows a parry. Those timings are
UNKNOWN from the clips, so the numbers below come from the code.

## Our numbers (from the code)

| What | Where | Ticks | Seconds |
|---|---|---|---|
| A mob's glint to its blow | `AttackTells.WINDUP` | 6 | 0.30 |
| A Kingdoms fighter's swing | `MobCombat.MIN/MAX_SWING_TICKS` | 10 to 40 | 0.5 to 2.0 |
| Its upswing (glint to blow), at least | `MobCombat.MIN_UPSWING_TICKS` | 4 | 0.20 |
| Its recovery after a swing | `MobCombat.RECOVERY_TICKS` | 4 | 0.20 |
| Its combo resets after | `MobCombat.COMBO_RESET_TICKS` | 40 | 2.0 |
| Perfect parry window, from the raise | `Guard.PERFECT_TICKS` | 5 (+2 locked on) | 0.25 |
| Parry window | `Guard.PARRY_TICKS` | 12 (+4 locked on) | 0.60 |
| A raise too soon after a missed one | `Guard.EAGER_TICKS` | 15 | 0.75 |
| Attacker stunned by a perfect parry / a parry | `Guard.PERFECT_STUN` / `PARRY_STUN` | 30 / 12 | 1.5 / 0.6 |
| Riposte owed after a perfect parry / a parry | `Guard.*_RIPOSTE_TICKS` | 40 / 25 | 2.0 / 1.25 |
| Riposte damage | `Guard.*_RIPOSTE` | x1.5 / x1.25 | |
| Hit pause | none: a parry only shakes the camera (`Shakes`) | 0 | 0 |

## What felt off, and what patch 7 changed

1. **Everyone swings at once.** The war camp clip is the clearest: a crowd closes in and every goblin strikes together,
   so there's no time to read a glint and parry. Better Combat only plays the swings, and `RangedTactics` only moves
   archers, so nothing staggered them.
   **Changed (Powers `combat/Crowds`):** at most three melee foes come at a player at once. The rest wait in a ring about
   6 blocks out: they circle, watch, shout now and then, and can't strike or close in. A turn lasts 5 s, then the next
   nearest gets it. Archers, bosses (100+ health) and anything too big to circle never wait.
2. **One parry, then you're lost in the crowd.** A parry stuns the one in front, but the next blow comes from behind or
   the side.
   **Changed (Powers `Guard` + `Crowds`):** a parry while another foe's blow is coming turns you to face him. Parries
   within 3 s of each other chain, and each link makes the riposte 15% harder, up to four links (+60%).
3. **Finishing one does nothing to the rest.** The skeleton clip's execution changes nothing around it.
   **Changed (Kingdoms `Dread`):** a foe finished (downed and executed, or its head off) in view of its fellows frightens
   those within 16 blocks who are weaker than the killer. They break off and run for 7 s. Timid ones always run, gloomy
   ones nearly always, brave ones never, proud and hotheaded ones seldom, and the rest about half the time. The undead,
   captains and anything big don't scare.

## What still differs

- **Timings unmeasured.** The glint-to-blow time (0.30 s), the parry windows and recovery are as they were. The clips
  can't show whether they feel right. A 20 s clip would let us measure them frame by frame: first person, guard raised,
  facing one guard who's swinging, with no other mobs.
- **No hit pause.** Those games freeze a frame or two on a heavy hit. We only shake the camera. Not changed here.
- **The turn to the next foe is a snap,** not a lunge across the gap as in Arkham. Our players move themselves.
- **Waiting foes don't feint or taunt** beyond their own ambient sounds. Brain-driven mobs (piglins, hoglins) hold their
  blows while waiting but aren't walked round the ring. MCA villager fighters hold their blows the same way.
