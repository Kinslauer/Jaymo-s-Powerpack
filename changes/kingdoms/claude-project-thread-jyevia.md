# fitted limbs lie with the body
- **Fitted and Frostborn limbs follow the body**: a villager's wooden, iron or Frostborn-made limb (and a scouter) was
  drawn on the bones before the dead, downed and ragdoll poses were laid on, so on a corpse a fitted leg stood upright on
  its own while the body lay on the ground (Andy's armoured Frostborn leg on a dead villager). They are now worn models
  (`FrostTechLayer` Scouter and Limbs, on `WornModel`) drawn in the body's pose as it is drawn, so they lie, slump and
  move with it. A new late hook (`FrostTechModelFeatureRendererMixin`) shows the fitted limb again after Jaymo's Powers'
  lost-limb hide and Kitchen's corpse hide, which hide the part by name.
- Tested in a dev client: a villager with a cut right leg and a Frostborn leg, standing and then dead (the leg lies at
  the hip with the body). Not tested: a downed body, a wooden or iron limb, a scouter on a corpse, Fresh Animations.
