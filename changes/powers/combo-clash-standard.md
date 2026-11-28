# Combo Clash combat for everyone: block and counters on F, a short dash that holds into the long roll
- **Combat mode is standard** (Andy: "the "combat mode" should be standard, and what chrono drops into when he uses
  time flow"). Combo Clash's combat mode is on for everyone who can fight from the moment they join
  (`client/compat/ComboClashClient`). The ` key still turns it off, and back on, for the rest of the session. Chrono's
  Time Flow puts it on whatever that key said, and when the flow ends it goes back to the player's choice. Anyone downed,
  carried, fainting, tied or crippled is out of it until they can fight. It's set on its flag directly, so the camera is
  left as you have it and no chat line shows.
- **Time Flow with Combo Clash:** a foe within 3 blocks takes Combo Clash's string; one further off, the flow still
  carries you to it for its blow (`ChronoFlowClient.CLASH_REACH`). The counter is on F.
- **What the attack key still does with combat mode on** (`ComboClashClient.yieldsAttack`, read by the new `@Pseudo`
  `ClientCombatMixin` for both the click and the key held): dig a block when no foe is within 6 blocks and nothing is
  locked on (a pick, shovel or hoe always digs). It also covers sneaking (takedowns, a Quiet Word), someone downed or
  helpless under the crosshair (finishers), flails and whips, boxing, an item in use or a guard up, riding a dinosaur,
  a gravity grip, hanging on a ledge and the symbiote's hold. The powers' own held moves keep it too: a Z Fighter's
  grab-throw, ape smash, race hold and hold combo, Chrono's overclocked flurry and Time Flow's lunges, and the Staff
  Surge. Our attack callbacks (takedowns, dinosaurs, boxing, lash, gravity, web cut, web power, Time Flow, Kingdoms' Sun
  click) now run in a phase before Combo Clash's (`ComboClashClient.BEFORE_CLASH`). On the server, a plain attack that
  gets through is no longer dropped (`CombatEventsMixin`). Q drops items in combat mode again.
- **Blocking on F** (Andy: "move blocking to f as well"). The new Guard key (F, `combat/GuardKey`,
  `client/combat/GuardKeyClient`) raises whatever blocks in your hands while held: a shield in the off hand first, else
  the weapon in the main hand, else a blocking weapon in the off hand. The guard, parry and perfect parry are still
  `Guard`'s (vanilla blocking, started from F). Right click no longer raises a guard (`Guard.use`). A shield or a weapon
  whose only right click was the guard stands aside, so the other hand gets the click. Items with a right click of their
  own keep it: a warhammer's smash, Sora's Dagger's Flurry, the glass gauntlet's turn, a knife's sneaking throw and a
  lance's couch. A guard raised any other way is let down again on both sides (`GuardKey.strayGuard`). Vanilla's let-go
  on right click up skips a guard F is holding (`GuardKeyHoldMixin`). A knife raised on F is always the guard, never the
  throw (`KnifeGuard.raise`). With combat mode on, F is Combo Clash's block and ours stays down.
- **Counters and power guards on F** (Andy: "the counters should move to f as well"): Time Flow's counter
  (`ChronoFlowClient`), the Spider-Sense counter (`WebPowerClient`), the webbed fists' web shield (`WebShieldClient`), the
  Z Fighter's bare-handed guard (sneak for the Full Counter; `ZFighterPowerClient`) and the Darktide guard
  (`DarktidePowerClient`). While one of them has F, no weapon comes up and Combo Clash's block stands aside
  (`GuardKeyClient.powerOwns`, `ComboClashClient.blockYields`). Right click held on someone downed is still the Z
  Fighter's revive.
- **Dash** (Andy: "their dash was too long, the dash i want should be quick and short, and holding the button should
  extend it into the long roll, good for covering distance and escaping aoe attacks or groups"). Combo Clash's dash is
  cut on the server to 3 ticks at 0.8 of its speed, about 2.3 blocks where it went nearly 5
  (`ComboClashCompat.shortenDash`). Held 3 ticks on the ground, our long roll takes over the way the dash went
  (`DodgeClient.tickClashHold`, new `Dodge.ClashRoll`). It costs a dodge's feathers, has a dodge's evasion window and
  shows the tumble to everyone. Held through the roll, you come up into the crouch, and into prone at the usual hold.
- **Keys** (`KeyConflicts` v15): vanilla's swap-hands comes off F (unbound; Controls can set it again). New key "Guard /
  counter" on F. Combo Clash's skills on 1-4 still take those number keys while you hold a weapon with skills, as before.
- Text: Time Flow, Darktide, the blocking trait, Sora's Dagger and the Z Fighter's guard now say F.
- Built; not played. Not tested: how its dash motion and our roll's tumble look one after the other, its first-person
  look with combat mode on in first person, and whether every power's left click got onto the list above (anything left
  off throws Combo Clash's string as well as its own).
