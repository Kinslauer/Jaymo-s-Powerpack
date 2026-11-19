---
status: done
task: rig-beetle-5-the-mind
branch: claude/rig-beetle-5-the-mind
pr: 1774
mods: powers
versions: 
started: 2026-11-18
owns:
  - src/main/java/com/jaymospowers/rig/RigTalk.java (new)
  - src/main/java/com/jaymospowers/rig/RigPilot.java (new)
  - src/main/java/com/jaymospowers/rig/RigEvac.java (new)
  - src/client/java/com/jaymospowers/client/mixin/RigTalkChatMixin.java (new)
  - src/main/java/com/jaymospowers/rig/RigNet.java
  - src/main/java/com/jaymospowers/rig/RigPicks.java
  - src/main/java/com/jaymospowers/rig/Rig.java
  - src/client/java/com/jaymospowers/client/rig/RigMenuScreen.java
  - src/main/resources/assets/jaymospowers/lang/parts/rig.json
  - changes/powers/rig-beetle-5-the-mind.md
---
# Rig patch 5: the Mind talks, flies and keeps watch

**Task:** Give a Rig wearer three new Mind actives as Rig menu entries (Sneak + J): talk to the Mind in chat, autopilot to a marked waypoint, and evacuate the host when the suit breaks; each with charge cost, poses and sounds, gated by Mind level.

## Plan
- RigTalk: wearer's typed chat caught and answered in the Mind's personality (hints only), via a ChatScreen mixin -> rig_chat payload -> RigMind replies.
- RigPilot: autopilot flies the suit toward the looked-at waypoint, charge permitting; movement takes control back.
- RigEvac: near-empty Core + lethal hit throws the wearer clear; suit slumps as a wreck.
- Wired via RigNet TALK/PILOT/EVAC + rig_chat payload, RigPicks, RigMenuScreen entries, new RigMind voice lines and lang keys.

## Notes
- CI: Build Powers was red on the net.minecraft.client.network import in the client mixin; fixed to net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking (the package RigClient uses). All checks now green.

## Done
What a player will see, and what wasn't tested.
- Not tested in game: compile-checked with sh ./gradlew compileClientJava/compileJava and tools/pr_checks.py passes; did not boot the pack.

- 2026-11-19 07:35 Cloud vault check (Health.md): task is in Inbox/Done, so this note is done.
