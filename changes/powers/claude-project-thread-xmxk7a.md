# blood stays drawn on MCA bodies
- Andy: blood decals "need to stay drawn on the MCA models". MCA draws villagers (and players in an MCA body) with its own skin and clothes layers, so the blood coat laid on the body's own model sat at the depth of MCA's bare skin, under its clothes, so it only showed through their gaps. Now on an MCA body the blood (a corpse's too) and the dirt on skin and clothes are painted over each of MCA's skin and clothes textures and drawn on that layer's own model, one step after MCA's, so they stay on top: bare skin shows its blood, clothes show theirs. Other bodies are drawn as before.
- `BloodCoatLayer` (`paints`), new `McaBlood` (only loaded with MCA).
- Built; not played.
