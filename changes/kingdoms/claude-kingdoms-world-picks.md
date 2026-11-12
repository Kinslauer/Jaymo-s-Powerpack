# Hunters' packs, the Gaunt and its curse, blighted wolves, night shapes, crews, a lasso, new spells, trophy stands and great foe music

**Trades**
- **Hunter**: a pack wolf that is always with you and is replaced two minutes after it falls (two for a Master). Use a bone on a beast to mark it: it glows and takes a quarter more from you and the pack. A bone used in the air sends the pack; sneak + bone calls it back. From Journeyman the pack follows the quarry out of sight, mends itself, fetches loose drops, and a goat horn marks and slows every beast near.
- **Hunter's bow** (Journeyman): hold a full draw on a beast for a beat for a bullseye that hits harder and pierces. Marked and bullseye kills give extra cuts when the carcass is butchered (or better drops without Jaymo's Kitchen). The bow draws twice as fast from the saddle.
- **Farmhand**: breaking a grown crop re-sows it (not in winter). From Journeyman an empty hand tends a crop on. Tamed animals hit harder near their farmer.
- **Smith**: built golems hit harder near a smith. From Journeyman, sneak + use a lit campfire with a pickaxe or hammer to raise a field forge: a minute of slow mending and quicker hands for your side, once every five minutes.
- **Lords**: sneak + goat horn calls your realm's guards, archers and golems to you for half a minute.
- A Master of any trade gets better prices from villagers in the town they work in.

**The wild**
- **Blighted wolves**: wolves that den near the Irradiated Giant's monolith change. They are tougher, hunt at any hour, and their bite carries the sickness.
- **The sickness**: lingering round that monolith makes you ill in three stages. The Potion of Cleansing, a healer, Mending or Restoration clears it; honey or wild garlic eases it.
- **The Gaunt**: a great foe of the snowy woods. It comes at night, is drawn to lit campfires, stalks before it rushes and is gone by day. Its bite can carry a curse. It drops its heart.
- **The curse of the hungry** (from the Gaunt's bite, eating its heart, or rotten flesh eaten in snow at night): at night you run on all fours, pounce from a crouch and pin what you land on, and crouch by the corpse of your own kill to feed a gauge. Full, you grow huge for thirty seconds. Ordinary food feeds you poorly, villagers run from you at night and daylight weakens you. Cleansing cures it and death ends it. `/kingdom hungercurse curse|cure|feed` for operators.

**Night folk**
- Vampires gain shapes on sneak + O: **bat** (small and flying), **colony** (bats lift a foe and drop it), **mist** (nothing hurts you and you hurt nothing), a **stare** that walks the weak-willed to you, and a **riposte** (crouch just before a blow and it finds only bats). Each costs blood. Sneak + O no longer dashes; plain O still does.

**Outlaws**
- **Crews**: barbarian camps now have ranks: plain raiders, one or two strong ones, a heavy, and a chief with one of four styles (the Knife, the Hammer, the Archer, the Poisoner), each with its own move. The nearest court posts a bounty under the byname.
- **Stealth perks**: Watched (damage builds while you keep an unaware target in your sights), The Stare (closes a target's sight in), Slip (tap sneak just before a blow to end up behind the attacker), Body Count (hidden kills heal).
- **Thief perks and knife forms**: Disarm Rush, Lift Weapon (pickpocket a held weapon) and Dry Remark (the faint-hearted drop what they hold).
- **Lasso**: throw it to hold a creature on a rope; use again up close to wrap it and lead it. Strong things break out sooner and great foes shrug it off. Players can only be held, and only where PvP is on.

**Sorcery**
- Three spells by scroll, staff, tome, the J key or the skill bar: **Bulwark** (earth: a dome that turns shots and shoves foes out), **Tide Pearl** (water: drags a foe to a ring at your feet) and **Harrying Gale** (air: hops a foe about, hurting it each time).
- **A forbidden rite**, learned from a rare Stitched Page: a Blood Sorcerer can unmake a bound captive into a brute that obeys for a moment and then turns on everyone. It is the heaviest crime there is and the captive's town remembers.

**Great foes**
- **Trophy stands** (wood, stone): every great foe drops a trophy. Set it, a head or anything else on a stand; the plate names the foe only if you know it.
- **Themes**: the four Giants, the Gaunt, ogres, wraiths, werewolves, wild dragons and warlords each have their own music, built from short layers so it never loops, rising as the foe weakens. It follows the Music slider.

Classes: `HunterPack`, `HunterBow`, `SettlerKit`, `FieldForge`, `Rally`, `Crews`, `remnants/Blight`, `hunger/` (`Gaunt`, `Hunger`, `HungerCurse`), `nightforms/`, `RogueGambits`, `forms/Lasso`, `forms/RogueForms`, `SorceryPicks`, `BruteRite`, `monsters/MadeBrute`, `trophies/`, `music/FoeThemes`. Art and sound from `tools/gen_hunger_picks.py`, `gen_rogue_picks.py`, `gen_sorcery_picks.py`, `gen_trophy_picks.py`, `gen_foe_themes.py`.

Left out: the cooked-meat bullseye and the ghostly crossbow (a Master's second wolf stands in), the thrown pitchfork (Strength's spear already pins), a wolf shape for vampires (the wolf-cursed own the beast), and the beam and ring-of-lightning spells, which already existed.

Compiled only; nothing was played. Least sure: the bat shape and its flight, the all-fours pose, how MCA villagers take the crew gear and the rally, the hero's-price effect through MCA's trade button, the new perks' places on the skill web, and how the synthesised themes sound.
