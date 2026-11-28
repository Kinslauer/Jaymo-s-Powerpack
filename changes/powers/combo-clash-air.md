# Combo Clash's air game is ours; moves cut a string short
- **Air combat** (Andy: "i like everything about except the air combos ... along with or enemy step for air combat").
  Combo Clash's server now counts every blow of its string as a blow on the ground: no hovering in the air, no string in
  the air, no downslam (`mixin/comboclash/CombatManagerMixin`, on `CombatManager.startM1`). Its uppercut (jump held as
  the fourth blow lands) is now our launcher: the foe goes up with you after it, as the Air Combo's launch does
  (`AirCombo.finisherLaunch`, through Combo Clash's M1 hook `landed`, `ComboClashCompat.variant`). It works for those
  who can fight in the air and have learned the Air Combo (or a web slinger's Agility); for anyone else it's a plain
  fourth blow. Three ticks off the ground, or flying, the attack key is vanilla's and our air combo's
  (`ComboClashClient.yieldsAttack`). Up there the juggle, the sneaking slam, the enemy step and the web's pull-ups work
  as before.
- **String cancels** (Andy: "there should also be ways to cancel your string into combos using moves"). A Combo Clash
  string under way gives way at once, through its public `ComboAPI.giveWay` (`ComboClashCompat.giveWay`), so the move
  plays clean. This happens on any power key press (`Powers.key`; the moves anyone has without a power too), the Air
  Combo's launch, a pull-up and an enemy step. Kingdoms' combat arts do the same.
- Built; not played. Not tested: the launcher's timing against Combo Clash's own fourth-blow knockback, which it
  replaces on that blow, and how the give-way motion looks into each power's move.
