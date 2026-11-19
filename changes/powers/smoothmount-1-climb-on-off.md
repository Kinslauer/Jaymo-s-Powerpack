# SmoothMount climbing patch 1: climb on, climb off, camera glide
- **Mounting**: climbing onto a horse/donkey/mule/pig now plays a short animation (0.5-0.75 s) instead of snapping on instantly. Three variants by angle: left (foot in stirrup, swing right leg over), right (mirror), behind (vault onto rump, slide forward). Armour slows the climb (cloth ~1×, full plate ~2×). The horse must stand still; a hit cancels the climb and no ride starts.
- **Dismounting**: climbing off plays a forward or backward dismount animation (~0.5 s) ending exactly where vanilla sets you down.
- **Camera**: first-person view eases from standing eye height to the saddle and back (~0.3-0.45 s) instead of snapping; the view follows the head through the clip.
- **Multiplayer**: other players see the climb animations too.
- **New classes**: `client/MountClimbPose` (runs the keyframed clips), `client/MountClimbCameraMixin` (camera glide), `net/MountClimbPackets` (C2S finish-climb, S2C sync), `core/MountClimbHandler` (server broadcast).
- **Animations**: five GeckoLib clips in `assets/jaymospowers/geckolib/animations/entity/player_mount_climb_on_left|right|back|climb_off_forward|back.animation.json`.
- Fresh Animations (EMF) pauses while any climb clip plays.
- Built; not play-tested in game.