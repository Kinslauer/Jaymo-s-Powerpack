# drawn progress bars - every text bar redrawn like the execution bars
- Andy: "I do not like these text based loading bars. The executions and tree chopping are the best". Every `||||....`, `[====----]` and block-character bar on the action bar is now drawn (`core/HudBar` sends it, `client/hud/HudBars` draws it):
  - **Holds** (reviving, giving up, getting up, out cold, struggling, eating to rise, coming to, treating, washing a rag, lifting someone, shield bash, Locust leap, Nadir's phase/Surfacing/Event Horizon, bonding, Legend nuke wind): three framed bars under the crosshair (under the execution bars, BELOW row 21), each filling in turn and brightening when full, a short caption under them ("Reviving Steve", "Lifting Steve"). The "[...] keep holding" text is gone.
  - **Gauges** (eye beam heat, bracer soak, fury, Legend Heart heat, Ember heat, Darktide water, Resonance shriek, balloon fuel): one bar in five segments over the hotbar (FOCUS row 5), label at the left, number at the right.
  - **Rig field**: one mark per blow kept, like the tree chop meter.
- Holds that used to stop your swings still do: the bar carries that (`HeldInput.bar`) instead of the client spotting `|||` text. Gauges never stop swings.
- Downed and come-to hints still show their line on the action bar, without the bar in it; the bar shows once you've started holding.
- Kingdoms and Kitchen send their bars here through Fabric Loader's object share (`jaymos:hud_bar`).
- `PickKit.gauge` removed (nothing used it any more).
- Compiled (all three mods); not played.
