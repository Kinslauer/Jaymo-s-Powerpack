# Fishing extras: don't stand on the hook, solid or light hooks, leaps, turning a run, clean landings, a line that hangs
- Reference Mods patch 12 (Better Fishing's and Enchanted Fishing Line's ideas, our own code).
- **Don't stand on the hook** (F1): standing right over your hook (on a bank, a jetty or a boat, within 2.5 blocks
  across) or splashing about in the water within 10 blocks keeps the fish away, as wading near it already did. A soft
  ring of ripples shows round the hook, to you only, while you're too close. A hook down an ice hole is still fished from
  beside it. `FishBite.spooks`, `STAND_OVER`, `SPLASH_*`, `RIPPLE_*`.
- **A solid hook or a light one** (F2): strike within a second of the take and the hook's set solid; later, it's only
  lightly hooked, and a light hook slips out if the line goes slack for a second and a half before the fish is nearly
  in. Rod pods' rods strike themselves, solid. `FishBite.SOLID_STRIKE_TICKS`, `FishingReal.SLACK_TENSION`,
  `LIGHT_SLIP_TICKS`.
- **The leap** (F3): a fish of 3 kg or more (or one of the big kinds) leaps clear of the water, higher than a small
  fish's hop; keep reeling through the leap and the line snaps. `FishRun.LEAP_KG`, `JUMP_UP_BIG`, `LEAP_GRACE`.
- **Turn against the run** (F4): look (pull your rod) the other way to a run and it tires the fish faster.
  `FishRun.against`, `ENERGY_AGAINST`. The fight's hint line says so.
- **An angler's log of bests** (F5): the journal keeps how cleanly your best of each kind was landed, one to three stars:
  one off for a light hook, one for taking the line past 85% of its breaking point, one for letting the fish run out
  most of your spool. Record fish already hang on the trophy plaque. `FishLog.Entry.bestClean`, `FishingReal.clean`.
- **Line that hangs like rope** (F6): a slack line sags between rod and hook, into the water if that's under it, and
  lies over the branches, rails and banks it crosses; drawn tight by a fish, it straightens. A tight line run through a
  tree's leaves or logs snags, and held hard there for a second it breaks. `HangingLine` (client),
  `FishingHookRendererMixin`, `FishingReal.snagged`, `SNAG_*`.
- Built in CI; not played. The numbers (how close spooks, how fast a light hook slips, how far a line sags) want a try
  on the water.
