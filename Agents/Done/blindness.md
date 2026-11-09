---
status: done
task: blindness
branch: claude/blindness
with:
pr: 1159
mods: powers
versions:
started: 2026-11-08
owns:
  - src/main/java/com/jaymospowers/power/blindness/
  - src/client/java/com/jaymospowers/client/power/blindness/
  - src/main/java/com/jaymospowers/JaymosPowers.java (shared hot spot - edit last, after merging master in)
  - src/client/java/com/jaymospowers/client/JaymosPowersClient.java (shared hot spot - one line added)
  - src/main/resources/assets/jaymospowers/lang/parts/blindness.json
  - changes/powers/claude-blindness.md
---
# Blindness power

**Task:** build the Blindness power for Jaymo's Powers: total blindness navigated by sound-wave echolocation (R), plus one-eye blind (G), blind-side swap (V), deafen (B), variant toggle Borrado/Escuro (Z), and fate-gated C abilities (Void Sight ascended / Blackout corrupted / Abyssal Echo both). Picked up hermes-a's skeleton in E:/wt/blindness and carried the topic on.

## Plan
- make hermes-a's skeleton compile against Minecraft 26.3 (BlindnessEcho payload lives in BlindnessEchoPayload, not PowerPayloads; ClientPlayNetworking receiver is client-only; no hLine on GuiGraphicsExtractor; drop the unhooked onSoundPlayed mixin stub)
- register BlindnessPower in JaymosPowers (init + import + registerAttachments) and BlindnessPowerClient.init() in JaymosPowersClient
- add lang/parts/blindness.json + changes/powers/claude-blindness.md change note
- sh ./gradlew build + python3 tools/pr_checks.py, commit, push, open PR
- set this note and the task to in-review with the PR number

## Notes
- Picked up from hermes-a's run: 17 new files were committed as e784cf09f, then origin/master merged in (d697ba8b5)
- JaymosPowers.java edit currently in the working tree is hermes-a's mangled indentation; rewrite it cleanly with tabs
- Not tested in game; build-checked only (all merged work since #941 is compiled, not played)

## Done
What a player will see, and what wasn't tested.
- 2026-11-08 20:27 PR #1159 is merged into master; moved to Agents/Done.
