# skyfalls made before 0.21.4 dry up too
Andy: "I saw another skyfall earlier today, i need them all removed until at least the world teir remember?" Since 0.21.4
a new chunk gets no sky waterfall before the Dread tier, but chunks made earlier kept theirs. `aether/world/SkyfallCleanup`
checks every chunk that loads in a One Dimension world: water running down out of the Aether's floor is traced back up
through the Aether (at most 8000 water blocks) and each source feeding it is sealed with the commonest solid block beside
it (else air), so the fall drains away. A heavenfall chunk's fall is kept once someone online has reached Dread, as for
new chunks. Water that lands on the islands is untouched; a waterfall poured off an edge by hand before Dread goes too.
Built, not played.
