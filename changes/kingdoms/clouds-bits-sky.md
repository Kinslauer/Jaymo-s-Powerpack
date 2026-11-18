# sky decks - lumpy tops, daily skies, gaps, high wisps
- **Lumpy tops**: some squares of the low and high cloud decks rise in mounds of one to three extra slices; few in fair weather, many as rain gathers.
- **A different sky each day**: each deck's height (within 12 blocks), drift speed (0.7x to 1.4x) and opacity (0.8x to 1.15x) are rolled from the day number and ease in over about a minute at dawn. The roll uses the day only, as the client has no world seed.
- **Gaps**: fair weather leaves clear patches in the decks (a new pattern each day, crossfaded at dawn); storms close them.
- **High wisps**: a third, faint deck at about 236 blocks, two slices, drifting 1.8x as fast, near white by day and warm at dusk. Off in low-end mode, which also has no bumps.
- Classes: `CloudDecks` (drift is now kept per deck, so a changed pace never jumps a deck). Tuning constants are at the top of the class.
- Built; not played.
