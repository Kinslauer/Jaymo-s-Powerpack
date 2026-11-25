# vampire and werewolf seizures fight like the new fighting AI
- Andy: "the symbiotes' and other takeovers (giant, vampire, werewolf) should also get the aggression and AI from the new implementation for MCA. they are the most playerlike and aggressive."
- **Footwork**: while the blood, the wolf or a sentient blade has you, it no longer stands still at its prey. It circles at arm's length and changes side every 1.5 s. The wolf and the blade hop into each blow so it lands falling as a critical hit; `Seizures` waits up to 6 ticks for the hop.
- **Dodging**: arrows and other shots coming at you are sidestepped (`client/TakeoverDodge`).
- **Aggression**: if something other than its prey hits you within 6 blocks while the prey is out of reach, it turns on the attacker.
- Built (compileJava + compileClientJava); not played.
