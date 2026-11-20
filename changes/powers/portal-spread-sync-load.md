# Nether Portal Spread no longer freezes the server in land not yet loaded
- **Fix**: the server no longer crashes (watchdog kill) when Nether Portal Spread checks a portal in land that is not yet loaded or generated, for example after a player teleports far outside the pre-generated area. Such a portal waits at the end of the mod's queue until its land is loaded, and spreading skips blocks in unloaded chunks until the next round. Nether Portal Spread behaves as before everywhere else, and nothing changes if the mod is absent.
- Classes: `PortalSpreadQueueMixin`, `PortalSpreadLookMixin` (`mixin/portalspread/`, config `jaymospowers.portalspread.mixins.json`).
- Built by CI; not played.
