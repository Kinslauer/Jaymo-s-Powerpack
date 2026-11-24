---
status: done
for: claude-cloud
from: claude-cloud-pending-vault-thread
asked: 2026-11-21
claimed_by: claude-cloud
pr: 2090
thread: cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR
---

# Villagers visibly doing their jobs

**Andy's words (2026-11-21, thread "Show villagers visibly doing their jobs"):** asked whether blacksmiths can use the Meat Hook and whether villagers visibly cook anywhere; picked "Tools, forge, stove, hook (1-4)" on the build card.

**What's wanted:** you can watch a villager work, not just see a puff of smoke.
1. Tools in hand at work (smith hammer, butcher cleaver, cook knife or pan, fletcher knife, mason pick, librarian book).
2. Blacksmith at the forge: furnace lights and smelts, hammering at the anvil with clangs and sparks, holds up the ingot or tool, puts it in a chest.
3. Cook at the stove: raw meat sits on the campfire or Kitchen cookware and turns cooked; they stir the pot and set dishes on the counter.
4. Butcher at the hook on their own: each morning hangs a carcass from the town's stock, then bleeds, skins and cuts it through the day with the existing hook stages.
Later (not picked yet): 5. fletcher, librarian, cleric, mason, leatherworker, shepherd scenes.

**Where to start:** the work so far is commit 5fed07217 (on branch `claude/project-thread-7tm3tj`, reverted there afterwards so that branch could carry the farm placement fix) and as a patch in the vault, `Assets/villagers-visible-jobs.patch`: start a branch from master and `git am` it (compiles, not played). Kingdoms `WorkScenes.java` has rows 1 and 2 (tools in hand for every vanilla trade + the cook, marked with custom data `jaymoskingdoms_work` so they're never lost or dropped; smiths heat at the furnace, which is lit only while empty, hammer at the anvil with sparks, quench in a water cauldron). Row 3 is mostly there: `WorkScenes.cook` (called from `Meals.cookAt`) cuts at the counter, cooks at the lit smoker and lays the dish on the counter as an ItemDisplay plate; `Meals.step` puts home cooks' food in hand. Still to do for row 3: check the plate transformation renders flat. Row 4 not started (plan below). Then change notes for kingdoms and kitchen, build both, PR.

## Notes
- Row 4 plan (Kitchen `corpse/Butchers.java`): a butcher with no job in working hours, a free meat hook within 14 of their job site, takes an unclaimed animal corpse (not a player's kill: add a `killedByPlayer()` getter on CorpseEntity; not carried, dragged or hung, stage FRESH, `Hanging.hangable`) within 20 of the hook. Reuse `Job` with a nil owner and a new FETCH phase (walk to the body, `handTo`), then OUT/CHOP as for a paid job; when chopped the cuts go to a sink Kingdoms sets by reflection (`Storage.put` for the butcher's settlement), else a container by the hook. Cleaver in hand while chopping. Corpses to work: Kingdoms `Farmers.herd` should kill a surplus beast for real (generic damage, so the meat isn't spoilt) when Kitchen is loaded and a butcher with a hook is near, instead of the poof and meat in arms.
- 2026-11-21 23:40 Put on the vault by the cloud thread "Put pending work on the vault". Being built by cloud Claude in thread cmsg_017LSt6rWz6CzxSGvzDd41XWCtfvV54qFpSdJetNTeKw36; bots leave it.
- 2026-11-24 17:59 2026-11-24 Andy: "finish here": cloud Claude finishes this in thread cmsg_017LSt6rWz6CzxSGvzDd41XWRihgeu7KNw14feoHe7EEoR; bots leave it.
- 2026-11-24 18:50 2026-11-24 PR #2090: rows 1-4 built (row 4: Kitchen Butchers town work on the hook, Farmers slaughter surplus for a butcher). Compiles; not played.
- 2026-11-24 20:42 PR #2090 is merged into master; moved to Inbox/Done.
