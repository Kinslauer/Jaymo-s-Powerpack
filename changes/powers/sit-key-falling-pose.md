# sit key in midair picks your falling pose
- **Sit key in midair** (] by default): while falling, each press swaps your falling pose to the next of the 12 (windmill, air run, arms out wide, knees tucked, edge grab, back flop, skydiver's arch, tumble, flat spin, curled up, pencil drop, limp). The new pose blends in at once and everyone in view sees it; the action bar names it.
- **Remembered**: the last pose you chose is kept (saved with the player, through relogs and deaths) and every later fall holds it from start to finish instead of the random picks. The brace before the ground still comes in as before (falls of 12+ blocks).
- Classes: core/Sitting (FALL_POSE attachment, FallKey payload), client/FreefallPose (chosen pose, fallKeyShown), client/MeditateClient (key routing). Key name and 12 action-bar lines in lang/parts/core.json.
- Built; not played.
