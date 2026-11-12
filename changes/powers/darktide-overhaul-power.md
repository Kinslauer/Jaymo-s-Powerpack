# Darktide overhaul, part 1: power
Andy: "Darktide needs an overhaul to bring it up to power and graphical fidelity with the other powers." His picks from the
picklist; this is the power half, the looks follow.
- **Flow is the only cost.** Dark Strike, Crescent, Ink Cross, Undertow, Pounce and Fang Barrage are ready again in a second or
  two; only Black Tide (15 s), Stillwater, the flurry and the C moves keep a real cooldown. Pounce costs 15 Flow.
- **Flow no longer drains in daylight**, trickles in at 1 a second anywhere, and is kept when you log out.
- **Damage**: Black Tide 6 to 12, rolls 16 blocks and pushes harder. Crescent +4 to +8, 3 to 5 foes. Undertow bursts for 8 and
  throws foes back, for everyone and not only the ascended. Riposte 6 to 10. Stillwater 10 + 2 a second held (was 6 + 1), its
  field full in 3 s. Drown 2 to 4 a second, and foes hang helpless in it.
- **The tide**: from 50 Flow it's high tide and moves reach a quarter further. At a full bar it's spring tide: the next Black
  Tide is twice as wide, the next Crescent cuts all the way round, the next Undertow drags those beside you under with you.
- **Maelstrom** (ascended, hold C): a 12-block dome of dark water for 10 s. You swim through it with Flow held full; everyone
  else in it is slowed, can't get out and drowns, then it crashes down for 16. Hold C again to bring it down early. 2 minutes.
  Tapping C (raise a shadow) now acts when you let the key go.
- **Blade of Dark Water**: guarding, G or X with both hands empty draws a katana of dark water (an iron sword's blow), so the
  whole kit works without WeaponMod or a blade of your own. It can't be dropped and runs back to water 15 s after its last use.
  Every parry now counts toward ascending, not only a katana's.
- **At home in water**: in water or rain you're a fifth faster on foot and swim like a dolphin, Undertow is free, and you can
  surface anywhere rain is falling.
- Classes: `DarktideMaelstrom`, `DarktideBlade` (new), `DarktideFlow`, `DarktideMoves`, `DarktideTide`, `DarktidePower`,
  `DarktideState` (Flow in the save, the `TIDE` attachment, the Maelstrom's cooldown slot). `docs/darktide.md` updated.
- Compiled (main and client); not played.
