# Locked-on swings hit small, quick foes (baby zombies)
Andy: "baby zombies are unfightable" (clip: an axe locked onto baby zombies' torsos, their health never moving).
- Better Combat's swing hits what its flat hitbox along the crosshair passes through, about a block tall for a side cut, and the lock-on camera trails a quick target. On a baby zombie (under a block tall, close in and below you) the cut passed over its head. Now whatever you're locked onto is struck by your swing whenever it's within the swing's reach, in front of you within its arc (at least 50 degrees each side, at most 90), in sight, and Better Combat would let you hit it at all (`LockedSwingTargetMixin` on Better Combat's `TargetFinder`). Unlocked swings are as before.
- Built; not played.
