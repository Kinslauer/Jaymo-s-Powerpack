# another player shakes a fainted player awake
- Andy: "players and npcs should be able to wake players who faint". A player lying out in a faint (hunger, thirst, exhaustion, Overcome) is shaken awake by another player's right-click on them (main hand, not sneaking): a swing and a rustle, and they come round on the spot, the letterbox opening as after any faint. Sneak-use is left to seizing, lifting and pickpockets.
- `Fainting.shake` (UseEntityCallback, Downed's first phase). The faint attachment now syncs to everyone, so the shaker's client doesn't eat or draw a bow at the same time.
- Built; not played.
