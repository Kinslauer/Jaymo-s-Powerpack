# Alt in mid air dives: jump + Alt is a dive-roll evade
- **The dive on Alt in the air**: press the Dodge key (Alt) in mid air over land and you throw yourself head first the
  way your movement keys point (forward with none held), with a little lift, and land in a roll. A quick Space + Alt is a
  dive every time, so it doubles as an evade on the ground. One dive per jump. Flips, air tricks, the shield vault and the
  water dive still come first when they fit (Andy's pick); the dive fills in when none of them does.
- `DodgeClient.airDive`, `Dives.canAirDive` (the server accepts the dive key in the air over land too, so everyone sees the
  head-first pose). Push, lift and how long the landing roll stays armed are constants at the top of `Dives`.
- Built by CI; not played.
