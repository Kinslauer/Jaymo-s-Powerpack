# a prompted finisher for each power
- **Power finishers** (Andy: "a qte finisher per power per enemy type"): a power holder with empty hands finishes a
  downed foe with the power itself, by the usual sneak + hold (or sneak + double click and hold), from anywhere round
  them. It replaces the neck snap for them. Each is two blows, and each blow asks for one of your ability keys first; a
  wrong key or none throws you off and a downed mob gets back up (a downed player stays down).
  Pocket Arsenal **Whatever Answers**, Chronokinesis **Borrowed Hour**, Cryokinesis **Deep Frost**, Darktide
  **Undertow**, Gravity **Dead Weight**, Resonance **Breaking Pitch**, Smoke **Last Breath**, Web Slinger **Cocoon**
  (no suit showing; the suit keeps its executions), Wind Control **Emptied Sky**, Xenoform **Star-Metal**. The Z Fighter
  keeps his own executions, unprompted.
- **Per body kind**: the effect is sized and aimed by the body's own box, and the kill breaks each of the eight kinds
  its own way: people and beasts bleed, machines spark, bugs are crushed flat under Dead Weight and Emptied Sky, winged
  things shed feathers, fish thrash water, slimes burst, lumps raise dust.
- Classes: `core/finisher/PowerFinishers` (kinds `Finishers.Kind.P_*`, `Strike.POWER`), three shared poses in
  `FinisherPose.powers`, lang part `power_finishers.json`. Lore: "How a power ends a fight" in Lore.md, for Andy to approve.
- Compiled; not played. The player's own pose is the same for every body kind, and three poses are shared between the
  ten powers; the looks were set by eye.
