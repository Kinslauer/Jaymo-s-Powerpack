---
status: open
for: hephaestus
from: coordinator
asked: 2026-11-28
thread: Cooking blocks off Carry On (Claude side session)
claimed_by: 
pr: 
suggested_owns:
  - src/main/java/com/jaymospowers/core/CarryOnCompat.java
  - src/main/java/com/jaymospowers/mixin/carryon/CarryOnPickupMixin.java
  - src/main/resources/data/jaymospowers/tags/block/carry_on_no_lift.json
  - src/client/java/com/jaymospowers/client/ (a new choice-box screen + its client hook)
  - src/main/resources/assets/jaymospowers/lang/en_us.json (the box's words)
  - docs/carry.md
---
# Sneak-click choice box on liftable blocks: "Use (bake)" / "Lift"

**Andy's words:** "maybe a context box for blocks like these, with use (and the specific in parentheses, for example a
furnace would say "Use(bake)") lift, or whatever other function the block has". 2026-11-28: "Finish what was stopped".

Codex (out until Dec 1, 10:47 AM) and Gemini (out ~115 h) both hit their limits, so this comes to the bots.
Branch: `block-choice-box` from origin/master. PR 2035 (cooking blocks off Carry On) is merged; this builds on it.

## Brief
## What Andy wants (his words, 2026-11-23)
"maybe a context box for blocks like these, with use (and the specific in parentheses, for example a furnace would
say "Use(bake)") lift, or whatever other function the block has"

His answers:
- EVERY block Carry On could lift gets the box (chests, barrels, cabinets too), not only cooking blocks.
- Lift comes back through the box. PR 2035 (merged) put the cooking blocks into
  `src/main/resources/data/jaymospowers/tags/block/carry_on_no_lift.json` so Carry On couldn't lift them. Take those
  cooking entries OUT again (campfires, furnace, smoker, blast furnace, the cookingforblockheads entries, and the
  jaymoskitchen stove, cutting_board, cooking_pot, frying_pan, spit, great_spit, hearth, brewing_keg, cask, still,
  tea_kettle, sushi_mat). Keep the original entries (pod parts, salvage rack, smeltery, kiln load, sentry spawner,
  infusion matrix, bounty board, pulley, plot stake, windmill, waterwheel): those never get a Lift.
- Labels (accepted): campfire Use (cook), furnace Use (bake), smoker Use (smoke), blast furnace Use (smelt),
  CfB oven Use (bake), CfB counter Use (prep), CfB cooking table Use (cook), cutting board Use (chop), Kitchen stove
  Use (cook), cooking pot Use (stew), frying pan Use (fry), spit/great spit Use (roast), hearth Use (warm),
  brewing keg Use (brew), cask Use (age), still Use (distill), tea kettle Use (brew tea), sushi mat Use (roll).
  Plus "Lift" on every one. The CfB oven also gets "Open door". Other liftable blocks: "Use (open)" when the block
  entity is a container or menu provider, else plain "Use". All words in Powers' lang (en_us), so they can be changed.

## The behaviour
Sneak + right-click with an empty main hand on a block Carry On could lift (Carry On loaded, nothing carried, block
has a block entity, not in #jaymospowers:carry_on_no_lift) opens a small box near the crosshair with the choices.
Escape closes it and nothing happens. Picking:
- Use (...): exactly what the sneak-click did before Carry On got it (Kitchen's cooking screen on a station, the
  block's own sneak use, the vanilla menu on a chest).
- Lift: Carry On lifts it as before, with its contents.
- Open door (CfB oven only): the oven's own sneak-click (its door), skipping Kitchen's station handler.
A plain sneak-click must never lift straight away any more. Without Carry On installed, nothing changes (no box).
Every other click stays as it is: not sneaking, holding something, while carrying (our Carry, Carry On, a Kitchen
corpse, being downed), and pod parts (our own carry takes those).

## What's known (checked)
- Carry On 2.12.1 (jar: modpack/mods/carry-on-d0jTyKNs.jar) lifts from a server-side UseBlockCallback in
  `tschipp.carryon.events.CommonEvents`: if not carrying, `PickupHandler.tryPickUpBlock(ServerPlayer, BlockPos, Level,
  BiFunction<BlockState,BlockPos,Boolean>)`; true -> SUCCESS, false -> PASS. Client side it returns PASS. Read the
  bytecode with javap (JDK: C:/Program Files/Eclipse Adoptium/jdk-25.0.4.101-hotspot/bin) to see what callback it passes.
- Powers already hooks it: `src/main/java/com/jaymospowers/mixin/carryon/CarryOnPickupMixin.java` and
  `src/main/java/com/jaymospowers/core/CarryOnCompat.java` (reflection, Carry On optional). Extend these: e.g. the
  mixin refuses tryPickUpBlock unless the lift came from the box (a flag set only around the box's Lift call).
- Kitchen opens its cooking screen on sneak + empty main hand in its UseBlockCallback
  (kitchen/src/main/java/com/jaymoskitchen/JaymosKitchen.java around "sneakingEmptyHanded"; `Station.of`,
  `Prep.isPrepSurface`). A CfB oven that's off falls through to CfB's own sneak-click (the door). CfB
  `CookingTableBlock.useWithoutItem` checks isShiftKeyDown. CfB jar: modpack/mods/cooking-for-blockheads-26.3.0.2+fabric-26.3.jar.
- Powers must not depend on Kitchen: refer to Kitchen/CfB blocks by id or tag (`#minecraft:campfires`,
  `#cookingforblockheads:ovens`, `#cookingforblockheads:counters`, `#cookingforblockheads:cooking_tables`).

## Things to check in the 26.3 sources before relying on them (don't guess)
- What Fabric's client-side UseBlockCallback does with FAIL / SUCCESS (is a packet still sent?), so opening the box
  can stop the click on the client.
- Whether opening a screen releases the sneak key (Minecraft.setScreen / KeyMapping.releaseAll) and how the server
  reads sneaking (Entity.isShiftKeyDown, the player input packet). Use replays the click on the server, e.g.
  `ServerPlayerGameMode.useItemOn`, with the player counted as sneaking for that one call, then restored.
- The payload / screen registration style Powers already uses (look for an existing C2S payload and a small Screen).
Sources: no decompiled MC sources on this PC. Use javap on E:/gradle-home/caches/fabric-loom/26.3/minecraft-merged.jar
(official names) and the Fabric API jars under E:/gradle-home/caches/modules-2/files-2.1/net.fabricmc.fabric-api/.

## Rules
- DO NOT run gradle or any build on this PC (Andy plays on it). Push your branch; CI builds it.
- Server is authoritative: check reach distance and that the block is still there before Use or Lift.
- Small, plain code. Tuning numbers as `static final` at the top. No version numbers in comments.
- Never edit `version=`, MODLOG.md or docs/codemap/. Add `changes/powers/block-choice-box.md`: a `# <title>` line,
  then what a player sees, the classes, and "Built in CI; not played." (see changes/README.md).
- Update docs/carry.md (the Carry On section and its testing list) to match.
- Commit with a clear message, push `block-choice-box` to origin, open a PR to master with gh
  (repo Kinslauer/Jaymo-s-Powerpack). The PR body must say plainly what wasn't played. End the PR body with
  "Generated by <your bot name>".
- Then wait for CI (`gh pr checks <n> --watch`) and fix what fails. Reply with the PR number, CI result, and a list
  of anything you couldn't check.
