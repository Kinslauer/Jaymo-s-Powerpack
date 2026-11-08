---
name: unseen-orc-988
description: Andy's invisible+invulnerable orc that only NPCs attacked (2026-11-05); PR #988 takes pack 0.20.77 / Kingdoms 0.7.565; root cause unconfirmed
metadata:
  type: project
  modified: 2026-11-05T07:33:32.609Z
---
2026-11-05 Andy: an orc "was invulnerable, and invisible... i could only see other npcs attacking it" (clips MedalTV 20261105 0203/0204/0210 don't show it; Morggash in 0210 is a visible captain). No werewolf nearby; no client log kept.
PR #988 MERGED 2026-11-05 07:36Z (branch claude/project-thread-1h62yq, pack 0.20.77, Kingdoms 0.7.565, built, not played): Lycans.hidden blocks targeting a werewolf's hidden host + unTarget turns attackers onto the beast; InBalming Quiet One veil is 5 s topped up at keeper's side (was 1 h, outlived keeper).
**How to apply:** best guess is a client/server entity desync (server has the orc, client doesn't). Clue: SpeechBubbles sends a villager line to chat ("Sibila: Attack!") only when the client has no such entity within 24 blocks. If it recurs, ask Andy for the CLIENT logs/latest.log right away (he uploaded only server logs last time).
2026-11-05 09:56Z: recurred in Jung's Blood.mp4: an MCA villager swinging repeatedly with hit sounds and a burst of blood sprays inside its own chest, nothing visible to hit (Andy: "happened before with the red indicator"). Andy: it targeted the skeleton in front, no damage either way. The burst was the attack-tell glint (AttackTells), not blood; MobCombat.plainBlow had no reach check, so MCA melee glinted+whiffed at a foe out of reach (villager shouting "Trespasser!", likely at Jung). Fixed in PR #1002, MERGED 2026-11-05 10:10Z (Powers 0.20.86, Kingdoms 0.7.577). The "red indicator" earlier case was likely the same with a heavy (red) tell.
