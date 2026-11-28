---
status: in-review
for: apollo
from: coordinator
asked: 2026-11-28
thread: Clip based bugfixes (Claude side session)
claimed_by: 
pr: 2360
suggested_owns:
  - kitchen/src/client/java/com/jaymoskitchen/client/corpse/ (CarryPose, DragPose, Limp, CorpseRenderer)
  - kitchen/src/client/java/com/jaymoskitchen/client/ragdoll/ (LooseParts, RagdollPose, DragSim)
  - kitchen/src/client/java/com/jaymoskitchen/client/mixin/ (FirstPersonHandsMixin, AvatarRendererMixin, ModelFeatureRendererMixin, RagdollModelMixin)
  - kingdoms/src/main/java/com/jaymoskingdoms/creatures/ (Digger, Warrens, TrapCoverBlock)
---
# Dragged bodies: legs stay on, a visible drag arm, diggers take bodies into their pits

**Andy's words:** (clip 2026-11-23, carrying then dragging a dead sheep) "in this clip the sheeps limbs are detatched as im carrying it, and there still isnt an arm visibly tethered to the dragged mob like i've asked for several times. i like the behavior of the diggers popping out to attack, and this time my dragged sheep fell into the hole, it would make sense if this was one of their behaviors, dragging corpses into the pits to either feed or dismember for meat". 2026-11-28: "Finish what was stopped".

**What's wanted:** a carried or dragged quadruped keeps its legs on its body; the player's arm visibly reaches to and holds the dragged leg (first and third person); diggers drag corpses near their pits down into them to feed or butcher.

**Where to start:** the brief below. Lore placement is already in Lore.md: "What falls into a warren's pits" (new).

Codex (out until Dec 1, 10:47 AM) and Gemini (out ~115 h) both hit their limits, so this comes to the bots.

## Brief
Work on a new branch `corpse-limbs-drag-arm-digger-pits` cut from origin/master, in your own worktree. Repo: Jaymo's Powers / Kingdoms / Kitchen, Minecraft 26.3 Fabric mods (Mojang names, no
mappings). Read AGENTS.md first, and every `.claude/rules/*.md` whose `paths:` match files you touch. Find code through
docs/codemap/kitchen.md and docs/codemap/kingdoms.md before grepping.

HARD RULES
- Do NOT run gradle or any build: this PC is for Andy's game. CI compiles the PR. So be extra careful with APIs: check
  every Minecraft/Fabric API you call against code already in the repo that uses it (grep), and don't invent methods.
- Kingdoms never compiles against Kitchen (and Kitchen never against Kingdoms). Cross-mod links go by entity id,
  scoreboard tags, or reflection/by-name lookups, the way existing code does (grep "jaymoskitchen" in kingdoms/src,
  e.g. how graves reach Kitchen's `Burial`). Optional-mod checks follow the existing compat classes.
- Commit and push your branch, open the PR, then set this task to in-review (vault_sync --set). Don't touch `version=`, MODLOGs or docs/codemap/. Don't undo the merged PR #2033 work (Scavengers,
  CorpseEntity nextPart/eatPart, quadruped leg names in Severed).
- No version numbers in code comments. Tuning numbers as `static final` constants at the top of the class. Match the
  surrounding style; surgical changes only.
- When done, write two change notes (exact format in changes/README.md):
  changes/kitchen/corpse-limbs-drag-arm-digger-pits.md and changes/kingdoms/corpse-limbs-drag-arm-digger-pits.md
  (`# <title>` line, then MODLOG-style text, no version). Say in them what wasn't played.
- Finish with a short report: what you changed per file, the root cause you found for pieces 1 and 2, and anything
  you were unsure of or couldn't do.

ANDY'S WORDS (from a clip of him carrying, then dragging, a dead brown sheep in a savanna village):
"in this clip the sheeps limbs are detatched as im carrying it, and there still isnt an arm visibly tethered to the
dragged mob like i've asked for several times. i like the behavior of the diggers popping out to attack, and this time
my dragged sheep fell into the hole, it would make sense if this was one of their behaviors, dragging corpses into the
pits to either feed or dismember for meat"
Earlier (2026-11-22): "i need our dragging and lifting to stay as is. just fix the functionality ... you should also use
both arms when lifting anything other than small animals and mobs."

THREE PIECES

