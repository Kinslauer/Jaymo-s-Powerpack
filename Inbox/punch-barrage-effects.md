---
status: in-review
for: any
from: Andy (Claude Code thread, Ragdolls and Cover pick-list)
asked: 2026-11-16
thread: 
claimed_by: hermes-b
pr: 1665
suggested_owns:
  - src/main/java/com/jaymospowers/power/chrono/ChronoPunch.java
  - kingdoms/src/main/java/com/jaymoskingdoms/spirits/SpiritBarrage.java
  - kingdoms/src/client/java/com/jaymoskingdoms/client/spirits/BarrageClient.java
---
# Punch and barrage effects

**Andy's words:** "The punch and barrage effects are what i want" (about
https://www.curseforge.com/minecraft/mc-mods/panda-cursed-fate-addon). Picked rows pb-punch and pb-barrage on the
Ragdolls and Cover pick-list (https://claude.ai/artifact/6v9SAjDsK47H9ay6MqgcPF); pb-ref (match a clip) left out.
Then: "put the punch and barrage effects on the tasks board instead."

**What's wanted:**
- **pb-punch, heavy punch impact:** a hit lands with a shockwave ring, a dust wave along the ground and a short
  screen shake. On spirits' Spirit Punch, Gravity's Inversion (GravityGutPunch), the Z-Fighter rushes (ZFighterRush)
  and Chrono Punch's last hit.
- **pb-barrage, barrage flurry:** dozens of blurred fists in front of you while it lasts, sparks where they land. On
  Chrono Punch's flurry and Kingdoms' barrage clashes (SpiritBarrage), where two flurries meet with sparks between them.

**Where to start:** the add-on is a Forge 1.20.1 add-on for Cursed Fate (not in our pack, licence unknown): watch its
page's videos for the look, copy no code or art. Build only from typed effects (docs/MagicFx.md: ring, wave, stroke,
mote, spark through MagicFx; Kingdoms: Fx), varying colour, size and time. Classes: Powers ChronoPunch,
GravityGutPunch, ZFighterRush; Kingdoms SpiritPunch, SpiritBarrage, BarrageClient.

## Notes
- Not to be built in the Claude Code thread that made the pick-list (Andy, 2026-11-16).
- Builds go on the Deck or CI, never Andy's PC.
