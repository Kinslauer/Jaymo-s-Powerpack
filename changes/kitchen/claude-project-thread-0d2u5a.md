# Scavengers and werewolves eat every body with flesh on it
- For Jaymo's Kingdoms' werewolves: `CorpseEntity.isFleshForDevourer` (any body with flesh on it lying still: not bones, iron, or anything burning, carried or carted) and `Scavengers.devourable/devourBite` (a scavenger's bite, torn open after the number of bites the eater asks for). The scavengers' tearing is the same code (`Scavengers.tear`).
- Andy: "everything should be able to be eaten". Scavengers now eat every body with flesh on it: players' bodies, players' kills, monsters', the rotten dead's (not zombies eating zombies). Only skeletons and iron are left (`CorpseEntity.isCarrionFor`).
- A body torn open leaves what it wore and held on the ground too, and a player's body also its hotbar; a player's things never despawn, so nothing of theirs is lost to it.
- Built; not played.
