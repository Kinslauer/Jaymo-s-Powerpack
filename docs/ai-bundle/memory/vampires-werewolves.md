---
name: vampires-werewolves
description: Vampires + werewolves drop, PR #129 MERGED 2026-10-14 as pack 0.10.3 / Kingdoms 0.7.0
metadata:
  type: project
---
2026-10-13 Andy: 0.10.0 is about vampires and werewolves (elder vampire villager, bite spreads vampirism, feeders/abstainers/hunters; lycanthropy with size variants and a grotesque full-moon change; players: vampire by bite, werewolf by ritual). Plan: docs/Vampires-Plan.md (also /mnt/project-files/vampires/).
Stage 1 built in Kingdoms `Vampires` (draft PR #129, branch claude/vampires-werewolves-culz0i): Elder from day 6, bitten survivors turn at next nightfall, exposed vampires hunted by guards. 23:12Z Andy: "The elder can use a bat form" (built: flies to prey 12-48 blocks off, flees as bat).
Stage 2 (Lycans: cursed villagers/folk change each full moon into the legend Werewolf with own size/build/coat, grotesque morph) and stage 3 (VampirePlayers: bite, drink sneak+use, cure removed; WerewolfPlayers: bone ritual in 4 campfires on a full moon, U at will after 3 moons, no-AI puppet stands in) all built 2026-10-13 ~23:30Z, compiled, not playtested. 2026-10-14 Andy: werewolf change tears 30% of each armour piece's durability, then it breaks or falls off; cure for both = rare priest quest (Cures: 1 in 4 clerics, slay 2 vampires / 1 cursed werewolf). Built, pushed. Open questions in the plan use bracketed defaults.
Related: [[lunar-events-omens]], [[cloud-build]].
MERGED 2026-10-14 03:45Z as pack 0.10.3 / Kingdoms 0.7.0. Screenshots of wild vs cursed werewolves: /mnt/project-files/vampires/screens/ (cursed beasts reuse the wild werewolf's 3 coats; Andy may ask for new cursed coats to be drawn; the change is mostly hidden by smoke).
2026-10-14 Andy: cursed werewolves get own coats (mangy/scarred/patchy x 3 coats) + torn clothes tinted by trade (RagsLayer, tools/gen_cursed_werewolf.py): PR #132 MERGED 2026-10-14 04:31Z (pack 0.10.4 / Kingdoms 0.7.1).

2026-11-05 Andy: werewolf bites should curse players too; Jung's beast still held a knife and suits/costumes showed on it. PR #999 MERGED 2026-11-05 09:5xZ (Kingdoms 0.7.574): Lycans.bite curses surviving players (40%/hit, first change next full moon, far howl only), player beasts bite too; strip/stow fix (item went back to selected slot); BeastHidesPlayerMixin skips drawing the beast-form player. Wild werewolves still spread nothing.
2026-11-05 Andy: Werewolves: Become a Beast (TeamLapen, art Vampirism-only, can't use) inspired 3 paths; Andy: "should be a variation that you can achieve, the mostly human is what happens now during a ritual" (= wolfblood). Built lean wolf in PR #999: sneak+U after mastery with FeralCurse stage <=1 (my default; decision card asked how to earn it), LeanWolfModel + tools/gen_lean_wolf.py coats, hint 111.
2026-11-05 17:4xZ Andy: Jung invulnerable as werewolf. PR #1035 MERGED 17:45Z (Kingdoms 0.7.599): player beast no longer Invisible (mobs ignored invisible players; body/arm hidden by BeastHidesPlayerMixin/BeastHidesHandMixin via Gift.beast = torn out), and puppet beasts skip Werewolf.HIDE x0.25. Not played.
