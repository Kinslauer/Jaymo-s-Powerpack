# Jaymo's Kingdoms: MODLOG

An original mod (own code, art and buildings) for Minecraft 26.3 (Fabric). Ideas inspired by Tale of Kingdoms, but
nothing taken from it (its code is GPL-3.0 and its assets all rights reserved).
Plan and the user's answers: `Origins Mod/docs/Kingdoms-Plan.md`.

## Testing
`run/` holds an offline dev server (port 25597, RCON 25598, password `kingdomsdev`) and ops.json for the dev
client's `Tester`. Start `runServer --args="nogui"`, then `runClient --args="--username Tester --quickPlayMultiplayer
localhost:25597"`, and drive it with RCON.

## 0.1.0: stage 1
- **Kingdoms** (`Kingdom`, stored on the Overworld by `Kingdoms`): name, colour, capital (a village bell), king,
  members (rank and standing), treasury (emeralds), claimed chunks (7 x 7 round the capital), overlord, fallen,
  succession time.
- **Founding** (`KingdomEvents`): a bell within 48 blocks of a player with no capital within 160 becomes one: a name,
  a colour and its banner by the bell, its land, a king and 5 guards.
- **Folk** (`Folk`): guards (iron, sword; keep within 24 of the capital, fight monsters, anyone the kingdom counts
  hostile, and whoever hits one of them) and the king (300 health, netherite, a boss bar; rallies at 40%: strength,
  resistance, speed, a horn, his guards called in). Skins drawn by `tools/gen_folk.py`.
- **Reputation**, two systems: per player per kingdom (-1000..1000; hostile at -100: guards attack; welcome at 0)
  and per kingdom per member (standing → rank: peasant, soldier 100, knight 300, lord 700). Killing guards -40, the
  king -400 (+60 with every other kingdom), monsters on a kingdom's land +2 (+3 standing for members), deposits.
- **A fallen king** (one of three, at random): a successor in a day; vassalage to the killer's kingdom (if they have
  one); or ruin (the guards scatter, the land is lost).
- **Commands**: `/kingdom list | info [name] | pledge <name> | leave | deposit <n> | withdraw <n> (lords) |
  reputation | found (ops)`. Right-clicking one of the folk gives an audience (and the king offers a clickable pledge).
- Entering a kingdom's land shows its name and your reputation.
- Verified on the dev server: a kingdom founded at a desert village (king + 5 guards), pledge, info, reputation.

## 0.1.1: keeps
- **Every new kingdom builds a keep** (`Castle`) beside its village: the flattest spot 18-40 blocks from the bell
  with the fewest village buildings in the way, its gate facing the bell. A foundation under it, the air cleared.
  Walls 10 high (weathered: mossy and cracked), battlements, four taller corner towers, a 3 x 4 arched gate, arrow
  slits, an upper floor (ladder up).
- **The throne room**: a carpet aisle in the kingdom's colour, steps up a dais, a quartz throne with gold arms and
  back, four wall banners in its colour, torches and a hanging lantern.
- **The panic room**: under the dais, walled in polished deepslate (blackstone bricks in a desert), reached by a
  trapdoor flush with the dais beside the throne and a ladder; the treasury chest is there.
- Desert and badlands kingdoms build in sandstone.
- **Who's where**: the king on his throne (or a third of the time hiding in the panic room), keeping close to it;
  two guards in the throne room, the rest at the gate. Keeps are stored per kingdom (`Kingdoms.KEEPS`).
- Verified on the dev server: a plains kingdom's keep (throne, arms, trapdoor and ladder, banners, carpet), the king
  on his throne.
