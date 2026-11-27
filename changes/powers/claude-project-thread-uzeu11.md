# Arrows stuck in you are real arrows, not tools on your back
- **Skeleton knock-downs**: coming round after a skeleton downed you left an arrow "lodged" in you the way a sword or dagger gets stuck in a body, so it was drawn as a flat arrow item standing off your back or hip, like a carried tool, and it never came out (only someone else's empty hand could pull it). It now goes in as one of the game's own stuck arrows, the same ones a skeleton's shots leave, which work loose by themselves.
- **Old saves**: arrows already lodged that way move over to the game's stuck arrows the next time the player (or mob) loads; stuck blades stay as they were. The stuck-weapon layer never draws an arrow.
- Classes: Lodged (`lodge` sends arrows to the arrow count, `unlodgeArrows` on entity load), LodgedLayer (skips arrows).
- Built; not played.
