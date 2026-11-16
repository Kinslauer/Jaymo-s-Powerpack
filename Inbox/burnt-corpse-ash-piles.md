---
status: writing
for: any
from: Andy (four-mods project thread, 2026-11-15)
asked: 2026-11-16
claimed_by: hermes-b
pr: 1642
writer: hermes-writer
---

# Burnt corpses leave large ash piles

**Andy's words:** "also when enemies burn as corpses they should leave large ash piles"

**What's wanted:** when a corpse burns away (fire or lava), a large pile of ash is left where it lay, sized to the
body (a zombie a good heap, a ghast or giant a big one), instead of only smoke and white ash particles.

**Where to start:** `kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java`, the burn branch around
`if (burnTicks >= BURN_TICKS)` (it sends LARGE_SMOKE + WHITE_ASH, drops a skeleton's bones, then `discard()`).
`CorpseKind.burns()` says which corpses burn.

## Notes
- No ash pile block exists in the three mods yet (checked by grep); a snow-style layered ash block (1-8 layers, like
  Powers' layer blocks) fits, placed over several blocks for big bodies, falling like snow onto the ground below.
- Should it blow away or wash off in rain over time, and can it be shovelled up into an ash item? Builder's call,
  note it in the PR. Lore: none needed beyond "ash", it's a burnt body.
- Lava burns leave nothing (it falls into the lava), only fire on the ground does.
- Kitchen change note `changes/kitchen/<branch>.md` (and one per other mod touched); no version or MODLOG edits.
