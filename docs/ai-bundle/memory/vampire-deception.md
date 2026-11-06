---
name: vampire-deception
description: Vampire/werewolf deception (Symbiote-inspired): hunger+heartbeat near players and all 24 picked ideas, PR #650 MERGED 2026-10-29 as Kingdoms 0.7.332 / pack 0.17.160
metadata:
  type: project
---
2026-10-29 Andy: use Symbiote: A Bonding Experience (ARR, ideas only) to drive anti-social/deception play for vampire and werewolf players; "vampires should hunger when within 150 blocks of other players. the screen beating and heartbeats playing while they are within your pov."
Built: PR #650 (Kingdoms 0.7.322 / pack 0.17.149): BloodFever.amongPlayers (mortal players only, food exhaustion + fever climb by closeness, extra if in a 40-degree view cone with line of sight), HEARTBEAT attachment synced to the vampire, BloodVision pulses red edges + warden heartbeat any time of day.
Pick list (24 ideas, numbered, sections A-E): https://claude.ai/artifact/4k22memmMbtEq7rQnobfbD. 2026-10-29 Andy ticked ALL 24 and answered: villagers count for the 150-block hunger (yes; heartbeat stays players-only), only mortals count, heartbeat grows to 80% max volume. PR #650 renumbered to Kingdoms 0.7.323 / pack 0.17.150. All 3 tracks built onto #650 (vampire 1-10/21-24, werewolf 11-16/23-24, paranoia 17-20), now Kingdoms 0.7.332 / pack 0.17.160; builds, dev server boots, not played. Accusing is the priest's MCA talk option "Accuse someone here" (Andy asked; McaAnswersMixin shows it only with another player within 10 blocks). Choices to flag: torn cloth uses beast-rag colour; morning-after only after forced nights; sleeper bites never infect; victim-memory flash fires on any bed exit.
Related: [[blood-fever-feral-curse]], [[vampire-blood-vision]], [[vampires-werewolves]].

2026-10-29 Andy: torn cloth, beast rags and real clothes "should all be one and the same": ClothesColour (client reads own MCA clothes texture when drawn, tells server; Lycans.clothes uses it for players). PR #650 MERGED 2026-10-29 03:1xZ (6a3b1df6) as Kingdoms 0.7.332 / pack 0.17.160, not playtested. Next free: Kingdoms 0.7.333 / pack 0.17.161.
