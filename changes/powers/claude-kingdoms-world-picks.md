# A ticking charm and one holder per power

- **Ticking Charm** (charm slot; clock, copper, string and amethyst): it clicks faster the nearer you are to the Irradiated Giant's monolith or a blighted wolf. Sound only. Needs Jaymo's Kingdoms to have anything to hear.
- Honey and wild garlic ease the blight sickness, and the Gaunt and its heart have alchemy essences.
- **One holder per power**, off by default: `/power oneholder on|off|status` and `/power oneholder free <power>` for operators. While on, a power someone already holds cannot awaken in, be granted to or be drunk from a phial by anyone else, until its holder dies for good, gives it up or an operator frees it. Z Fighter lineages and Xenoform are exempt.

Classes: `baubles/TickingCharm`, `core/OneHolder`, hooks in `compat/KingdomsCompat`, `Afflictions`, `Powers`, `PowerCommand`.

Compiled only; not played.
