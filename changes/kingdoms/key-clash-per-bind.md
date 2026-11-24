# The key clash pop-up comes per key, on its first press
- **One key at a time**: the pop-up about keys bound twice no longer lists every clash when you join. The first time in
  a visit that you press a key two bindings share, it names that key and what's bound to it ("Y is bound twice: Bound
  to Raise the medallion and Z-Fighter wheel"), and asks to rebind one in Controls. Andy: "the bind clashing tool tip
  should appear per bind and only when you first press it after you spawn".
- Each key's pop-up still shows twice at most, ever (counted per key in `config/jaymoskingdoms-key-clashes.txt`; the
  old single count starts over). Keys our mods share on purpose are still left out. `KeyClashes`.
- Built on CI only; not played.
