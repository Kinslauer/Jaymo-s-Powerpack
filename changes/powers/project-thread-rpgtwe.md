# no walking on a missing leg - cane, crutch, and hauling up a block on your belly
- Andy: "you shouldnt be able to walk upright with no leg, unless you make a cane or crutch". A player with a leg gone
  and nothing fitted in its place drops to a crawl and stays there (as with two broken legs). A wooden or iron leg
  still walks as before.
- **Walking Cane** (a plank over three sticks: `PS / .S / .S`) and **Crutch** (six sticks and a leather pad:
  `SLS / SSS / .S.`): held in either hand, one stands you up on the leg you have left (you rise by yourself when it
  comes to hand; put it away and you go down again). The cane walks at the pace a missing leg already cost (55%), the
  crutch at 70%. With both legs gone and bare neither helps: fit a leg.
- A legless crawl is hauled by the arms, so it keeps 85% of a crawl's pace with one leg gone and 65% with both
  (the walking penalty used to carry over into the crawl).
- Andy: "crawling players should still be able to haul themselves up one block". Every crawling player (by the key,
  the stance wheel, broken legs, a missing leg, or downed and awake) now goes up a full block by crawling into it, then
  moves at 30% pace for 16 ticks and pays a little food.
- Classes: `core/wounds/Crutches` (items, `legless`, the tick), `Breaks.crawlOnly`, `Crawl.tickHaul`. Art and JSON from
  `tools/gen_crutches.py`. Transformed Xenoforms and creative players are not held down.
- Compiled; not played. The third-person hold (the stick hanging from the fist to the ground) is set by eye from the
  numbers and may need its angle tuned.
