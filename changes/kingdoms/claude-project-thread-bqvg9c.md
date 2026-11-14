# Tales: story quests over days and weeks, starred board work, a fuller journal
- **Tales.** Some villagers with work have more than an errand. A tale starts as a talk, runs in steps (go, search,
  ask, face someone, bring, build, escort, wait) over days and weeks, and ends by what you chose. Waits end with the
  asker's gold ! coming back or a runner bringing a sealed letter on the road by day. 14 tales ship, each with 5 to 10
  endings; three decide who did it when they start, so they aren't the same twice.
- **Spare or strike.** A tale's named foe yields when beaten: finish them or let them walk. It's remembered (new
  memories for mercy and for cutting down one who yielded), it changes later tales, and some of the spared come back:
  with thanks, a warning, asking to join, or for revenge with friends.
- **Finding them.** Also from a rumour (a passer-by, or a night's drinking), a book in a ruin chest or on a body, a
  letter (a sequel), or a close companion asking about their own past (it moves their bond).
- **Board work.** Town boards post paid work rated one to five stars: a pack to hunt, a named elite, a night to hold
  the town, a traveller to walk to another town, an incursion (nest, camp or pit) to clear, lamps to set before
  nightfall, something to build, a cave-in to blast, goods to leave in a chest. More stars: more and tougher foes, a
  leader, iron, potions, more pay. Each town's board levels up as its work gets done.
- **Journal.** Two new tabs: Stories (each tale so far, with what you chose) and Past (finished, failed and given-up
  quests). A pin box on each quest tracks only that one on screen, with a rough direction.
- **Village life.** Two villagers near you sometimes stop and talk to each other (40 scenes, in their own tongue).
  A town brings up how your tale there ended, once each, over the next days.
- **For writers.** Tales and scenes are data files (`data/jaymoskingdoms/tales`, `scenes`); the format is in
  `docs/StoryQuests-Plan.md`, and `tools/tale_check.py` checks them in the PR checks.
- Classes: `Tale`, `Tales`, `TaleCast`, `QuestJobs`, `BoardWork`, `Overheard`, `QuestPast`; `Journal`, `QuestNet`,
  `JournalScreen`, `QuestClient`, `TownNotices`, `Bounties`, `Quests`.
- Built and checked by the tale checker. Not played.
