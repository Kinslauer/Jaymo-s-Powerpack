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
- 2026-11-09: Andy: remove all the other bots' schedules and let the default Hermes check in on them every 5 minutes, sending whoever needs to be where. The bots' cron jobs now sit on a never-fires yearly schedule, and a new no-agent job `dispatch-bots` in the default profile runs `E:/hermes/scripts/dispatch_bots.py` every 5 minutes to fire the right bot's job, two at a time.
- 2026-11-09: Andy: the cloud bot (Prometheus, hermes-modeller) does not count toward the cap, so three bots can work at once — two counted (one local builder plus at most Quill) plus Prometheus. Prometheus was also started on the astronomy-telescope task.
- 2026-11-09: Andy: questions with concrete options go up as Discord polls in the thread being used, not as buried text — `E:/hermes/scripts/discord_poll.py` posts them with the bot token.
- 2026-11-08 (mimic): Andy: "prometheus can pick up the mimic, and tell him I want that to replace the way the echos function". The player-copying Mimic from Tiv's ItsJim video reference replaces Chrono's echo mechanic; Prometheus supplies its original true-form model, texture and animations, with gameplay handled separately.
- 2026-11-08: Andy wants to see when Hermes bots stall or stop. Added E:/hermes/scripts/bot_health.py: Kanban shows one card per bot (Blocked = stalled 30 min / missed 2 runs / 2 failed runs) and each claimed task's last change; cron job bot-alerts (default profile, every 5 min) DMs Andy new problems; Windows task "Hermes Gateway Watchdog" restarts the gateway if it isn't running (pause it with E:/hermes/scripts/watchdog.off).
- 2026-11-08 (rider-class): Andy: "add everything you thought up, and everything in this wheel should be available to riders", "immediately not exclusively": Riders get all eight Rider ideas, and their own horses obey every horse-wheel order, roster call and whistle at any trust; other players keep the trust rules. PR #1161.
- 2026-11-08 (five mods pick-list): Andy ticked the list. Realistic Water: "we can try it" - he accepts that the block physics comes with the volumetric simulation, since they are one system. Streams Reflowing keeps rivers and lakes ("streams wins obviously, i just want the blocks"). Simple Difficulty: parasites for bad water, plus treatment methods for them; permanent hearts found in the world are wanted. Tinker's Delight: knives, melting cookware back down and splitting cakes/bricks yes, the food buff for tinkered gear no. Dynamic Trees stays parked until a 26.3 Fabric build exists. Worldbound gets one pass for gaps.
- 2026-11-08 (rider-class): Andy: a Rider's starter horse tops out only slightly faster than the fast run ("the sprint you break into after a few seconds of running"); it opens up at max racing mastery, so Riders still want better horses. PR #1161.
- 2026-11-08: Andy approved adding the ore hearts ("i approved adding the ore hearts, send it to the modeller"). Art (heart crystal ore in stone and deepslate, the heart crystal item) goes to Prometheus as task ore-hearts-art; the code side stays with hermes-b on ore-hearts. Andy's two design calls (survive death, the cap and where it shows) are still open and are asked in the Needs Andy thread.
- 2026-11-08 (ore hearts): Andy: "1. lost when you die, but it grants you a life as in another revive before reincarnation. 2. do this as well." So a found heart is spent on death — it turns that death away as an extra revive, one life per heart before reincarnation — and the crystal cap is five full hearts above base, shown quietly as a faint extra run on the health bar. Task ore-hearts unblocked and back in the pool.
- 2026-11-09 (bots chain, and a dead claim no longer blocks): Andy: "make it so they pick one up whenever they are finished, one after the other" — every bot's cron prompt now keeps claiming the next Inbox task in the same run (max two per run) and all five run every 5 minutes. The real cause of the idling was a claim left by a bot that died mid-task: startup-crash sat claimed by 'hermes' for six hours, and because a builder never shares the pair with another builder, every bot refused to start. vault.py now hands a claim back after 45 minutes of silence from its bot.
- 2026-11-08 (Needs Andy topic): Andy: "Put anything that ends up here (Needs Andy) in this discord topic" — every blocked task, design question, approval ask and PR ready for his merge is posted in the Discord topic "Needs Andy" (thread 1569168864384913589, parthenon forum), not only written to the vault. In the workflow skill as a standing rule.
- 2026-11-08 (pick-list to inbox): Andy wants pick-list picks sent to the Inbox as tasks rather than done ad hoc. Parasite cures go on an existing item, not a new one - "the treatment can be added to any of the existing items, maybe one of the teas" (the pack's teas are Powers willow-bark and chamomile).
- 2026-11-08: Andy: YOUTH stops posting its tool steps in Discord (E:/hermes/config.yaml display.platforms.discord.tool_progress: off); its short updates between steps and approval prompts still show.
- 2026-11-08 (astronomy): Andy linked the Astronomy mod and said "add this but in the high renaissance artstyle". Art ruling stands for anything astronomy-shaped: sepia ink on aged paper, cross-hatching for volume, construction lines and faint diagrams left visible, instruments drawn as studies rather than sleek objects. Style reference saved at Assets/astronomy-reference.png. Astronomy 0.3.0 is MIT but NeoForge 1.21.1, so our own remake.
- 2026-11-08 (astronomy picks): Andy ticked the whole astronomy pick-list. The journal and skill ladder is worked into the Kingdoms scholar side ("Should be worked into the scholar side"), not into Powers and not as a third ladder. Six Inbox tasks posted: astronomy-sky (foundation), astronomy-telescope, astronomy-armillary, astronomy-scholar-journal, astronomy-sun-moon, and astronomy-art for Prometheus.
- 2026-11-08: Andy: "set up" Quill (hermes-writer): model nemotron-3-ultra (was a leftover deepseek-coder trial), job quill-text-pass every 15 min (4-59/15) doing Pipeline step 3 text passes via E:/hermes/powerpack/vault.py; it counts toward the 2-bots-at-a-time limit.
- 2026-11-08: Andy: "allow three to run, the third only being Quill": builders stay at 2 at once; Quill (hermes-writer) may run as a third (E:/hermes/powerpack/vault.py).
- 2026-11-08 (spirits look): Andy wants the mana spirits to be "a faint but visible warrior", not just arms - Johnny Silverhand as the model, fully manifested when they speak and on level-up, visible only to the mana attuned. The Quiet One never manifests a body, only its large arms. First-person framing follows Cyberpunk (the spirit in your own view). References saved in Assets/spirit-*.png. Task: spirit-manifestation.
- 2026-11-09 (minotaur GeckoLib): Andy asked about animating the Stegonaut and other Blockbench models with GeckoLib, then: "Actually lets do the minotaur because its an actual blockbench model, and send it as a task for prometheus". The minotaur moves from BbMobRenderer to GeckoLib, using its model's own walk, calm idle and second swing too. Stegonaut and the other dragons/beasts wait on how it goes. Task: minotaur-geckolib (for: prometheus).
- 2026-11-09 (cloud agent): Andy asked whether Hermes could use his Anthropic API key so YOUTH can call Claude for cloud work, then "Yeah set up option 2": a Claude Managed Agent (Powerpack Builder, tools/cloud-agent/) that does a task in Anthropic's cloud sandbox, builds, and opens a PR; YOUTH starts it with `run.py task <name>` (or for `for: cloud` Inbox tasks). Billed to the API key, $20 cap a session. Andy still has to run the one-time setup on the PC (keys, `ant apply`, `run.py credentials`).
- 2026-11-09: Andy: YOUTH (default Hermes) is a hyper-assistant that only delegates: it files Inbox tasks for the right bot and never builds, edits mod code, pushes to PR branches or claims tasks; it always runs and never counts toward the bot limit (E:/hermes/SOUL.md).
- 2026-11-09: Andy: YOUTH starts cloud jobs (tools/cloud-agent/run.py, from tools/cloud-agent/README.md "For YOUTH") for cloud requests and for: cloud tasks; one at a time; added to E:/hermes/SOUL.md.
- 2026-11-09: Andy: YOUTH may start cloud jobs only on his own call (not friends, not for: cloud tasks on their own); Hermes terminal passes ANTHROPIC_API_KEY and GITHUB_TOKEN through (config.yaml terminal.env_passthrough) for run.py; YOUTH must never print them.
- 2026-11-09: (follow-up) Hermes refuses terminal.env_passthrough for provider credentials (ANTHROPIC_API_KEY, GITHUB_TOKEN; GHSA-rhgp-j443-p4rf), so the passthrough line was removed again; cloud jobs need run.py to read the keys itself.
- 2026-11-09: Andy chose option 1: the gateway watchdog (E:/hermes/scripts/gateway_watchdog.ps1) posts to a Discord webhook itself when the gateway is down and when it's back (user env var HERMES_ALERT_WEBHOOK; webhook named "Youth Heartbeat"). A cloud heartbeat check (PC off) is skipped unless remote wake is possible.
- 2026-11-09: Andy: "do whatever you must" about the stale Kitchen jar blocking pulls in E:/Origins Mod. Moved libs/jaymos-kitchen-0.5.93.jar (untracked local build) to E:/Origins Mod backups/stale-jars and fast-forwarded master; deck/deploy.ps1 (untracked, Deploy to Deck) now moves any untracked/changed libs/jaymos-kitchen-*.jar there and restores libs/ before it pulls, like pbdr step 1.
- 2026-11-09: Andy: live Kanban copy in Discord, Hotspot > parthenon > "Kanban" post: one message YOUTH edits in place, every 3 min when Kanban.md changed (cron discord-board, E:/hermes/scripts/discord_board.py; failures DM Andy).

## Discord board location (2026-11-09)
Andy, in the `parthenon` topic "Kanban": "new kanban goes here". The live board message is kept in that thread
(id 1569368728901255309) by `E:/hermes/scripts/discord_board.py`, run by the `discord-kanban` cron job every 5 minutes.
- 2026-11-09: Gateway freezes = disk stalls on E: (HDD RAID holding Hermes, builds and the page file). Andy: gateway loop watchdog now tolerates ~3 min (config.yaml gateway.loop_watchdog_probe_timeout_s 30, max_strikes 6); the Windows watchdog checks every minute; Andy moves the page file to C: (SSD) himself with E:/hermes/scripts/move_pagefile_to_ssd.ps1, then restarts.
- 2026-11-09 (animations for all): Andy: "I want tasks set for all the models with no animations, the omen, redstone and sculk, and all the dragins need models and animations." Nine Prometheus tasks posted, each moving one onto GeckoLib with keyframed animations: omen-geckolib, android-mech-geckolib (Powers), redstone-golem-geckolib, skulk-golem-geckolib, and stegonaut/aetherion/atroxiia/varasuchus/zephyrus-geckolib (each dragon gets a real .bbmodel first). The spirit figures go into the open spirit-manifestation task (note added) so they don't collide. Weapons, armour, blocks and the goblin villager body are left without animations.
- 2026-11-09 (names): Andy named the cloud Claude **Odysseus** ("We can call it Odysseus"), and wants the name used everywhere it can be: the vault board labels now read `💻 Odysseus` (PR #1197, `tools/vault_sync.py`), and the skill roster and Hermes memory carry the name. Branch names (`claude/...`), the git author and old notes keep their wording.
- 2026-11-09 (bot cap): Andy: "make a maximum of two bots run at a time for now, ill increase that when i upgrade my computer" and "one builder/modeller and one reviewer/writer". `MAX_CONCURRENT_BOTS = 2` in `E:/hermes/powerpack/vault.py`, and a builder never runs alongside another builder — the pair is one builder plus Quill at most. A Gradle daemon was stopped mid-build while a deck deploy ran, which failed the deploy; one build at a time avoids it.

## 2026-11-09 - dependent PRs merge as a set

Andy: "any feature that requires another PR to work as intended should be held and pushed together." A PR whose point needs a sibling PR (the night sky before the telescope, armillary and journal can read it) waits for the set instead of merging alone, even when the instruction is "merge everything".
- 2026-11-09: Andy: Claude and Hermes (YOUTH and every bot) write times on a 12-hour clock with AM/PM. Added to every Hermes SOUL.md; bot_health/Bots.md, Kanban card times, dispatcher and watchdog Discord alerts now show 12-hour times.
