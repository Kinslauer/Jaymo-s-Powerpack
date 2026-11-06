---
name: mouse-buttons-26-3
description: In MC 26.3 MouseButtonEvent.button() is 1-based (left 1, middle 2, right 3); compare with InputConstants.MOUSE_BUTTON_* only
metadata:
  type: reference
  modified: 2026-11-01T00:34:51.471Z
---
MC 26.3 numbers mouse buttons from 1: `InputConstants.MOUSE_BUTTON_LEFT = 1`, `MIDDLE = 2`, `RIGHT = 3`
(AbstractWidget.isValidClickButton checks == 1). Code that checked `button() == 0` for left never fired, and `== 1`
"right" caught left clicks. Fixed pack-wide in PR #821 (2026-11-01). Always use the constants.

Also learned there: MCA's skin/clothes bodies (receiveDeferredAnimationPose) take the pose captured after
MobSwingLivingEntityRendererMixin laid our poses on, so additive poses run again at flush double up
(`McaLookModels.posedByMca` guards the Bio-built hunch). A cloud test game with MCA works per [[cloud-playtest-setup]];
`./gradlew runClient` with run/saves/test made by runServer, and xdotool clicks.
