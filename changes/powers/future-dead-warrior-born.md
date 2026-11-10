# The future's dead are Warrior-born: mangled tails, 32 skins, dried husks

Andy: "husks zombies and player zombies all with mangled tails in various conditions (12+ at least) and 30+ different
skins".
- **Tails**: every grown zombie, husk and stranger's zombie player in the future drags a Warrior-born tail, mangled one
  of 16 ways: bent hard to one side, snapped and hanging from the break, cut through halfway, gone at the root, hanging
  limp, broken twice, the fur torn away in patches, the tip rotted to bone, charred and short, crushed flat, split into
  two strands, coiled tight, gone stiff, hanging by a strand, cut short with an old rag knotted on, broken with the
  bone through. Dead tails barely sway; a snapped one swings from its break. Each mob keeps its own, picked by its id.
- **Skins**: plain zombies there now wear one of 32 civilian skins (16 new outfits: leather jacket, tracksuit, chef's
  whites, paramedic, security, blouse and skirt, overalls, raincoat, dojo gi, bomber jacket, coveralls, lab coat,
  cardigan, puffer jacket, delivery uniform, fight trunks), and husks one of 32 dried skins of their own instead of
  the game's husk. About half carry a Warpainted's ochre bands or a Battle-scarred's old scars on the face.
- Babies, and a real player's zombie, are left as they were; nothing changes outside the future.
- `client/power/chrono/FutureDeadLooks` (skins, the tail's 16 conditions and its render layer), `FutureZombieRendererMixin`,
  new `FutureHuskRendererMixin`, `ChronoLivingEntityRendererMixin`. Art: `tools/gen_future_zombies.py` writes
  `textures/entity/future/civilian_<n>.png`, `husk_<n>.png` and `dead_tail.png`.
- Built; not played. The tail shapes were checked in a Python mock of the drawing code and the skins as images, not in
  game; check the tails' place on the back, the husks' skins and the zombie players' tails in the future.
