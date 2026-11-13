package com.jaymoskingdoms.client.creatures;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

/** What a creature's renderer reads off it: its pose number, how full it is, and what floats inside a clear cube. */
final class CreatureState extends LivingEntityRenderState {
	int look;
	float full;
	boolean lie, flaps;
	final ItemStackRenderState[] inside = {new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState(), new ItemStackRenderState()};
	int insideCount;
}
