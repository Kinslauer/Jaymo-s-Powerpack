# NPC controls by the crosshair - busy, trade, shake awake
- Andy: "a similar box should appear when interacting with npcs, like corpses, that tells you all the controls ... the talk option should change with context. saying "busy" when they are".
- **Every control at once**: the list beside the crosshair on an MCA villager no longer swaps when you sneak; it shows right click to talk/greet/bow, Shift + right click to trade, and Shift + hold right click to pick a pocket (from behind, when learned).
- **Busy**: when they wouldn't stop to talk (TalkBusy: fighting, talking to someone else, tending someone, seeing to the dead, with a prisoner, looting), the talk line reads "busy (fighting)" and so on. The server tells each player who is busy within 8 blocks every half second, only when it changed (`ContextBusy`).
- **Trade** only shows for jobs that trade (not guards, outlaws, workers, nitwits or the jobless: `McaCompat.trades`), and not when the sneak click would pick their pocket instead.
- **Fainted** folk (Overcome) show Shift + right click: shake awake.
- `ContextLines`, `ContextBusy`, `McaCompat`, `DialogueCue`, `JaymosKingdomsClient`.
- Compile-checked; not played.
