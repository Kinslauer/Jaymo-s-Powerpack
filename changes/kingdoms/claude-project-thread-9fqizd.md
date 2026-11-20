# Taken weapons stay taken: no more shields duplicating off downed villagers
- **Dupe fixed** (Andy's clip, 2026-11-19: a downed MCA guard's shield came back after every tap of use, three shields from one villager). MCA's equipment routine hands a guard a brand-new shield (and a sword if their pack has none) whenever a hand is empty, and it kept running on the downed, so each disarm (the standing tap of use) was refilled at once.
- MCA no longer dresses or arms a downed villager (`McaEquipmentMixin`). A villager a player has disarmed is tagged `jaymoskingdoms.disarmed` and from then on only takes up hand items that are in their own pack (`Downed.owned`), so coming to doesn't hand them a fresh shield to take again; give them a weapon and they'll use it.
- MCA arms a villager with the very stack in their pack, so a weapon taken from the hand now leaves the pack too (`Downed.unpack`); before, a full inventory dropped a stack that was still in their pack.
- A realm's Folk soldier who was disarmed isn't given a new shield or banner by the rank sweep (`Ranks`).
- Safety net: while a disarmed body stays down, anything that turns up in its hands is cleared each tick, so nothing can be taken twice.
- Built; not played.
