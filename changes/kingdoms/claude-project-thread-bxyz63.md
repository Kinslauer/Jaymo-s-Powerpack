# mobs carry finite ammunition
- **Skeletons** (and strays, bogged, parched) wear a **Tattered Quiver** on their backs with 6-12 arrows. When it runs dry they put the bow away and fight hand to hand, then draw again once they pick arrows back up.
- **Every archer counts its shots**: pillagers 8-14, piglins 6-10, illusioners 12-18, folk archers 12-20, ship crewmen 10-16, Scalebane archers 16-24, the Gorgon 60-80 (her arrow rain spends them too). Pillagers and Scalebane archers swap to melee when empty; folk archers draw their blades. Piglins just stop shooting.
- Guards carry 2-3 javelins and gunmen and pirates 6-10 shot.
- Arrows and javelins shot by mobs now stay where they land and can be picked up, by you or by them: an empty mob walks over to arrows within 16 blocks once the fight is over, and picks up any it brushes past.
- **Thrown weapons leave the hand**: a drowned that throws its trident and a captain who throws their axe are empty-handed until they pick it back up. The captain's axe drops where it lands.
- Folk and villagers at peace slowly restock (one of each kind every 30 seconds) back to what they started with.
- Classes: `MobAmmo` (new), mixins `MobAmmoShotMixin`, `SkeletonQuiverMixin`, `CrossbowGoalAmmoMixin`, `DrownedThrowMixin`; client `QuiverLayer`.
- Built; not played.
