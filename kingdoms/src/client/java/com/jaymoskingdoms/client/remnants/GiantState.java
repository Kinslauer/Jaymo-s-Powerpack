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
}
