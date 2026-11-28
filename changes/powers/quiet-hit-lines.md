# quieter hurt lines and rarer leg knockdowns
- **"I'm hit!" once a fight**: a blow from someone or something (a swing, an arrow, a bite, a blast) no longer names who did it or how. The first blow of a fight says "I'm hit!" under the crosshair with the red side mark; the blows after it show nothing. A blow after 20 s with none starts a new fight. Our features that strike without a named attacker (colossi, drunk goblins, claws, thrown things and the like: `HurtCauseHud.BLOW_FEATURES`) count as blows too.
- **Other hurts once each**: a hurt nobody dealt (the sun, fire, poison, hunger, cold, a fall, drowning, our own afflictions) shows its own line once, when it starts; the same cause shows again only after 10 s without it. At most two lines at once.
- **Shorter lines**: every "X hit me / got me from range / blew me up" line is gone (`hurt_cause.jaymospowers.hit` is "I'm hit!"), and 49 wordy cause lines are cut down ("The sun! I need shade", "The gloom...", "Too much ki!").
- `HurtCause.Hurt` now carries `fight` instead of the attacker's name and the ranged flag; the server log still names the attacker.
- **Leg knockdowns**: a heavy blow to a player's leg or foot (`LegFell`) fells them one time in five (`PLAYER_CHANCE` 0.2), down 80%. Mobs still always go down.
- Built; not played.
