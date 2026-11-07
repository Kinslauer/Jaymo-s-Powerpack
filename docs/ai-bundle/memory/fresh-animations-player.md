---
name: fresh-animations-player
description: Fresh Animations Player Extension + EMF/ETF in the pack (PR #154, pack 0.10.29); our poses pause EMF; Xenoform extras FA-style
metadata:
  type: project
---
2026-10-14 Andy asked for FA: Player Extension (Modrinth fa-player-extension; CF 1281029 file 8245230, FA+Player-v1.1.zip,
SHA-1 122e397a...). Terms: modpacks OK, no redistribution, no copying unedited assets into our own files. Standalone (own
player.jem), needs EMF (CF 844662) + ETF (CF 568563), both LGPL client-only. PR #154 MERGED 2026-10-14 (pack 0.10.29).
- client/PackResourcePacks (was MedievalVillagers) enables each pack once (FA marker jaymospowers-fa-player-v1).
- EMF pause: client/EmfCompat/EmfAccess register EMFAnimationApi.registerPauseCondition -> PoseTakeover.drawingPosed().
  Any new body pose's RenderStateDataKey must be added to PoseTakeover.posed, or FA's pivot offsets bend it.
  EMF already pauses for Player Animation Library (Better Combat).
- EMF copies the player's FA pose onto layered HumanoidModels (Xenoform forms, armour) after setupAnim.
- XenoMotion: per-entity springs (turn/trail/fall/air/land/breath) drive Xenoform extras (Wyrm wings/tail etc).
Also in PR #154 (Andy 2026-10-14): Wyrm wings bigger, two halves, wrap round the body on the ground (Wyrm.WRAP_*),
longer 3-joint tail; Web parkour from ParCool's ideas: WebParkour/WebParkourClient/WebParkourPose (vault, cling = Sneak
in air facing wall, wall jump, slide = Sneak while sprinting).
Turned down 2026-10-14: ParCool (Forge/NeoForge only, <=1.21.8; LGPL so moves may be remade), Nature X (ARR, forbids
asset use, <=1.21.11).
