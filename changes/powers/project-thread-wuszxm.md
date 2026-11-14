# Chrono: quickened rocks - a thrown rock goes like a bullet
Andy: "chrono should be able to accelerate thrown rocks to an insane fps. almost becoming a bullet."
- **Quickened rocks**: a loose rock thrown from the off hand while Overclocked (Velocity too, as with the rest of the
  speedster's hands) leaves at 6 blocks a tick (120 blocks a second, four times a normal throw), dead flat with no drop
  and no stray, and hits for 12 (a plain rock is 3). Zombies and Hornkin don't shrug it off. Cooldown, momentum
  and knock-outs are as before.
- **The charge** (Andy: "only chrono has to charge, and it lessens w/ mastery"): in Velocity the rock goes the moment
  use is pressed. An overclocked Chrono still winds up, 3 ticks less for each ability learned and each Mastery rank:
  1.5 s with only the starter, 0.9 s with all four abilities, 0.15 s at Paragon (momentum still shortens it further).
- A rock caught out of the air (use, at speed) and hurled back goes quickened too, a guard's or barbarian's included.
- **Look and sound**: a gold ring across its path and a sharp crack as it goes, a thin gold streak behind it the whole
  way, a gold burst where it strikes. It always shatters (never lies there to pick up), with more specks and a deeper
  crack in the block. One that hits nothing is gone after 2 seconds (about 240 blocks).
- Tuning: `ChronoRapid.ROCK_SPEED`, `ROCK_DAMAGE`, `ROCK_LIFE`, `ROCK_CHARGE_STEP`, `ROCK_CHARGE_MIN`; `ThrownRocks.QUICK_SPECKS`, `QUICK_CRACK`.
- Classes: `ChronoRapid` (quickens, quickened, rockStreak, rockStruck), `ThrownRocks.quicken`. Compiled; not played.
