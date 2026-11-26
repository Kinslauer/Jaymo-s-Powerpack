# Cut parts off a body from its loot screen
Andy: "there should be a way to dismember them from the inventory screen so you can take the limb if it doesnt drop". A person's body (or one built like a person) now has a cut-off button above its hands in the loot screen: pick the head, an arm or a leg and it comes off in your hand (a severed part with Jaymo's Powers), with whatever was worn on it.
- Gear bolted onto a body (Jaymo's Powers says which: the Rig on the future's troopers and androids) can't be taken from its slot. Its slot is washed red, its tooltip says which part it comes off with, and a click on it cuts that part off. A piece bolted to the torso never comes off.
- A machine's body wearing bolted gear (an android in its Rig plate) opens the loot screen too, so its plate can be cut off before it's searched.
- `CorpseEntity#setAttachedGear` (a reflection hook, plain types), `attachedParts`, `canCutOff`, `cutOff`; parts lost any way drop their bolted gear with them. `CorpseLootMenu` buttons (`CUT`, `CUT_GEAR`), `CorpseLootScreen` button and part list, lang part `corpse_cut_off.json`.
- Built; not played. Player bodies have no cut-off button.
