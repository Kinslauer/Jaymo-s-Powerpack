# Youth, the wood-wisp
- **Youth** (new, never spawns by itself): a small, see-through, glowing Sylvan with two pairs of fae wings and a trail of
  light for legs. Hermes (Andy's YOUTH bot) calls it to a player who asks a question in chat and answers through it. It
  appears beside the player in a swirl of motes, faces whoever is nearest, and wanders between 8 GeckoLib idles (hover,
  flutter, loop-the-loop, sitting on the air, stretch, peer, twirl, cupping a little light) with a pause in between, never
  the same one twice running. It gestures while it talks. Right-click it for a hint on how to ask.
- It can't be hurt, pushed or fought, is never saved with the world, and fades away after 2 minutes with no question,
  when its asker walks 16 blocks off, changes dimension or logs off. One per player, 4 at most. `/kill` removes it.
- **`/youth`** (gamemaster level; sent by Hermes over RCON): `summon <player>`, `say <player> <text>`, `sayall <text>`,
  `dismiss <player>`, `status` (one line of JSON: players online with their account and display names, where, and who has
  a wisp).
- Classes: `inhabitants/YouthWisp`, `inhabitants/YouthCommand`, `client/inhabitants/YouthWispRenderer`. Model and
  animations: `kingdoms/art/youth_wisp.bbmodel` from `kingdoms/tools/gen_youth_wisp.py`. Lang: `lang/parts/youth_wisp.json`.
- Built; the dev server boots and `/youth status` and `/youth sayall` run. Not seen in game: the look, the animations,
  the translucency and the summon/say flow with a real player are untested.
