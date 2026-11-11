# downed animations for every kind of body
- Andy: "can every mob type get its own set of at least 2 downed animations?" Each downed mob now gets one of at least
  two ways to lie for its kind of body, picked by the mob (it keeps the same one each time it goes down):
  - **People**: crawl face down (as before) or shuffle along on their backs feet first, heels digging in and hands
    pushing at the hips; lying still, on their backs (as before) or curled on their side, knees drawn up and arms
    wrapped over the middle, shuddering in their fits.
  - **Machines built like people**: on their backs shorting out (as before) or sat slumped and powered down, legs out
    ahead, folded over them, faults jerking the limbs and snapping the head up. Other machines topple onto their side
    or pitch onto their front.
  - **Beasts** (wolves, horses, cows, bears, modded animals): on their side paddling (as before, now lifting the head
    and twitching the tail) or sprawled on the belly, forelegs out ahead and hind legs back, head on the ground, now and
    then pushing the front up and sinking back. A crawling beast drags itself on its forelegs instead of sliding along
    on its side.
  - **Bugs** (spiders, silverfish, bees): flipped on their backs with legs curled up and twitching, scrabbling and
    rocking to right themselves, or flat on the belly with legs splayed, jerking one by one; crawlers haul on their
    front legs.
  - **Birds and flyers**: fallen on the side, one wing beating feebly, legs drawn up, or belly down with wings spread
    flat, beating at the ground and hopping in their fits.
  - **Fish and water life**: flopping on their side in bursts that tire over a minute, or belly up (always in water,
    bobbing and drifting).
  - **Slimes**: melted flat and quivering, or squat and hiccuping with hops that go nowhere.
  - **Golems, creepers, blazes and other lumps**: toppled on the side shuddering, or pitched onto their front.
- Every pose turns the whole body first, so modded models we don't know still lie right; legs, wings, head and tail are
  posed where the model uses vanilla's part names. Fresh Animations still steps aside for them.
- `client/wounds/DownedPose` (kinds and styles, the person and machine poses), new `client/wounds/DownedBodies` (the
  rest). Tuning numbers at the top of both.
- Built (compileClientJava); not played.
