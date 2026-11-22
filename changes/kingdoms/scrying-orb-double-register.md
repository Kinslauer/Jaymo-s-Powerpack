# The server starts again: the Scrying Orb registered its packets twice
- **Fix**: the server crashed on start ("Packet type jaymoskingdoms:scrying_orb_view is already registered"). The Scrying
  Orb block registered the orb's three packets and `ScryingOrb.register()` registered them again. Now only `ScryingOrb`
  does.
- Class: `ScryingOrbBlock`.
- Built; the server was started with it.
