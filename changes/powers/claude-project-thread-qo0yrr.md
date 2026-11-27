# New players start with low-end mode on and the vanilla texture look
- **New installs**: on an install's first launch with the pack, low-end mode is switched on (shaders off, Fast graphics) and the vanilla texture look is picked (the pack's retexture packs off, and not downloaded), on every PC, not only weak ones. The first world joined says so once in chat, with `/lowend off` (shaders and the starting graphics settings back) and `/texturelook reason` (the pack's textures back) for the full look.
- **Existing players keep their settings**: an install counts as new only when it has no `config/jaymospowers-low-end.properties` yet, which every earlier launch wrote. Nothing changes for Andy's or Jung's installs, or for any player who has launched the pack before.
- Classes: NewPlayerDefaults (new), LowEndMode (`newInstall`, `switchOnForNewPlayer`), TextureLook (`pickVanilla`), JaymosPowersClient (registered before the pack's resource packs are switched on).
- Built; not played.
