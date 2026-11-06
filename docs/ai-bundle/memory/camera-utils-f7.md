---
name: camera-utils-f7
description: Camera Utils in the pack with Andy's F7 third-person preset; merged PR #128 as pack 0.9.23
metadata:
  type: project
---
2026-10-13 Andy asked for their Camera Utils preset on F7. Camera Utils (ARR, Max Henkel; Modrinth 5XAah6bJ, CF 510234:8887090) is in packMods; F7 = its view 2 by default.
client/CameraPreset (preLaunch entrypoint) writes third_person_cam_2_* = X -3.4, Y 0.1, Z 0.9, rot -3, not inverted, GUI shown into config/camerautils/camerautils.properties once (marker jaymospowers-camera-preset-v1). KeyConflicts v2 unbinds its zoom off Z (Mode key; OK Zoomer zooms). Shoulder view stands aside while a Camera Utils view is on (reflects ClientConfig.thirdPersonCam/detached).
Merged PR #128 2026-10-13 21:1xZ as pack 0.9.23. Untested in game.
