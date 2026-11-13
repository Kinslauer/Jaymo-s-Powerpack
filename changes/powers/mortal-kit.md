# Fighting without a power

- **Four moves for anyone with no power**, on the four ability keys (R, G, V, B by default). Each names itself on the
  action bar when used and shows the seconds left if pressed too soon.
  - **Power Strike (R):** one heavy blow at whoever is in front of you: 4 damage plus three quarters of what your hand
    already deals, and a hard shove. 8 seconds.
  - **Sweep Kick (G):** takes the legs out from under a person-shaped foe, flat on its back for 2.5 seconds, nothing
    broken. Anything else is kicked and slowed. 12 seconds.
  - **Second Wind (V):** at or under 60% health: 2 hearts back at once and Regeneration II for 5 seconds, slowness
    and weakness shaken off. 90 seconds.
  - **Battle Cry (B):** you and every player within 10 blocks get Strength for 10 seconds; lesser mobs that were coming
    for you (30 health or less, within 8 blocks) are weakened and slowed for 6. 60 seconds.
- **A falling attack for every kind of weapon.** Land your own blow after falling 2.5 blocks or more (not flying or
  gliding) and it hits harder by the fall (1 a block, up to 8), breaks your fall, and does its weapon's own thing:
  sword plunges; greatsword hits half again harder and catches everyone near; dagger goes through armour; axe hits half
  again harder and staggers; blunt weapons and flails send a shock that hurts and lifts everyone near; spears and
  polearms pin the struck in place; a staff shoves far; bare hands put the struck on its back. The vanilla mace keeps
  its own smash. Bows and guns have none.
- **Launcher, juggle and slam, only for those who can fight in the air:** Wind, Z Fighter, Smoke, Xenoform, Web,
  Darktide and Gravity. Sneak and hit something on the ground to throw it up and go up after it (6 seconds between
  launches; nothing over 3.5 blocks tall or that cannot be shoved). Each further hit before it lands bumps it up again,
  up to five times. A sneaking hit in the air drives it down: 6 damage where it lands, 1 more for each juggle, and 3 to
  whatever stands beside it.
- **Chain Axe.** An iron axe on a chain, forged at the Tool Station from a Chain Axe Head (three ingots of iron, cast
  or forged like any head), a Tool Handle and an iron chain. In the hand it is an axe. Right click throws it out nine
  blocks and hauls it back: whoever it meets takes four fifths of its blow and is tugged toward you; sneak as you throw
  and they are dragged all the way in. 1.5 seconds between throws.
- **The fight meter.** Hidden: nothing on screen shows it. It fills with the damage you deal and take against things
  that fight back, and drains 5 seconds after the last blow. **False Super and a Z Fighter's first Super breakthrough
  now only come up while it stands past 80%**, so they are earned deep in a real fight and not by standing in a fire.
  Not gated: the symbiote taking over to save itself, an Earthling's ki surge and adrenaline (they are help, not
  unlocks).
- **A cap on ability damage between players**, off by default. Set `pvp_ability_cap` in
  `config/jaymospowers-server.properties` to a share of full health (0.25 = no single ability hit takes more than a
  quarter). Plain weapon swings, arrows and tridents are never capped.
- The moves use the plain arm swing: none of this has its own animation yet. Compiles; not played in game.
