# Air combo throws mobs as ragdolls
- `MobRagdolls.thrown(mob, vx, vy, vz)`: Jaymo's Powers' air combo knocks a mob limp (or takes one already limp) and throws the body at that speed, not cut to the knockback caps (a throw on purpose, as players' `LivingRagdolls.knock`), with a flick at the head (`THROWN_TUMBLE` 0.2) so it turns over in the air; its limp time starts again so it doesn't get up in mid-air. Does nothing with Ragdollified Reactions loaded.
- Built; not played.
