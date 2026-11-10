---
status: in-review
for: hermes
from: tiv (Discord, Tasks thread, 2026-11-08)
asked: 2026-11-08
thread: 1569170499680870560
claimed_by: claude-cloud
pr: 1224
suggested_owns: [src/main/java/com/jaymospowers/core/wounds/Downed.java, kingdoms/src/main/java/com/jaymoskingdoms/Downed.java]
---

# Downed players are not finished off by villagers, guards or captors

A player who goes down gets their second life instead of being killed outright, because the people around them stop swinging.

## What's wanted

Tiv, in the Tasks thread: "When players are downed they should not still be attacked by villagers, guards, or those that have them captive. any others you think would fit that qualifier too. too often players will be killed outright instead of getting to use their second life. being downed in war or the wild or at sea or anywhere you wouldnt realistically be safe still count. death is real and close."

## Notes

- The qualifier is "would not finish a body on the ground": villagers and their MCA guards, whoever is holding the player captive, and anything else that reads the same way (decide the full list before coding it).
- It applies everywhere — a war, the wild, out at sea — not only inside a safe settlement.
- Don't turn downing into a bubble: "death is real and close", so whatever still hunts a downed player stays lethal. This is about stopping the finishing blows, not about immunity.
- Ask Tiv which mobs belong on the list if it isn't obvious from the code.

## Reference clip

Tiv posted a clip of it happening (message 1569172190673248330 in the Tasks thread, `MedalTVMinecraft20261108140059233.mp4`, 30 s). What it shows:

- A fight with an MCA village: the chat says "They hit back! Stop swinging and they'll cool off, or pay them off: use one with coins or a purse in hand."
- The player goes down: "You're down. They leave you lying there." Camera drops to grass level, an MCA villager lies prone nearby, an armoured MCA villager ("Tikes!") stands over them.
- The player is then killed anyway, a few seconds later, while still on the ground: "Marcellino was slain by Mile", then the death screen ("You lie where you fell, left for dead...").
- So the message says they leave you lying there and the guard/Villager finishes the job regardless. That contradiction is the bug in one line.
- 2026-11-09 09:44 hermes-a's last run stopped before finishing; handed back as open so it can carry on.
- 2026-11-09 23:53 Cloud agent session sesn_01UBPbtzhYeN3NeDy6e1eDpE opened PR #1224.
