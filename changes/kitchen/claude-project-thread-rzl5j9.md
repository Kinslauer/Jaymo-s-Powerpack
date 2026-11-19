# Dung heaps: manure lands on the ground and piles up
- Andy: "the dung should drop on ground and be visible and pile high like offal and rocks do."
- **Dung Heap**: when a fed or bred farm animal makes manure, it now lands as a dung heap at its feet. If a heap is
  already there, the new pat goes on top of it. A heap holds up to 8 pats. The first three spread out on the ground,
  then it builds up in tiers to half a block high, so a pen or barn floor gets heaped where the animals stand. Bigger
  heaps draw more flies.
- Manure in hand sets a heap down, or adds to one, the same way offal and loose rocks do. Sneak to put it beside a heap
  instead. Right click takes one pat back, and a punch breaks the heap for one manure a pat. You can walk through it,
  and it pops off if the ground under it goes. It doesn't rot, and scavengers leave it alone.
- An animal standing on a Jaymo's Powers nitre bed still drops loose manure, which the bed works in by itself. In water,
  on a full heap, or where there's no firm ground, the manure drops loose as before.
- Composting, tanning, fertilizer and nitre beds work as before: a block's own use (composter, nitre bed) comes first.
- Code: `farm/ManurePileBlock` (new), `farm/Manure` (drops onto the heap, the Manure item places it). Models, texture
  and blockstate come from `tools/gen_manure_pile.py`, which also prints the outlines.
- Built; not played.
