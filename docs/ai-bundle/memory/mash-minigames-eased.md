---
name: mash-minigames-eased
description: Andy 2026-10-26: mashing minigames were overdone; eased across the pack and beam clashes weigh power levels (PR #530 merged 2026-10-26)
metadata:
  type: feedback
  modified: 2026-10-26T01:19:03.802Z
---
2026-10-26 Andy: symbiote and beam battles "near impossible to break without a SERIOUS struggle, which I appreciate, but this is overdone"; factor power levels into beam clashes; transforming mid-clash should count.
Done (thread "Ease mashing minigames and beam battles", pack 0.17.1 / Kingdoms 0.7.216 / Kitchen 0.5.30):
- BeamClash: taps worth more (0.009, fade 0.9, ~6 taps/s fills the surge), taps + surge cap weighed by sqrt(power ratio) 0.75-1.35; Z Fighter ki wave reads ki power live, so going Super mid-clash pushes harder at once and fills the surge.
- Eased by ~30%: symbiote takeover break-free (55 - 5/ability, fade 0.18), suit-removal fight (22-9), Great Ape fits, blood fever, feral hunts/changes (Kingdoms), dough kneading (15).
**Why:** Andy wants a struggle, not an exhausting one.
**How to apply:** new mash minigames should land near these numbers; keep a struggle, don't make it trivial. Related: [[blood-fever-feral-curse]], [[zfighter]].
