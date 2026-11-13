# falling animations - many falls, picked per fall, for every humanoid
- **Who**: players and every humanoid (villagers, MCA folk, Kingdoms' folk, illagers, zombies, skeletons, piglins...) now
  animate as they fall, seen by everyone. Vanilla-model villagers, witches and wandering traders kick their legs and lift
  their folded arms; illagers use their own arms. Fresh Animations steps aside for a falling mob, as it does for a downed one.
- **Thirteen animations**, one picked at random each fall (mirrored left or right at random too), matched to the drop:
  - Short drops (under 12 blocks): arms flung out wide (Fresh Animations: Player Extension's fall, remade), running on air
    (Not Enough Animations' fall, remade), knees tucked for the landing, the old windmill, or a grab back up at the edge.
  - Longer falls: the panic first (windmill, air run, arms out, a flop onto the back, a grab at the edge), then about
    0.6 s before the ground the brace: upright, legs together, arms crossed over the head.
  - Long falls (48+ blocks, off the Aether's islands, or no ground in sight): 1.6 s of panic, then one of the long-fall
    shapes: a skydiver's arch (our Sky Dive move's look), an end-over-end tumble (the thrown body's tumble), a flat
    spin, a curled-up roll, a pencil drop, or gone limp like the ragdolls; after 5.5 s it changes to another, and braces
    before the ground. Each turns into the next over 0.4 s.
- Wind users and Leap of Faith still dive head first (unchanged). Nothing changes for anything else that has the body:
  gliding, flying, elytra, riding, ladders, water, slow falling, ledge hangs and climbs, freerun moves (wall runs, bar
  hangs, Sky Dive), landing rolls, being carried or thrown, held by a web, symbiote, Lift or a vampire's grip, downed,
  limp as Kitchen's ragdoll. Someone carrying a body only kicks their legs. On landing it eases out over 5 ticks.
  Your own body isn't tipped over in first person.
- The fall no longer turns the hat layer twice as far as the head.
- Code: `FreefallPose` (the tracker, the picker and the `Style` animations), `PoseTakeover.mobPosed`.
- Built; not played.
