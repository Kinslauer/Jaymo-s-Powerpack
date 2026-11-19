# Gnomes: real gnomes with their own model, gardens and temper
- **Gnomes**: the "Gnome" and "Angry Gnome" zombies in player heads that ATi Structures' gnome huts and Grim Kingdoms'
  gnome towers place are now real gnomes. Each one turns into ours where it stands (from a new structure, its spawner or
  an old chunk), keeps its shield, and that spot is its house. Our own model (`kingdoms/tools/gen_gnome.py`), not
  copied from either mod: a tall red cap that flops back, a white beard, a big nose, a blue tunic, a stick, and a plank
  shield on some. It has idle, walk, run, bonk, gardening, digging and hurt animations, and a high villager grumble for
  a voice.
- **Neutral until wronged**: walking past is fine. Stand in their flowers or farmland, break a block near their house, or
  open one of their chests, barrels or pots, and every gnome in the house comes at you for a minute. A gnome with a
  shield takes half of a blow from the front.
- **Gardens**: by day each gnome now and then kneels in the beds round its house and works bone meal in, so the
  flowers spread (up to 48 plants). Strip a garden that once grew and the gnomes dig down into the ground and leave.
- **Gifts**: seeds, saplings, flowers or bone meal (`jaymoskingdoms:gnome_gifts`) calm a gnome that's angry at you.
  Otherwise it trades a stone-gift: raw iron, an emerald, or now and then a diamond. Each gnome trades once every 5
  minutes. A gnome drops bone meal and iron nuggets when killed. Spawn egg included.
- Lore: "The gnomes" (Andy approved 2026-11-19). Classes: `beasts/Gnome`, `beasts/Gnomes`, `client/beasts/GnomeRenderer`.
- Built; not played: the swap, gardening, anger and gifts haven't been seen in game.
