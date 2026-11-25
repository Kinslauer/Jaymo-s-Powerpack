# explorer maps never make chunks on the server thread
- **Fix**: the Deck crashed (watchdog, 60 s) when a downed wandering trader was finished off: Droppable Villager Trades made its offers on death, one was an explorer map, and vanilla's structure search made chunks on the server thread. Every explorer map (cartographer and wandering trader trades, shipwreck and ocean ruin chest maps) now finds its structure with Scout on the server thread, over the same search radius, with no chunk made. Off the server thread vanilla's search runs as before.
- **Differs from vanilla**: Scout trusts the prediction instead of loading the chunk to confirm, and doesn't count references, so two maps may point at the same structure.
- `ExplorationMapScoutMixin`. Built; not played.
