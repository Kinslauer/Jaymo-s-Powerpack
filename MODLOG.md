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
