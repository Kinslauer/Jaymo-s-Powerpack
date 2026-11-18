# Logging in works again with Inferno's library
- StrawberryLib (the library Inferno came with) asks whether a player is a spectator while the game is still making the local player, before it has a connection, and every login failed with a packet handling error. The game now answers that it doesn't know yet, and looks again a moment later.
