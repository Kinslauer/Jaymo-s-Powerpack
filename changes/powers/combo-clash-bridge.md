# Combo Clash in the pack: its 4-hit strings and dashes, with our combat stepping aside
- **Combo Clash** ("Combo Clash" by HanHoYoung, 1.0.4 for Fabric 26.3) is now in the pack, as its unmodified jar. Its
  licence allows that in a free modpack that credits it (the credit is in LICENSE, section 4). Nothing of it is copied
  into our mods. Its combat mode is on the ` key (left of 1). In combat mode you get an over-the-shoulder camera. Left
  click throws a 4-hit string whose fourth blow launches the foe: hold jump as it lands for an uppercut, or land it in
  mid-air for a downslam. Alt dashes front, side or back, and while you're being hit it's the evasive. Hold F to block,
  and tap F just before a blow for a perfect block. Bare hands, swords, axes and shields each have their own set. Press
  ` again to fight your usual way.
- **Keys** (`KeyConflicts` v14, once per install, only while each is still on its default): combat mode moves from G
  (the second ability) to `. Dash and evasive move from Q to Left Alt, the dodge key. "Move HUD" comes off H (the
  character menu) and is left unbound.
- **What steps aside in combat mode** (`client/compat/ComboClashClient`, by reflection):
  - Better Combat's swings and the region swings (`ToolSwingGateMixin`), so a left click is Combo Clash's M1.
  - The Alt dodge's dash, roll, hold to prone, dive, vault, flip and trick (`DodgeClient`). Rolling back up after a leg
    blow still works.
  - Our arm and body poses over a player while one of its motions plays (`PoseTakeover.posedOtherwise`). That covers the
    armed stance, the parry, the hit flinch, boxing and Fang Gauntlet arms, body weight, BodyMotion/BodyAnim and
    Fresh Animations.
- **Who can't use it:** someone downed or out cold, carried, fainting, tied hand and foot, black while a new life is made,
  or crippled off their feet. The server refuses their M1 (`compat/ComboClashCompat`, through Combo Clash's public M1
  hook), and the client switches combat mode off for them with one press of its key, so the camera goes back.
- Kept as before, and running off its hits too: lock-on and its body regions, wounds, downing, gibs, hit sounds and the
  air combo. Its hits are ordinary player attacks.
- Built; not played. Lock-on's camera beside its shoulder camera, and its arm motions beside the poses we don't gate, are
  untested.
