# Rig suit-down - parking the suit plays the wrap backwards
- **Suit-down** (Andy: "parking the suit should show a suit down like the clip"): parking the Rig with the call key while standing still and unhurt for 5 s throws your arms out and the suit comes off piece by piece, the wrap suit-up in reverse. The helm goes first (the eyes go dark, the faceplate lifts, the helm folds back off the head), then the legs, the chest opens off the reactor, and last the gauntlets slide off past the hands. Each plate swings open and flies off, and each piece has its own unlocking sound. Then the arms come down and the pieces blast off to be parked as before.
- Filmed from the front like the wrap: on the face for the helm, the chest for the rest, then a wide shot, and back behind you.
- A blow part way, leaving, or a low-health failsafe parks the rest at once. Parking while moving or hurt, the sentry post and evac still park instantly as before.
- Classes: `RigCall` (suitDown, the staged take-off), `RigSuitUp` (DOWN style, stillAndCalm, sounds), `RigMoves.SUITDOWN`, `RigSentry` (waits for a suit-down), `RigSuit` (plates flying off), `RigPose`, `RigSuitUpCamera`.
- Built; not played.
