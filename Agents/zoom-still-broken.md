---
status: blocked
task: zoom still broken (Shift + Left Alt)
branch: none yet
with: 
pr: 
mods: powers
versions: 0.28.56 in Andy's CurseForge mods folder
started: 2026-11-28
owns:
  - (nothing edited yet)
---
# Zoom still broken

**Task:** Andy: "the zoom is still broken, can you see what is wrong?" Shift + Left Alt should zoom (SneakZoom, through OK Zoomer's Zoom key).

## Findings (Claude, 2026-11-28)
- Andy's CurseForge game (E:\CurseForge\Instances\Jaymo's Powers) last started Nov 26, 3:59 PM (lastPlayed, latest.log 4:21 PM) on **Powers 0.27.92**, with no Combo Clash loaded. The #2354 fix is in **0.28.55**; 0.28.56 is in mods/ since Nov 28, 3:48 AM but has never been started. options.txt is from that same Nov 26 run (key moves v14/v15 not applied yet).
- `.jar.old` is made on every update (pbdr-deck.ps1), so it doesn't point to a second game copy. Only one keyboard layout in Windows, so Alt+Shift isn't eaten by language switching.
- On paper the 0.28.56 path is sound: OK Zoomer 19.0.0-beta.2 is in HOLD mode with spyglass mode OFF and reads `ZOOM_KEY.isDown()` at START_CLIENT_TICK. SneakZoom holds that key down at tick start before OK Zoomer and at tick end. Vanilla `KeyMapping.isDown()` is a plain field. Combo Clash only spends presses (consumeClick/drain); it never lets go of keys. DodgeClient and Combo Clash's dash hold off on `ownsAlt`.
- FOV mixins: UltimateCinematicFovMixin, Kingdoms' FilmPlainFovMixin (priority 2000, plain FOV during films), ZFighterScanZoomMixin (only with the scan held), wind and Rig speed FOV (multiplicative), RoadPaceFovMixin (speed only). Combo Clash's CameraMixin goes through HitFeel.fov on calculateFov (not read in detail).
- Still open: a film flag stuck on (`UltimateCinematic.filming`, `LoreFilmClient.locked`, `FarmWakeFilmClient.running`) would wipe every zoom. Combo Clash's HitFeel.fov and Powers' ComboClashClient camera are not read.

## Next
- Asked Andy (via the coordinator): where and when he tried it, and what happened: nothing, a dash, or a zoom that snaps back. If he hasn't started 0.28.56 yet, try it there first; a failed Shift + Alt hold now logs "Zoom:" with the reason.
