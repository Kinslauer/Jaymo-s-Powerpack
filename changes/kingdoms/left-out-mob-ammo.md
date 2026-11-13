# Witches, Hornkin, rock throwers and the Ogre King run out too
- **Witches** carry 4 to 7 splash potions (healing throws at raiders count too). With none left a witch throws nothing and backs away from her foe; she brews one back every 30 s while nothing is after her, and picks up splash or lingering potions she walks over (`WitchPotionAmmoMixin`, `MobAmmo.Kind.POTIONS`).
- **Hornkin hurlers** carry 5 to 8 globs of magma. Out of magma they come in with their fists, and they scoop one back every 30 s while calm (`MobAmmo.Kind.BOMBS`).
- **Rock-throwing captains** carry 3 to 5 rocks. Each lands as a cobblestone that he, or anyone, can pick up again (`MobAmmo.Kind.ROCKS`).
- **The Ogre King** tears each boulder out of the ground beside him (stone, dirt, gravel, cobblestone, netherrack with open air above, never from under his own feet). The hole stays, and the boulder lies where it lands as the block it was. With nothing to tear up he throws nothing (`OgreHolds.tearable`).
- These were left out of the finite ammunition change. Still free on purpose: powers and sorcery (the Cinder-Shade's Ember Knives are fire magic) and the Trials hall's wall darts (a trap).
- Built (`compileJava`); not played.
