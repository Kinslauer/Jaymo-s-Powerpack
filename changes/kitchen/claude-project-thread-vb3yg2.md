# Windmill sails after Better With Mods
- A windmill's four sails are now Better With Mods' kind (MIT; its sailcloth texture): a spar running out to the tip with a
  blade of cloth beside it, pitched into the wind, instead of the flat sailcloth squares.
- **Dye them one at a time.** Right-click the windmill's hub with a dye to colour its next sail; each click moves on to
  the next, so four dyes make four colours. The dye is used up (not when that sail is already that colour). Colours are
  saved with the windmill.
- Code: `mill/power/{RotorEntity,RotorBlock}`, `client/mill/RotorRenderer`; texture `block/windmill_cloth`.
- Compiles (javac). Not played: the pitch and colours in the world.
