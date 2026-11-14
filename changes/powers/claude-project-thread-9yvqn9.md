# standing sigils, chapel world-load fix
- `MagicFx.sigil(level, at, facing, ...)` draws a sigil standing in the air facing a direction (both faces drawn); a zero facing still lies flat. `MagicFx.shape(...)` and `MagicFxClient.shape(...)` send or draw any shape directly, so Kingdoms' casters and films can use them.
- Fixed the Darkened Chapel template pool: its structure-block ignore list used the old format, which 26.3 rejects, so worlds could fail to load with Powers.
- Built; seen in the Kingdoms dev client.
