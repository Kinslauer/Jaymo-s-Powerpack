# Decisions

Every decision Andy makes, newest **last**. Append with `Templates/Decision.md`. Standing rulings from before this
log started are in `docs/AI-HANDBOOK.md` section 7 (see [[Rules]]).

### 2026-11-06: project vault
- **Decided:** the vault lives in the repo at `docs/vault/`, committed and shared by every agent. Obsidian installed
  on the PC with winget.
- **Applies to:** all three mods; every agent session.
- **Agent:** [[Done/obsidian-vault-4d9705|obsidian-vault-4d9705]]

### 2026-11-06: agents work only in their own worktree
- **Decided:** every agent, Hermes included, works only in its own git worktree, never switches branches in a shared
  checkout, and never runs `git checkout -- .`, `git clean -f` or `git reset --hard` (Hermes lost unsaved work that way).
- **Applies to:** every agent session, every assistant.
- **Agent:** the project thread (PR #1100 review session).

### 2026-11-07: one live spot for agents and decisions
- **Decided:** agent notes and this decision log live in `E:\Origins Mod\docs\vault\Live\` (ignored by git), written
  there directly by every agent, so the board shows agents the moment they start and everything is in one place.
- **Applies to:** every agent session, every assistant.
- **Agent:** [[vault-live-board]]

### 2026-11-07: who bleeds what, and ground blood lies still
- **Decided:** Andy: "the emberborn should have the same type of cooling magma blood that the cubes have, and so should the
  magmavein stoneborn. all zombies should get the rotten blood, and the rest of the blood changes are good to go too. just
  no redstone textures ... the blood that falls on the ground shouldnt randomly sputter or splat or move unless directly
  interacted with." Endborn ender-purple, Divine gold, Verdant sap approved; corpses bleed their own blood.
- **Applies to:** Powers blood (`BladeBlood`, client blood), Kingdoms `PowersCompat.heritageBlood`.
- **Agent:** [[blood-heritage]] (PR #1109)

### 2026-11-07: magma bubbles, blood falls; Kitchen bleeds the body's blood
- **Decided:** Andy: "the magma should bubble as it cools throwing off those popping lava particles, the BLOOD should not.
  it can still move and fall and drip, like water does through blocks, but over ledges. this way a bleeding body above a
  ledge can drip a pool up to 20 blocks below it". Skeletons don't bleed; stump bleeds are red spurts, bigger once fallen.
  Kitchen's blood follows the body (checked at Andy's ask). Merged as #1109.
- **Applies to:** Powers blood, Kitchen `KitchenBlood`.
- **Agent:** [[blood-heritage]]
- 2026-11-07: ParCool can't ship (Forge/NeoForge only, LGPL); Andy picked 12 of its moves to remake ourselves (not zipline, grapple, energy drink), split into Inbox tasks parcool-ledge-and-wall-moves and parcool-running-and-falling.
- 2026-11-07: Andy: "the hermes reviews should be handled here": reviews of Hermes's Inbox PRs run in the Claude session "Train Hermes to work like the Claude threads" (vault-review skill), not in the coordinator thread.
- 2026-11-07: Andy: "review what the fixer sends you then send it to the coordinator, dont ask me for that, the coordinator will bring it to my attention and i can kick it back down the ladder if need be." The PC Claude reviewer runs vault_sync --send on a passing Hermes PR without asking Andy first. Also: three Hermes bots (hermes-a, hermes-b, hermes-fixer) set up as Hermes profiles with scheduled Inbox jobs.

### 2026-11-07: Critter Armory vs Fresh Animations
- **Decided:** "Build a compat fix" (decision card): fit Critter Armory armour to Fresh Animations models for pigs, sheep, horses.
- **Applies to:** Powers compat, task critter-armory-fit.
- **Agent:** coordinator thread.
- 2026-11-07: Andy, on the fishing send-back (metal rod, sharks, fish processing): "i phrased those as three separate things intentionally, its fine if they would have taken one each, next time break it up that way." When Andy lists separate features, the coordinator posts one Inbox task per feature (each its own branch and PR), not one bundled task.

### 2026-11-07: Realistic fishing, round 2
- **Decided:** Andy picked all four: fish by place and time (plus Kitchen bait working again), overfishing, real tuna and shark meat, fish size and weight. Tideborn react to fishing (attack, poaching crime in their havens) is built either way.
- **Applies to:** Powers fishing, Kitchen chopping, Kingdoms Tideborn.
- **Agent:** fishing-tideborn.

### 2026-11-07: Family farmhand's family jobs
- **Decided:** Andy picked all four: tend the crops, keep the animals, unlock MCA's chores (Work button), and the child gathers items into the house chest. Wary of zombies, hiding at night and last-resort defence are built either way.
- **Applies to:** Kingdoms FarmhandHome / FarmFamily.
- **Agent:** farm-family.
- 2026-11-07: Andy started a Hermes model trial: hermes-a (Builder A) on nvidia/nemotron-3-ultra-550b-a55b, hermes-b (Builder B) stays on nvidia/nemotron-3.5-super-vl-120b-a12b, hermes-fixer on nvidia/nemotron-3.5-lightning-30b-a3b. Compare review rounds per task (Kanban timeline) before keeping or reverting. Config backups in the reviewer session scratchpad.

### 2026-11-07: Fishing, all the fishing mods in one
- **Decided:** Andy wants every fishing mod's functions in one (not their realistic 3D rods). Minigame: "all three together" (strike window, charged cast, drag, fish runs, rod bend). Ship the built round now as one PR, then the tracks in `docs/Plan-fishing.md` (fight, tackle, fish, records) each as their own PR, two at a time.
- **Applies to:** Powers fishing, Kitchen, Kingdoms Tideborn.
- **Agent:** fishing-tideborn.

### 2026-11-07: One character menu on H
- **Decided:** Andy OK'd the mockup (tabs down the left edge: Character, Powers, Skills, Journal & Quests, Faction, Map & Waypoints, Party; old keys open their tab). The Journal tab keeps M's rule (needs a journal on you), "but everyone spawns with a journal now".
- **Applies to:** Powers (menu, H), Kingdoms (its tabs, starting journal).
- **Agent:** character-menu.
- 2026-11-08: Andy: Inhabitants (JeremySeq, MIT, Forge 1.20.1) is "going to be a kingdoms addition": port its Bogre, Impaler and Warped Clam into Kingdoms (GeckoLib is already in the pack), one Inbox task per feature. Andy wants a dedicated modelling model for the Bogre ("claude doesnt do the best with models").
- 2026-11-08: Andy wants Dungeon Conquest: Stormborn Tomb ("get them, we need it"). None of it or its deps (DungeonZ, Draugr Invasion, Supplementaries+Moonlight, FrogiLib) has a 26.3 Fabric build, so it becomes a Kingdoms port: MIT tomb structures (Supplementaries decor swapped), the DungeonZ-style no-build rule (MIT), our own Draugr-like undead on the Hollows (Draugr Invasion is ARR: nothing copied), our own tomb boss.
- 2026-11-08: Andy named the bots: Apollo = hermes-a, Bacchus = hermes-b, Hephaestus = hermes-fixer; the modeller (hermes-modeller, kimi-k3.1-pro) is Prometheus and makes the Bogre. Inbox tasks may say `for: <name>`; E:/hermes/powerpack/vault.py maps names to bots.

- 2026-11-08 (kitchen-rats): Andy chose to reuse the Rats mod's textures (LGPL-3.0, with credit) for Kitchen's rats, after seeing the look page.
- 2026-11-08 (kingdoms-geckolib-groundwork): Andy: the Stormborn Tomb reuses the Skeleton Lord (boss) and the silver skeleton (Dead Traveler model) instead of a new tomb skeleton and draugr lord, both ported to GeckoLib with improved animations; the new Draugr is built too (GeckoLib, our own art). The example GeckoLib mob is the barrow beetle. PR #1149.
- 2026-11-08: Andy: Hermes picks up a backup reviewer role — if the PC reviewer and the coordinator session are both down, Hermes reviews in-review Inbox tasks (build, pr_checks, review-lessons, the task's numbered list), sends failures back with numbered notes and `status: changes`, passes good ones with `--send`, and posts ready PRs to Andy; it never merges on its own.
- 2026-11-08: Andy: two bots may build at once ("make it so 2 bots can run at once"), changing the one-at-a-time rule in `E:/hermes/powerpack/vault.py` (`MAX_CONCURRENT_BOTS = 2`); a third bot waits for its next run.
- 2026-11-08: Andy: every bot shows its callsign as its primary name, like Quill — Apollo (hermes-a), Bacchus (hermes-b), Hephaestus (hermes-fixer), Prometheus (hermes-modeller). Set as `display_name` + Bot Mode title in each profile.yaml; profile ids unchanged.
- 2026-11-08 (mimic): Andy: "prometheus can pick up the mimic, and tell him I want that to replace the way the echos function". The player-copying Mimic from Tiv's ItsJim video reference replaces Chrono's echo mechanic; Prometheus supplies its original true-form model, texture and animations, with gameplay handled separately.
- 2026-11-08: Andy wants to see when Hermes bots stall or stop. Added E:/hermes/scripts/bot_health.py: Kanban shows one card per bot (Blocked = stalled 30 min / missed 2 runs / 2 failed runs) and each claimed task's last change; cron job bot-alerts (default profile, every 5 min) DMs Andy new problems; Windows task "Hermes Gateway Watchdog" restarts the gateway if it isn't running (pause it with E:/hermes/scripts/watchdog.off).
- 2026-11-08 (rider-class): Andy: "add everything you thought up, and everything in this wheel should be available to riders", "immediately not exclusively": Riders get all eight Rider ideas, and their own horses obey every horse-wheel order, roster call and whistle at any trust; other players keep the trust rules. PR #1161.
- 2026-11-08 (five mods pick-list): Andy ticked the list. Realistic Water: "we can try it" - he accepts that the block physics comes with the volumetric simulation, since they are one system. Streams Reflowing keeps rivers and lakes ("streams wins obviously, i just want the blocks"). Simple Difficulty: parasites for bad water, plus treatment methods for them; permanent hearts found in the world are wanted. Tinker's Delight: knives, melting cookware back down and splitting cakes/bricks yes, the food buff for tinkered gear no. Dynamic Trees stays parked until a 26.3 Fabric build exists. Worldbound gets one pass for gaps.
- 2026-11-08 (rider-class): Andy: a Rider's starter horse tops out only slightly faster than the fast run ("the sprint you break into after a few seconds of running"); it opens up at max racing mastery, so Riders still want better horses. PR #1161.
- 2026-11-08: Andy approved adding the ore hearts ("i approved adding the ore hearts, send it to the modeller"). Art (heart crystal ore in stone and deepslate, the heart crystal item) goes to Prometheus as task ore-hearts-art; the code side stays with hermes-b on ore-hearts. Andy's two design calls (survive death, the cap and where it shows) are still open and are asked in the Needs Andy thread.
- 2026-11-08 (pick-list to inbox): Andy wants pick-list picks sent to the Inbox as tasks rather than done ad hoc. Parasite cures go on an existing item, not a new one - "the treatment can be added to any of the existing items, maybe one of the teas" (the pack's teas are Powers willow-bark and chamomile).
