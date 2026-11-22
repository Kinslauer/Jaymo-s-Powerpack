# camp guards wear their camp's gear, not MCA's iron
- **Camp guards**: a camp's raiders and pirates who have become MCA villagers (the pirates on the Illager Structures
  balloon, cove deckhands and gunners, barbarian raiders) now dress only from their own pack: their camp's leather,
  hides and coats. MCA used to fill every empty slot from its guards' iron set, so a pirate deckhand stood in full plate.
  Ones already in plate take it off the next time they dress (`Ranks.campOwned`, `McaEquipmentMixin`).
- **Raider pieces kept**: a raider's torn hood, torn cloak and horned crest (the Guard Wardrobe's Raiders column) stay on
  when he becomes an MCA villager (`McaBridge.convertRaider`).
- Built by CI; not played.
