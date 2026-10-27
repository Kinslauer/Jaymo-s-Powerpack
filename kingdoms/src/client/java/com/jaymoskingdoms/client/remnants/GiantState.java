package com.jaymoskingdoms.client.remnants;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What an Aether Giant's model needs to draw a frame. */
public final class GiantState extends LivingEntityRenderState {
	/** How far each arm's blow has run (ticks; 0 when it isn't swinging), and whether both came down together (a stamp). */
	float leftAttack, rightAttack;
	boolean stamp;
	boolean aggressive;
	/** How hard it's flinching from a hit: 1 just struck, down to 0. */
	float flinch;
	/** (0.7.184) Its entity id: the seed its death's tumble and break-up are drawn from. */
	int seed;
	/** (0.7.261) How far through its stride it is, in steps: the right foot comes down on the even ones, the left on the odd. */
	float stride;
	/** (0.7.267) How far into its grab it is (ticks, {@code AetherGiant#GRAB_TOTAL} long), or -1 when it isn't grabbing. */
	float grab = -1.0F;
}
