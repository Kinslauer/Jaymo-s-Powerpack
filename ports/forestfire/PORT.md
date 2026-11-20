# Peterwolf's Forest Fire & Firefighting, ported to 26.3 for Jaymo's Powers

**Peterwolf's Forest Fire & Firefighting** is by **Peterwolf** (Piotrek;
https://www.curseforge.com/minecraft/mc-mods/peterwolfs-forest-fire-firefighting,
https://github.com/peterwolf-pl/Peterwolf-s-Forest-Fire-Firefighting), licensed **MIT** (see `LICENSE`, also shipped
inside the jar). It is dedicated to all firefighters who risk their lives protecting forests, wildlife and communities.

This folder is a port of version **1.1.11** (CurseForge file 8681320, the only file, built for Minecraft 26.2) to
Minecraft 26.3, made because there is no 26.3 build yet. Lifting its `minecraft ~26.2` requirement alone isn't enough:
the jar stops the game on start in 26.3. The public GitHub source is older than 1.1.11 (it has no firestorms, for one),
so the code here is 1.1.11's own, recovered from the CurseForge jar with Vineflower and changed only where 26.3 needs
it. It ships in Jaymo's Powers' free modpack with this credit. Once Peterwolf releases for 26.3, the pack switches to
the official jar and this folder goes.

## Changes from the original
- Build: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.164.0+26.3, Loom 1.18-SNAPSHOT; version
  `1.1.11-26.3-jaymo.1`; build output on E:\ on the home PC; Gradle wrapper from the TRMT port. One source set (the
  original split client classes into their own; the client entrypoint and client mixin config still load only on the
  client).
- `fabric.mod.json`: depends on `minecraft ~26.3`; version from the build. Nothing else.
- 26.3 API changes:
  - Blocks have no codecs in 26.3 (`simpleCodec` and `codec()` are gone): the `CODEC` field and `codec()` override are
    taken out of `CommandPostBlock`, `PortablePumpBlock`, `GroundNozzleBlock`, `HoseSplitterBlock`,
    `IntakeStrainerBlock`, `PortableSprinklerBlock` and `PortableWaterTankBlock`.
  - `PushReaction.DESTROY` is `PushReaction.POPPED` (`ModBlocks`).
  - `Player.drop(ItemStack, boolean)` takes a `Prediction`: `Prediction.SERVER_ONLY`, since every call is on the
    server (`FirefighterCommands`, `HoseConnectionManager`, `HoseEndpointManager`, `HoseInventoryService`).
  - `Entity.hurtMarked` is `Entity.needsSync` (`RescueTicker`, `HoseEndpointManager`).
  - `FirefighterCommands`: a counter copied into a final local before its lambda (the decompiled code reused it).
- Left out: the Firefighting Water Bomber, which needs Peterwolf's Planes (not in the pack): `aircraft/`,
  `client/aircraft/`, `compat/planes/`, `component/water_tank/`, `simulation/water_drop/`, `command/FireplaneCommands`,
  `network/firefighting/FireplaneActionPayload`, the `FireplaneAimCameraMixin`, and the plane's recipe, item model, textures and water-source tag (its recipe names an item only Planes registers, so it failed the data load). The mod only ever loaded these by
  name when Planes was installed, so nothing else changes. Its config keys for the aircraft are still read and ignored.
- No incident alert: `IncidentManager.alertNearbyPlayers` no longer sends "INCIDENT ALERT: ... respond with pump, hose and
  command post" to chat (players within 256 blocks) or the action bar (everyone else). The pack keeps world events out of
  chat. Nearby players are still counted as the fire's responders.

## Checked
Every Minecraft, Fabric API and Fabric Loader member the jar refers to (its own classes' inherited ones included) was
checked against the 26.3 jars before and after the port: 17 were missing in the original, 0 are in the port. Its mixin
targets (`FireBlock.tick`, `TitleScreen.extractRenderState`) are unchanged in 26.3. A dedicated server with it and
Jaymo's Kingdoms starts.