1. A quadruped corpse's legs float loose from its body while carried and while dragged.
   The sheep's legs were drawn hanging in the air apart from the trunk. Find the real cause in Kitchen's client code:
   kitchen/src/client/java/com/jaymoskitchen/client/corpse/{CorpseRenderer,Limp,CarryPose,DragPose}.java,
   client/ragdoll/{LooseParts,RagdollPose,DragSim}.java, client/mixin/{ModelFeatureRendererMixin,RagdollModelMixin}.java.
   Likely suspects to check (not confirmed): Limp hangs limbs "from the shoulder or hip straight down" in the model
   space of a humanoid, which for a quadruped (QuadrupedModel: leg pivots under the body, body part rotated 90° on X)
   puts the legs somewhere off the trunk; or LooseParts/RagdollPose moving leg parts by offsets meant for another
   rig; or the leg parts' x/y/z being set (not just rotated) so they leave their pivots. Fix it so a quadruped's legs
   stay on its body (pivot stays at the hip/shoulder, only the rotation changes) while carried, dragged and lying.
   Don't break humanoids, chain/snake bodies or modded mobs.

2. A visible arm tethered to the dragged body (Andy has asked several times).
   While dragging, the player's holding arm must visibly reach to and hold the leg being dragged, so the hand stays
   on the body. Third person: the arm(s) point at the grip point (right arm alone for a small body, both for a big one,
   as DragPose's comment says), AND the dragged body's held leg should end at the hand (if the body's grip point sits
   away from the hand, close the gap: make the server/client grip point follow the hand position, or lengthen/aim the
   arm so it meets it). First person: today FirstPersonHandsMixin only handles carrying. Add a drag pose in first
   person: the holding arm reaches down and back toward the body so it shows at the edge of the screen gripping
   (the body itself is behind you). Find out why the existing third-person DragPose doesn't show or doesn't reach
   (is it applied? is it overridden by a later pose, e.g. the walk animation or a Powers pose? is dragHand synced? is
   the angle maths wrong?) and fix that cause. Code: DragPose, CarryPose, AvatarRendererMixin, FirstPersonHandsMixin,
   DragSim, and CorpseEntity.dragHand / gripPoint (kitchen/src/main/java/com/jaymoskitchen/corpse/CorpseEntity.java).

3. Diggers drag bodies into their pits (Kingdoms).
   Kingdoms' diggers (kingdoms/src/main/java/com/jaymoskingdoms/creatures/{Digger,Warrens,TrapCoverBlock,Packs}.java)
   live in warrens ringed by hidden staked pits under trap covers, and pop out to attack. Add: a digger that sees a
   corpse (Kitchen's corpse entity, by its entity type id, e.g. look it up in BuiltInRegistries.ENTITY_TYPE; only when
   Kitchen is loaded) lying within a few blocks of one of its warren's pits, or one a player drags near a pit, goes to
   it, takes it by a leg and drags it (move the corpse entity along behind the digger, slowly, heavier bodies slower or
   needing two diggers) to the nearest pit and down into it. In the pit it's "fed on / butchered": over time the
   diggers take meat from it (use Kitchen's own by-name hooks if they exist, e.g. the eatPart/nextPart scavenger path
   from PR #2033, reached by reflection; otherwise remove the body after a while and leave bones). Then:
   - Sympathy: like real burrowing carrion-takers (hyenas, badgers, kobolds of legend), they cache food in their burrow.
   - Feedback loop: a warren that has fed recently is sated: its diggers stay in their holes and don't pop out to attack
     for a while (a fed predator stops hunting); a hungry warren takes bodies more eagerly. The bones/body in the pit
     stay there as a sign.
   - Stewardship: nobody is punished for ignoring them; a player who leaves a carcass by a warren (feeds it) gets a
     warren that leaves them alone for a time.
   Don't let a digger take a body a player is holding/dragging out of their hands mid-drag unless it's right at a pit
   edge (Andy's own sheep fell in: a dragged body crossing an open pit can be pulled in). Don't take a body from a
   spider brood's larder (Lore: "The brood's larder": diggers won't take from her larder; grep the code for the larder
   tag/marker if there is one). Leave player corpses alone? Use your judgment, but player bodies hold loot and a
   respawn tie: do NOT take player corpses into pits.
   Lang: player-visible text goes in kingdoms lang parts (see .claude/rules/kingdoms-lang.md).

## Notes
- 2026-11-28 13:33 YOUTH delegated this to apollo (Claude approved): Kitchen corpse drag pose plus visible drag arm, and Kingdoms diggers dragging corpses into pits; Apollo picked as the free coding bot.
- 2026-11-28 14:05 hermes-a's claim had no sign of work for 30 min: handed back as open.
