# Rig Octo Core: the tendrils' controls, Core Only, a full fold, hidden plates come off
- **R and G are the upper arms** while the arms are out (Andy: the arm controls "should be the same as the tendrils"):
  R is the upper right arm and G the upper left. Hold one on a creature, player, item or corpse to grab it; let go to
  hurl it down your look. Sneak + R or G at a wall or the ground rips a chunk out (left click smashes the right arm's
  chunk, right click the left's). Each arm holds its own thing, so two at once. It's the tendrils' own grab, hold, tug and
  throw, drawn on the claws with iron and piston sounds instead of cords and slime. R and G come from any power while
  the arms are out, unless a symbiote's tendrils are out too (then they're the tendrils'). Hover and the palm blast stay
  in the Rig menu.
- **Left click runs the tendril combos** with two things held: clap, crush, shatter, thrash and barrage. On a barrage
  the lower pair stabs. The arms' own jab, cross and blast is skipped while they hold something.
- **Arm Walk is the full Wall Crawl now.** It crawls walls (forward or Jump climbs, Sneak or back goes down, strafing
  goes sideways) and ceilings (Jump under one, or Sneak by one, takes you up; the movement keys crawl you upside down;
  Sneak again drops you). A Sneak press at a wall or ceiling slams all four claws in (the blocks crack, with a clank).
  Over the top of a wall you hop onto the ledge. Holding Jump with nothing to climb (or sprinting) coils the arms low,
  and letting go flings you down your look, further the longer you coiled. It works in water. Without Arm Walk, a wall
  is no longer climbed just by having the arms out.
- **Core Only** (Rig menu, Octo Arms group): every other Rig piece you wear blasts off and parks, as a sent-away suit
  does, leaving only the Octo Core and its arms. Pick it again to call them back. The pieces that need the full set
  (hover, palm blast and the rest) stay locked while only the Core is on.
- **Folded arms collapse completely** into their mounts, with nothing left sticking out.
- **Hiding a Rig piece** with the inventory's eye button now takes its plates off with the suit-removal animation and
  its hisses, and showing it builds them back on. A hidden Octo Core keeps its reactor on the chest, lit, and the arms
  mount on the reactor and reach out from the front, still drawn and still working.
- Classes: `RigOctoClient` (`routeKey`, `armKey`, the crawl and the coil, `MOUNT_FRONT`, the full collapse),
  `RigOctoWalk` (ceiling claws), `RigOcto` (`CORE_ONLY`, `onCeiling`, `coreOnly`), `RigCall.coreOnly`, `RigSuit`
  (`REACTOR`, hidden pieces retract), `RigMenuScreen`, `WebTendrils` (`keys`, `machine`, `tickArms`),
  `WebTendrilCombos`, `WebLineRenderer`, `WebTendrilBundle`, `WebPowerClient`, `JaymosPowersClient`, `RigClient`.
- Built; not played.
