# Gibs toned down: only overwhelming force bursts a body
- Andy (a mule burst apart by one crossbow bolt to the head): "gibs need to be turned down realistically".
- **Big bodies are harder to burst**: every burst chance (blast, blunderbuss shot, crushing blow on someone standing) is cut by the body's bulk over a man's (`BlastGore.sizeScale`, `MAN_BULK`). A horse or mule bursts about a fifth as often as a man, a cow about half as often, a ravager about a thirteenth. Man-sized and smaller bodies are unchanged.
- **A crushing blow bursts someone standing only if that one blow could have killed them from full health** (`CRUSH_GIB_OVERKILL`). A lesser killing blow still takes the part it lands on, as before (`CRUSH_SEVER`).
- **A crushing blow has to land in reach** (`CRUSH_REACH`, 3.5 blocks between the two bodies' edges): another mod's spell or shot that counts as the caster's own hand no longer counts as a slam (no burst, no throw, no extra limb loss).
- Unchanged: someone downed under a slam still bursts; blasts still gib by closeness (now also cut for big bodies); Stoneborn are never burst.
- Built; not played. The server log for the mule clip wasn't reachable (it runs on the Deck), so which rule burst the mule wasn't confirmed: all three ways a single hit could burst it are covered.
