# Wind: Spiral Dive
- **Spiral Dive (Jung):** a Wind user with 2 or more abilities unlocked who taps X in the air, looking steeply down
  (50 degrees or more) with 6 blocks of clear air below, plunges in a spin with a spiral of cloud winding round them,
  steering a little with their aim. It works out of wind flight (the flight is dropped). Where they land a ring of wind
  runs out along the ground: everything near is shoved away and lifted, harder after a longer fall (full at 30 blocks),
  for light wind damage (2 to 5), and fire in the ring is blown out. Allies are spared.
- **Costs:** one hunger point, a 20 s cooldown (longer in the cold) that is spent even if the ring catches nothing, and
  the landing leaves the diver winded: rooted, no walking or jumping, for 0.6 to 1.5 s. Taking flight again more than
  half a second into the dive pulls out of it, with no ring.
- Fall rules are untouched: the dive only sets the diver's speed (as the Thunder Dive does), and it needs the same 2
  unlocked abilities from which a Wind user's falls stop hurting.
- **Changed for fully mastered Wind users:** a Tempest Kick aimed steeply down from 6 or more blocks up is now the
  dive. When the dive can't start (too low, cooling down) the tap is the kick as before. The Thunderhead's Thunder Dive
  stays on C.
- The X key's description mentions it. No new key, packet or client code: the tap is read in `WindKick`'s handler.
- Classes: `WindSpiralDive` (new), `WindKick` (the tap), `WindPower` (`dropFlight`, registration, stop on downed and on
  losing the power, `/powerdebug wind cooldowns`). `docs/wind.md` has the numbers and test steps.
- **Not built and not played.** The session that wrote it could not reach the build's download hosts, so
  `gradlew build` has not been run on this change; the PR checks are its first compile. No HUD chip for the cooldown.
